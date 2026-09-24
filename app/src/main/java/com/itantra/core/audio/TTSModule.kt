package com.itantra.core.audio

import android.content.Context
import android.util.Log
import com.itantra.core.download.ModelRegistry
import com.itantra.domain.contracts.AudioCallbacks
import com.itantra.domain.model.AppResult
import com.itantra.domain.model.ErrorCode
import com.itantra.domain.model.ModelPack
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Text-to-Speech synthesis using sherpa-onnx's [OfflineTts] — real espeak-ng-based phonemization
 * over Piper/Coqui/Mimic3 VITS voices (see `ModelRegistry`'s class doc for exactly which
 * languages have a verified free voice source; the rest report a real error, never fabricated
 * audio).
 *
 * 100% offline — zero cloud/Google Speech services. Every voice shares one downloaded
 * `espeak-ng-data` directory (ModelPack.ESPEAK_NG_DATA); per-language voice bundles are
 * downloaded+extracted by ModelDownloadManager into `filesDir/models/tts/{lang}/`.
 */
class TTSModule(
    private val context: Context,
    private val callbacks: AudioCallbacks
) {
    companion object {
        private const val TAG = "TTSModule"
        const val PLAYBACK_SAMPLE_RATE = 16000

        /** Only languages with a real, verified sherpa-onnx voice source — see ModelRegistry. */
        private val LANGUAGE_TO_PACK: Map<String, ModelPack> = mapOf(
            "hi" to ModelPack.TTS_HINDI,
            "gu" to ModelPack.TTS_GUJARATI,
            "ml" to ModelPack.TTS_MALAYALAM,
            "bn" to ModelPack.TTS_BENGALI,
            "en" to ModelPack.TTS_ENGLISH
        )
    }

    private val ttsCache = mutableMapOf<String, OfflineTts>()

    /** One synthesis or voice load at a time (T70). The cache is a plain HashMap, and two
     *  messages arriving during a cold load would otherwise both load the same voice. */
    private val ttsLock = kotlinx.coroutines.sync.Mutex()

    /** Synthesized PCM plus the sample rate it must be played at. */
    data class SynthesisResult(val samples: FloatArray, val sampleRate: Int)

    /**
     * Synthesize text to a PCM waveform for the specified language, at the voice's native
     * sample rate.
     *
     * @param text Input text string (UTF-8, supports all Indic scripts).
     * @param languageCode BCP-47 language code (e.g., "hi", "en"). Only [LANGUAGE_TO_PACK]'s
     *   languages have a real voice; anything else — or a supported language whose voice pack
     *   isn't downloaded yet — returns null and reports a real [ErrorCode.MODEL_LOAD_FAILED].
     * @return [SynthesisResult], or null on failure. Never returns fabricated audio.
     */
    suspend fun synthesize(text: String, languageCode: String): SynthesisResult? =
        ttsLock.withLock { synthesizeUnlocked(text, languageCode) }

    private suspend fun synthesizeUnlocked(text: String, languageCode: String): SynthesisResult? =
        withContext(Dispatchers.Default) {
            val tts = getOrLoadTts(languageCode)
            if (tts == null) {
                callbacks.onAudioError(
                    AppResult.Error(
                        ErrorCode.MODEL_LOAD_FAILED,
                        "TTS not available for '$languageCode' (unsupported language or voice pack not downloaded)"
                    )
                )
                return@withContext null
            }

            try {
                val startMs = System.currentTimeMillis()
                val audio = tts.generate(text = text)
                val synthesisMs = System.currentTimeMillis() - startMs
                val durationMs = audio.samples.size.toLong() * 1000L / audio.sampleRate

                Log.d(
                    TAG,
                    "sherpa-onnx TTS synthesized ${audio.samples.size} samples @ ${audio.sampleRate}Hz " +
                        "in ${synthesisMs}ms [$languageCode]"
                )

                callbacks.onTTSSynthesisComplete(durationMs)
                // Return the voice's native sample rate. The previous linear-interpolation
                // downsample to 16kHz had no anti-aliasing filter, so everything above 8kHz
                // folded back into the audible band as metallic artifacts — and AudioTrack
                // plays 22050Hz natively, so the resample bought nothing.
                SynthesisResult(audio.samples, audio.sampleRate)
            } catch (e: Exception) {
                Log.e(TAG, "TTS synthesis error for '$languageCode': ${e.message}", e)
                callbacks.onAudioError(
                    AppResult.Error(ErrorCode.TTS_SYNTHESIS_FAILED, "TTS failed: ${e.message}")
                )
                null
            }
        }

    /**
     * Loads (or returns the cached) [OfflineTts] instance for [languageCode]. Returns null when
     * the language has no known voice source, or its bundle / the shared espeak-ng-data hasn't
     * been downloaded yet — callers must treat null as "genuinely unavailable," not retry-forever.
     */
    private fun getOrLoadTts(languageCode: String): OfflineTts? {
        ttsCache[languageCode]?.let { return it }

        val pack = LANGUAGE_TO_PACK[languageCode] ?: return null
        val voiceDirName = ModelRegistry.getInfo(pack)?.extractDirName ?: return null
        val espeakDirName = ModelRegistry.getInfo(ModelPack.ESPEAK_NG_DATA)?.extractDirName ?: return null

        val voiceDir = File(context.filesDir, "models/$voiceDirName")
        val espeakDataDir = File(context.filesDir, "models/$espeakDirName")
        if (!voiceDir.isDirectory || !espeakDataDir.isDirectory) return null

        val onnxFile = voiceDir.listFiles { f -> f.extension == "onnx" }?.firstOrNull() ?: return null
        val tokensFile = File(voiceDir, "tokens.txt")
        if (!tokensFile.exists()) return null

        return try {
            val config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = onnxFile.absolutePath,
                        tokens = tokensFile.absolutePath,
                        dataDir = espeakDataDir.absolutePath
                    ),
                    numThreads = 2,
                    debug = false,
                    provider = "cpu"
                )
            )
            val tts = OfflineTts(assetManager = null, config = config)
            ttsCache[languageCode] = tts
            Log.d(TAG, "sherpa-onnx TTS loaded for '$languageCode' from ${onnxFile.name}")
            tts
        } catch (e: Exception) {
            Log.w(TAG, "sherpa-onnx TTS load failed for '$languageCode': ${e.message}")
            null
        }
    }

    /**
     * Load [languageCode]'s voice into the cache without synthesizing anything (T45), so the first
     * received message does not pay the load. Returns false if there is no voice for it or its
     * pack is not downloaded. Takes the same lock as synthesize() (T70).
     */
    suspend fun warmUp(languageCode: String): Boolean =
        ttsLock.withLock { withContext(Dispatchers.Default) { getOrLoadTts(languageCode) != null } }

    fun getLoadedLanguages(): Set<String> = ttsCache.keys.toSet()

    fun unloadLanguage(languageCode: String) {
        ttsCache.remove(languageCode)?.release()
    }

    fun release() {
        ttsCache.values.forEach { runCatching { it.release() } }
        ttsCache.clear()
    }
}
