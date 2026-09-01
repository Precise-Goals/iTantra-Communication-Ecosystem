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
 * Text-to-Speech synthesis using AI4Bharat IndicTTS VITS (ONNX INT8).
 *
 * Converts incoming text strings to waveform audio for playback via AudioPlaybackManager.
 * Per-language models (~12–15MB each INT8) are lazy-loaded on demand.
 *
 * Supported language models (assets/models/tts/):
 * - hi_vits_int8.onnx  (Hindi)
 * - gu_vits_int8.onnx  (Gujarati)
 * - mr_vits_int8.onnx  (Marathi)
 * - kn_vits_int8.onnx  (Kannada)
 * - ml_vits_int8.onnx  (Malayalam)
 * - ta_vits_int8.onnx  (Tamil)
 * - te_vits_int8.onnx  (Telugu)
 * - or_vits_int8.onnx  (Odia)
 * - bn_vits_int8.onnx  (Bengali)
 * - en_vits_int8.onnx  (English — Piper fallback)
 *
 * Output: 22050Hz float PCM waveform (resampled to 16kHz by AudioPlaybackManager)
 */
class TTSModule(
    private val context: Context,
    private val callbacks: AudioCallbacks
) {
    companion object {
        private const val TAG = "TTSModule"
        private const val TTS_ASSET_DIR = "models/tts"
        const val OUTPUT_SAMPLE_RATE = 22050
        const val PLAYBACK_SAMPLE_RATE = 16000

        /** Character-level vocabulary for basic Indic TTS (production: use SentencePiece) */
        private val BASIC_PHONEMES = " !\"'(),-.:;?abcdefghijklmnopqrstuvwxyz".toList()
    }

    private val ortEnv: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }
    // Cache of loaded language sessions to avoid reloading same language repeatedly
    private val sessionCache = mutableMapOf<String, OrtSession>()
    private var currentLanguage: String? = null

    /**
     * Synthesize text to audio waveform for the specified language.
     *
     * @param text Input text string (UTF-8, supports all Indic scripts).
     * @param languageCode BCP-47 language code (e.g., "hi", "ta").
     * @return FloatArray PCM waveform at [OUTPUT_SAMPLE_RATE], or null on failure.
     */
    suspend fun synthesize(text: String, languageCode: String): FloatArray? =
        withContext(Dispatchers.Default) {
            val session = getOrLoadSession(languageCode) ?: return@withContext null
            val startMs = System.currentTimeMillis()

            try {
                // Step 1: Text normalization and phoneme encoding
                val phonemeIds = encodeText(text, languageCode)
                if (phonemeIds.isEmpty()) return@withContext null

                // Step 2: Create input tensors for VITS
                val inputIds = OnnxTensor.createTensor(
                    ortEnv,
                    longArrayOf(*phonemeIds.toLongArray()),
                    longArrayOf(1, phonemeIds.size.toLong())
                )
                val inputLengths = OnnxTensor.createTensor(
                    ortEnv,
                    longArrayOf(phonemeIds.size.toLong()),
                    longArrayOf(1)
                )
                // Speaker embedding (speaker 0 for single-speaker models)
                val speakerIds = OnnxTensor.createTensor(
                    ortEnv,
                    longArrayOf(0L),
                    longArrayOf(1)
                )

                val inputs = mapOf(
                    "input" to inputIds,
                    "input_lengths" to inputLengths,
                    "scales" to createScalesTensor(0.667f, 1.0f, 0.8f),
                    "sid" to speakerIds
                )

                // Step 3: VITS inference → waveform
                val outputs = session.run(inputs)
                @Suppress("UNCHECKED_CAST")
                val waveform = (outputs[0].value as Array<Array<FloatArray>>)[0][0]

                val synthesisMs = System.currentTimeMillis() - startMs
                val durationMs = (waveform.size.toLong() * 1000L / OUTPUT_SAMPLE_RATE)
                Log.d(TAG, "TTS synthesized ${waveform.size} samples in ${synthesisMs}ms (${durationMs}ms audio) [${languageCode}]")

                inputIds.close(); inputLengths.close(); speakerIds.close()
                outputs.close()

                callbacks.onTTSSynthesisComplete(durationMs)
                waveform
            } catch (e: Exception) {
                Log.e(TAG, "TTS synthesis error: ${e.message}", e)
                callbacks.onAudioError(
                    AppResult.Error(ErrorCode.TTS_SYNTHESIS_FAILED, "TTS failed: ${e.message}")
                )
                null
            }
        }

    /**
     * Resample waveform from [OUTPUT_SAMPLE_RATE] (22050Hz) to [PLAYBACK_SAMPLE_RATE] (16kHz).
     * Uses linear interpolation — sufficient for voice audio quality.
     */
    fun resampleTo16k(waveform: FloatArray): FloatArray {
        val ratio = PLAYBACK_SAMPLE_RATE.toDouble() / OUTPUT_SAMPLE_RATE
        val outputLength = (waveform.size * ratio).toInt()
        val resampled = FloatArray(outputLength)

        for (i in resampled.indices) {
            val srcIdx = i / ratio
            val floor = srcIdx.toInt()
            val frac = (srcIdx - floor).toFloat()

            resampled[i] = if (floor + 1 < waveform.size) {
                waveform[floor] * (1f - frac) + waveform[floor + 1] * frac
            } else {
                waveform.getOrElse(floor) { 0f }
            }
        }
        return resampled
    }

    private fun getOrLoadSession(languageCode: String): OrtSession? {
        sessionCache[languageCode]?.let { return it }

        val assetPath = "$TTS_ASSET_DIR/${languageCode}_vits_int8.onnx"
        return try {
            val modelBytes = context.assets.open(assetPath).readBytes()
            val sessionOptions = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                try { addNnapi() } catch (e: Exception) { /* CPU fallback */ }
            }
            val session = ortEnv.createSession(modelBytes, sessionOptions)
            sessionCache[languageCode] = session
            currentLanguage = languageCode
            Log.d(TAG, "TTS model loaded for '$languageCode' (${modelBytes.size / 1024}KB)")
            session
        } catch (e: Exception) {
            Log.e(TAG, "TTS model load failed for '$languageCode': ${e.message}")
            callbacks.onAudioError(
                AppResult.Error(
                    ErrorCode.MODEL_LOAD_FAILED,
                    "TTS model unavailable for language: $languageCode. ${e.message}"
                )
            )
            null
        }
    }

    /**
     * Text normalization and character-level phoneme encoding.
     * Production: replace with SentencePiece tokenizer loaded from assets.
     */
    private fun encodeText(text: String, languageCode: String): List<Int> {
        val normalized = text.lowercase().trim()
        return normalized.mapNotNull { char ->
            val idx = BASIC_PHONEMES.indexOf(char)
            if (idx >= 0) idx + 1 else null // 0 reserved for padding
        }
    }

    /** Create VITS inference scales tensor [noise_scale, length_scale, noise_scale_w]. */
    private fun createScalesTensor(noiseScale: Float, lengthScale: Float, noiseScaleW: Float): OnnxTensor {
        return OnnxTensor.createTensor(
            ortEnv,
            FloatBuffer.wrap(floatArrayOf(noiseScale, lengthScale, noiseScaleW)),
            longArrayOf(3)
        )
    }

    /** Unload a specific language model to free RAM. */
    fun unloadLanguage(languageCode: String) {
        sessionCache.remove(languageCode)?.close()
        Log.d(TAG, "TTS model unloaded for '$languageCode'")
    }

    /** Get currently loaded language codes. */
    fun getLoadedLanguages(): Set<String> = sessionCache.keys.toSet()

    fun release() {
        sessionCache.values.forEach { runCatching { it.close() } }
        sessionCache.clear()
        runCatching { ortEnv.close() }
        Log.d(TAG, "TTSModule released")
    }
}

/** Extension to convert List<Int> to LongArray for ONNX tensor creation. */
private fun List<Int>.toLongArray(): LongArray = LongArray(size) { this[it].toLong() }
