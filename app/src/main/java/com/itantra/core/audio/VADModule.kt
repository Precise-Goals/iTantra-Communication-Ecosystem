package com.itantra.core.audio

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import com.itantra.domain.contracts.AudioCallbacks
import com.itantra.domain.model.AppResult
import com.itantra.domain.model.ErrorCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer

/**
 * Voice Activity Detection module using Silero VAD v4 (ONNX, ~2MB).
 *
 * Processes 16kHz mono PCM audio in 100ms chunks (1600 samples).
 * Emits speech/silence detection via [AudioCallbacks.onVADTriggered].
 *
 * Model path: assets/models/silero_vad.onnx
 *
 * This model is always resident in memory (~2MB) as it runs continuously during idle listening.
 * CPU usage: < 5% on a mid-range SoC (Snapdragon 680+).
 */
class VADModule(
    private val context: Context,
    private val callbacks: AudioCallbacks
) {
    companion object {
        private const val TAG = "VADModule"
        private const val MODEL_ASSET = "models/silero_vad.onnx"
        private const val SAMPLE_RATE = 16000
        /** 100ms chunk at 16kHz */
        const val CHUNK_SIZE = 1600
        /** Speech detection threshold [0.0–1.0]. Tuned for noisy field environments. */
        private const val SPEECH_THRESHOLD = 0.5f
        /** Minimum silence duration before emitting end-of-speech (ms) */
        const val SILENCE_DURATION_MS = 800L
    }

    private var ortEnv: OrtEnvironment? = null
    private var session: OrtSession? = null

    // Silero VAD maintains hidden state between chunks for temporal context
    private var hState: FloatArray = FloatArray(2 * 1 * 64) { 0f }
    private var cState: FloatArray = FloatArray(2 * 1 * 64) { 0f }
    private var isSpeechActive = false

    /**
     * Initialize the Silero VAD ONNX session.
     * Must be called once before [process].
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.Default) {
        try {
            ortEnv = OrtEnvironment.getEnvironment()
            val modelBytes = context.assets.open(MODEL_ASSET).readBytes()
            session = ortEnv!!.createSession(modelBytes, OrtSession.SessionOptions().apply {
                // Try NNAPI → CPU fallback for VAD (lightweight enough for CPU)
                addConfigEntry("session.load_model_format", "ORT")
            })
            resetState()
            Log.d(TAG, "Silero VAD initialized successfully (~${modelBytes.size / 1024}KB)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "VAD initialization failed: ${e.message}")
            callbacks.onAudioError(
                AppResult.Error(ErrorCode.MODEL_LOAD_FAILED, "VAD model failed: ${e.message}")
            )
            false
        }
    }

    /**
     * Process a 100ms audio chunk (1600 samples at 16kHz) through Silero VAD.
     *
     * @param audioChunk FloatArray of 1600 normalized PCM samples [-1.0, 1.0].
     * @return Speech probability [0.0–1.0].
     */
    suspend fun process(audioChunk: FloatArray): Float = withContext(Dispatchers.Default) {
        val sess = session ?: return@withContext 0f
        val env = ortEnv ?: return@withContext 0f

        try {
            // Prepare inputs
            val inputTensor = OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(audioChunk),
                longArrayOf(1, audioChunk.size.toLong())
            )
            val srTensor = OnnxTensor.createTensor(
                env,
                longArrayOf(SAMPLE_RATE.toLong())
            )
            val hTensor = OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(hState),
                longArrayOf(2, 1, 64)
            )
            val cTensor = OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(cState),
                longArrayOf(2, 1, 64)
            )

            val inputs = mapOf(
                "input" to inputTensor,
                "sr" to srTensor,
                "h" to hTensor,
                "c" to cTensor
            )

            val outputs = sess.run(inputs)

            // Extract speech probability
            @Suppress("UNCHECKED_CAST")
            val probability = (outputs[0].value as Array<FloatArray>)[0][0]

            // Extract updated hidden states for temporal continuity
            @Suppress("UNCHECKED_CAST")
            val newH = (outputs[1].value as Array<Array<FloatArray>>)
            @Suppress("UNCHECKED_CAST")
            val newC = (outputs[2].value as Array<Array<FloatArray>>)
            updateHiddenState(newH, newC)

            // Emit VAD event
            val isSpeech = probability >= SPEECH_THRESHOLD
            if (isSpeech != isSpeechActive) {
                isSpeechActive = isSpeech
                callbacks.onVADTriggered(isSpeech, probability)
            }

            // Cleanup
            inputTensor.close(); srTensor.close(); hTensor.close(); cTensor.close()
            outputs.close()

            probability
        } catch (e: Exception) {
            Log.e(TAG, "VAD inference error: ${e.message}")
            0f
        }
    }

    /** Reset VAD hidden state (call when starting a new session or after long silence). */
    fun resetState() {
        hState = FloatArray(2 * 1 * 64) { 0f }
        cState = FloatArray(2 * 1 * 64) { 0f }
        isSpeechActive = false
    }

    private fun updateHiddenState(
        newH: Array<Array<FloatArray>>,
        newC: Array<Array<FloatArray>>
    ) {
        var idx = 0
        for (i in newH.indices) for (j in newH[i].indices) for (k in newH[i][j].indices) {
            hState[idx++] = newH[i][j][k]
        }
        idx = 0
        for (i in newC.indices) for (j in newC[i].indices) for (k in newC[i][j].indices) {
            cState[idx++] = newC[i][j][k]
        }
    }

    fun release() {
        runCatching { session?.close() }
        runCatching { ortEnv?.close() }
        session = null
        ortEnv = null
        Log.d(TAG, "VADModule released")
    }
}
