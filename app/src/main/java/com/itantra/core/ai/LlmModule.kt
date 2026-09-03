package com.itantra.core.ai

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.nehuatl.llamacpp.LlamaHelper
import java.io.File

/**
 * Real on-device LLM inference (Phi-3 Mini, GGUF, via llama.cpp / `org.nehuatl.llamacpp`).
 *
 * Replaces [com.itantra.core.ai.TacticalAiEngine]'s hardcoded keyword-matching as the AI
 * Assistant's actual brain when a real model is downloaded and the device can run it. Returns
 * null (never fabricated text) when the model isn't downloaded, hasn't finished loading, the
 * device's ABI isn't supported, or generation genuinely fails — callers fall back to
 * [TacticalAiEngine] honestly, the same pattern used for STT/TTS/VAD elsewhere in this app.
 *
 * Native libs in the `llamacpp-kotlin` AAR only cover arm64-v8a and x86_64 — no armeabi-v7a
 * build exists (confirmed by inspecting the published AAR's jni/ contents). [isDeviceSupported]
 * gates on this so unsupported devices get an honest "unavailable," not a native crash.
 */
class LlmModule(private val context: Context) {

    companion object {
        private const val TAG = "LlmModule"
        /** Matches "Phi-3-mini-4k" — the model this app downloads is the 4k-context variant. */
        private const val CONTEXT_LENGTH = 4096
        private const val LOAD_TIMEOUT_MS = 120_000L
        private const val GENERATE_TIMEOUT_MS = 90_000L
        /** Bounds runaway generation (confirmed on-device: replies ran to 200+ tokens without
         * stopping). LlamaHelper.predict()'s public API has no n_predict/stop hook — its native
         * layer supports them (confirmed via javap: LlamaContext.completion()'s doCompletion(...)
         * signature includes them), but reaching that would mean bypassing LlamaHelper's private
         * internals via reflection, which is worse than enforcing the bound here using only its
         * public API (stopPrediction(), already used for the timeout case below). */
        private const val MAX_TOKENS = 250
        /** Matches the Phi-3 chat template markers in MainViewModel.buildLlmPrompt() — stop at
         * end-of-turn, or if the model hallucinates starting a new user turn. */
        private val STOP_SEQUENCES = listOf("<|end|>", "<|user|>")

        /** Real constraint of the llamacpp-kotlin 0.4.0 AAR — verified by inspecting its jni/ dir. */
        private val SUPPORTED_ABIS = setOf("arm64-v8a", "x86_64")

        fun isAbiSupported(abis: List<String>): Boolean = abis.any { it in SUPPORTED_ABIS }
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val loadMutex = Mutex()
    private val generateMutex = Mutex()

    private var eventFlow: MutableSharedFlow<LlamaHelper.LLMEvent>? = null
    private var helper: LlamaHelper? = null
    private var isLoaded = false
    private var loadedModelPath: String? = null

    fun isDeviceSupported(): Boolean = isAbiSupported(Build.SUPPORTED_ABIS.toList())

    /**
     * Loads [modelPath] (a local GGUF file) if not already loaded. Safe to call repeatedly —
     * a no-op once loaded for the same path. Returns false (never throws) on any failure:
     * unsupported ABI, missing file, or a load error reported by the native engine.
     */
    suspend fun ensureLoaded(modelPath: String): Boolean = loadMutex.withLock {
        if (isLoaded && loadedModelPath == modelPath) return@withLock true
        if (!isDeviceSupported()) {
            Log.w(TAG, "Device ABI (${Build.SUPPORTED_ABIS.joinToString()}) unsupported — no arm64-v8a/x86_64 llama.cpp build")
            return@withLock false
        }
        val file = File(modelPath)
        if (!file.exists() || file.length() <= 0) {
            Log.w(TAG, "Model file missing or empty: $modelPath")
            return@withLock false
        }

        val flow = MutableSharedFlow<LlamaHelper.LLMEvent>(extraBufferCapacity = 64)
        val engine = LlamaHelper(context.contentResolver, scope, flow)
        val deferred = CompletableDeferred<Boolean>()

        val collectorJob = scope.launch {
            flow.collect { event ->
                when (event) {
                    is LlamaHelper.LLMEvent.Loaded -> if (!deferred.isCompleted) deferred.complete(true)
                    is LlamaHelper.LLMEvent.Error -> {
                        Log.e(TAG, "LLM load error: ${event.message}")
                        if (!deferred.isCompleted) deferred.complete(false)
                    }
                    else -> {}
                }
            }
        }

        val uri = Uri.fromFile(file).toString()
        engine.load(path = uri, contextLength = CONTEXT_LENGTH) { /* also signaled via flow above */ }

        val loaded = withTimeoutOrNull(LOAD_TIMEOUT_MS) { deferred.await() } ?: false
        collectorJob.cancel()

        if (loaded) {
            helper?.release()
            helper = engine
            eventFlow = flow
            isLoaded = true
            loadedModelPath = modelPath
            Log.d(TAG, "LLM loaded from $modelPath")
        } else {
            runCatching { engine.release() }
        }
        loaded
    }

    /**
     * Generates a real response to [prompt], or null if the model isn't loaded, generation
     * times out, or the engine reports an error. Never returns fabricated/canned text.
     */
    suspend fun generate(prompt: String): String? = generateMutex.withLock {
        val engine = helper
        val flow = eventFlow
        if (!isLoaded || engine == null || flow == null) return@withLock null

        val deferred = CompletableDeferred<String?>()
        val accumulated = StringBuilder()
        val collectorJob = scope.launch {
            flow.collect { event ->
                when (event) {
                    is LlamaHelper.LLMEvent.Started -> Log.d(TAG, "generation started")
                    is LlamaHelper.LLMEvent.Ongoing -> {
                        accumulated.append(event.word)
                        if (event.tokenCount % 8 == 0) Log.d(TAG, "generating... ${event.tokenCount} tokens so far")

                        val stopIndex = STOP_SEQUENCES.asSequence()
                            .map { accumulated.indexOf(it) }
                            .filter { it >= 0 }
                            .minOrNull()
                        if (!deferred.isCompleted && (stopIndex != null || event.tokenCount >= MAX_TOKENS)) {
                            val text = if (stopIndex != null) accumulated.substring(0, stopIndex) else accumulated.toString()
                            Log.d(TAG, "generate(): stopping early at ${event.tokenCount} tokens (${if (stopIndex != null) "stop sequence" else "max tokens"})")
                            deferred.complete(text.trim())
                            runCatching { engine.stopPrediction() }
                        }
                    }
                    is LlamaHelper.LLMEvent.Done -> {
                        Log.d(TAG, "generation done: ${event.tokenCount} tokens in ${event.duration}ms")
                        if (!deferred.isCompleted) deferred.complete(event.fullText)
                    }
                    is LlamaHelper.LLMEvent.Error -> {
                        Log.e(TAG, "LLM generation error: ${event.message}")
                        if (!deferred.isCompleted) deferred.complete(null)
                    }
                    else -> {}
                }
            }
        }

        try {
            Log.d(TAG, "generate(): calling predict() with ${prompt.length}-char prompt")
            engine.predict(prompt)
        } catch (e: Exception) {
            Log.e(TAG, "predict() threw: ${e.message}", e)
            collectorJob.cancel()
            return@withLock null
        }

        val result = withTimeoutOrNull(GENERATE_TIMEOUT_MS) { deferred.await() }
        if (result == null) {
            Log.w(TAG, "generate(): timed out after ${GENERATE_TIMEOUT_MS}ms — stopping in-flight completion")
            // Without this, the native completion keeps running after we give up on it, and the
            // next generate() call's predict() collides with it (confirmed on-device: a
            // "LlamaAndroid.launchCompletion" error immediately following a timeout).
            runCatching { engine.stopPrediction() }
        }
        collectorJob.cancel()
        result
    }

    fun release() {
        runCatching { helper?.release() }
        helper = null
        eventFlow = null
        isLoaded = false
        loadedModelPath = null
    }
}
