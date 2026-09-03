package com.itantra.core.audio

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import android.util.Log
import com.itantra.core.download.ModelAssetExtractor
import com.itantra.domain.contracts.AudioCallbacks
import com.itantra.domain.model.AppResult
import com.itantra.domain.model.ErrorCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.PI
import kotlin.math.sin

/**
 * Speech-to-Text module using AI4Bharat IndicConformer (sherpa-onnx export, ONNX INT8).
 *
 * There is no single "multilingual" model — sherpa-onnx ships one ONNX graph per language,
 * each with its own tokens.txt vocabulary alongside it. Sessions and vocabularies are
 * lazy-loaded and cached per language code.
 *
 * Model files: filesDir/models/stt_{lang}_int8.onnx + stt_{lang}_tokens.txt
 *
 * If a language's model/vocab isn't downloaded, [transcribe] returns
 * [AppResult.Error] with [ErrorCode.MODEL_LOAD_FAILED] — it never fabricates text.
 */
class STTModule(
    private val context: Context,
    private val callbacks: AudioCallbacks
) {
    companion object {
        private const val TAG = "STTModule"
        const val SAMPLE_RATE = 16000
        private const val N_MELS = 80
        private const val FRAME_LENGTH = 400   // 25ms window at 16kHz
        private const val HOP_LENGTH = 160     // 10ms hop at 16kHz

        /** Candidate input names for the acoustic feature tensor, in priority order. */
        private val FEATURE_INPUT_ALIASES = listOf("audio_signal", "x", "features", "input", "waveform")
        /** Candidate input names for the sequence-length tensor, in priority order. */
        private val LENGTH_INPUT_ALIASES = listOf("length", "x_lens", "input_length", "x_length")
    }

    private val ortEnv: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }
    private val sessionCache = mutableMapOf<String, OrtSession>()
    private val vocabCache = mutableMapOf<String, Array<String>>()
    private val ioNamesCache = mutableMapOf<String, IoNames>()

    private data class IoNames(val featureInput: String, val lengthInput: String?, val outputName: String)

    /**
     * Ensure the session + tokenizer for [languageCode] are loaded (downloading is handled
     * separately by ModelDownloadManager — this only loads what's already on disk).
     * @return true if the language is ready to transcribe, false if the model/vocab is missing.
     */
    suspend fun ensureLoaded(languageCode: String): Boolean = withContext(Dispatchers.Default) {
        if (sessionCache.containsKey(languageCode) && vocabCache.containsKey(languageCode)) {
            return@withContext true
        }

        try {
            val startMs = System.currentTimeMillis()

            val modelPath = ModelAssetExtractor.getPhysicalModelPath(
                context, "stt_${languageCode}_int8.onnx", "models/stt/${languageCode}_model.int8.onnx"
            )
            val vocabPath = ModelAssetExtractor.getPhysicalModelPath(
                context, "stt_${languageCode}_tokens.txt", "models/stt/${languageCode}_tokens.txt"
            )

            if (modelPath == null || vocabPath == null) {
                Log.w(TAG, "STT model or tokenizer not available on disk for '$languageCode' (model=$modelPath, vocab=$vocabPath)")
                return@withContext false
            }

            val vocab = parseTokensFile(vocabPath)
            if (vocab.isEmpty()) {
                Log.w(TAG, "STT tokenizer for '$languageCode' parsed to an empty vocabulary")
                return@withContext false
            }

            val sessionOptions = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
                setInterOpNumThreads(1)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                try {
                    addNnapi()
                    Log.d(TAG, "STT('$languageCode'): NNAPI delegate enabled")
                } catch (e: Exception) {
                    Log.d(TAG, "STT('$languageCode'): NNAPI unavailable, falling back to CPU XNNPACK")
                }
            }

            val session = ortEnv.createSession(modelPath, sessionOptions)
            val ioNames = resolveIoNames(session) ?: run {
                Log.e(TAG, "STT('$languageCode'): could not resolve input/output tensor names, closing session")
                session.close()
                return@withContext false
            }

            sessionCache[languageCode] = session
            vocabCache[languageCode] = vocab
            ioNamesCache[languageCode] = ioNames

            val loadMs = System.currentTimeMillis() - startMs
            Log.d(TAG, "STT('$languageCode') loaded in ${loadMs}ms — vocab size ${vocab.size}, inputs=${ioNames}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "STT('$languageCode') load failed: ${e.message}", e)
            false
        }
    }

    /**
     * Inspect the session's real input/output tensor names instead of assuming fixed ones —
     * the exact sherpa-onnx export convention can't be verified without the actual model file.
     * Picks the length input (rank <= 1) and feature input (everything else) by shape when the
     * alias lists don't match, and fails explicitly rather than guessing wrong.
     */
    private fun resolveIoNames(session: OrtSession): IoNames? {
        val inputNames = session.inputNames
        val outputNames = session.outputNames
        val outputName = outputNames.firstOrNull() ?: return null

        FEATURE_INPUT_ALIASES.firstOrNull { it in inputNames }?.let { feature ->
            val length = LENGTH_INPUT_ALIASES.firstOrNull { it in inputNames }
            return IoNames(feature, length, outputName)
        }

        // No known alias matched — fall back to shape-based inference.
        val inputInfo = session.inputInfo
        val lengthCandidates = inputInfo.filter { (_, info) ->
            ((info.info as? TensorInfo)?.shape?.size ?: -1) <= 1
        }.keys
        val featureCandidates = inputNames - lengthCandidates

        val feature = featureCandidates.firstOrNull() ?: return null
        val length = lengthCandidates.firstOrNull()
        return IoNames(feature, length, outputName)
    }

    /** Reads a sherpa-onnx `tokens.txt` file from disk and parses it via [CtcDecoder.parseTokens]. */
    private fun parseTokensFile(path: String): Array<String> =
        CtcDecoder.parseTokens(java.io.File(path).readText())

    /**
     * Transcribe a speech audio buffer to text.
     *
     * @param audioBuffer PCM float samples [-1.0, 1.0] at 16kHz.
     * @param languageCode BCP-47 language code (e.g., "hi", "ta", "en").
     * @return Transcribed text string, or [AppResult.Error] if the model isn't downloaded
     *   or inference fails. Never returns fabricated placeholder text.
     */
    suspend fun transcribe(
        audioBuffer: FloatArray,
        languageCode: String = "hi"
    ): AppResult<String> = withContext(Dispatchers.Default) {
        val sess = sessionCache[languageCode]
        val vocab = vocabCache[languageCode]
        val ioNames = ioNamesCache[languageCode]

        if (sess == null || vocab == null || ioNames == null) {
            val error = AppResult.Error(
                ErrorCode.MODEL_LOAD_FAILED,
                "STT model not downloaded for language '$languageCode'"
            )
            callbacks.onAudioError(error)
            return@withContext error
        }

        val inferenceStart = System.currentTimeMillis()
        try {
            val features = extractLogMelSpectrogram(audioBuffer)
            val numFrames = features.size / N_MELS
            if (numFrames <= 0) {
                return@withContext AppResult.Error(ErrorCode.STT_INFERENCE_FAILED, "Audio buffer too short to transcribe")
            }

            val featureTensor = OnnxTensor.createTensor(
                ortEnv,
                FloatBuffer.wrap(features),
                longArrayOf(1, N_MELS.toLong(), numFrames.toLong())
            )

            val inputs = mutableMapOf(ioNames.featureInput to featureTensor)
            val lengthTensor = if (ioNames.lengthInput != null) {
                OnnxTensor.createTensor(ortEnv, longArrayOf(numFrames.toLong())).also { inputs[ioNames.lengthInput] = it }
            } else null

            val outputs = sess.run(inputs)

            @Suppress("UNCHECKED_CAST")
            val logits = outputs[0].value as Array<Array<FloatArray>>
            // NeMo/IndicConformer CTC convention: blank is the LAST vocab entry, not id 0
            // (confirmed on-device: hi's tokens.txt has "<unk> 0" ... "<blk> 5632").
            val text = CtcDecoder.greedyDecode(logits[0], vocab, blankId = vocab.size - 1)

            val inferenceMs = System.currentTimeMillis() - inferenceStart
            Log.d(TAG, "STT inference: '${text.take(50)}' in ${inferenceMs}ms [${languageCode}]")

            featureTensor.close()
            lengthTensor?.close()
            outputs.close()

            if (text.isBlank()) {
                AppResult.Error(ErrorCode.STT_INFERENCE_FAILED, "Empty transcription")
            } else {
                val confidence = estimateConfidence(logits[0])
                callbacks.onSTTResult(AppResult.Success(text), confidence, inferenceMs)
                AppResult.Success(text)
            }
        } catch (e: Exception) {
            Log.e(TAG, "STT inference error: ${e.message}", e)
            callbacks.onAudioError(
                AppResult.Error(ErrorCode.STT_INFERENCE_FAILED, "STT failed: ${e.message}")
            )
            AppResult.Error(ErrorCode.STT_INFERENCE_FAILED, e.message ?: "Unknown error")
        }
    }

    /**
     * Extract 80-dimensional log-mel spectrogram features from raw PCM audio.
     * Uses standard mel filterbank parameters matching IndicConformer training config.
     */
    private fun extractLogMelSpectrogram(audio: FloatArray): FloatArray {
        val frames = mutableListOf<FloatArray>()
        var start = 0

        while (start + FRAME_LENGTH <= audio.size) {
            val frame = audio.copyOfRange(start, start + FRAME_LENGTH)

            // Apply Hann window
            for (i in frame.indices) {
                frame[i] *= (0.5f * (1f - cos(2.0 * PI * i / (FRAME_LENGTH - 1)))).toFloat()
            }

            // FFT magnitude spectrum (simplified — real impl uses FFTW or KissFFT via JNI)
            val spectrum = computePowerSpectrum(frame)

            // Apply mel filterbank (80 filters, 0Hz–8000Hz)
            val melFeatures = applyMelFilterbank(spectrum, N_MELS, SAMPLE_RATE)

            // Log compression
            for (i in melFeatures.indices) {
                melFeatures[i] = (ln(melFeatures[i].toDouble() + 1e-10)).toFloat()
            }

            frames.add(melFeatures)
            start += HOP_LENGTH
        }

        // Per-feature (per-mel-channel) normalization across this utterance's frames — NeMo's
        // AudioToMelSpectrogramPreprocessor default ("normalize: per_feature"). IndicConformer is
        // NeMo-trained, so it expects normalized input; without this the encoder saw
        // out-of-distribution magnitudes and collapsed to the same predicted token regardless of
        // audio content (confirmed on-device: three different-length recordings all decoded to
        // the same single repeated character).
        val T = frames.size
        for (m in 0 until N_MELS) {
            var mean = 0.0
            for (t in 0 until T) mean += frames[t][m]
            mean /= T
            var variance = 0.0
            for (t in 0 until T) {
                val d = frames[t][m] - mean
                variance += d * d
            }
            val std = kotlin.math.sqrt(variance / T)
            val denom = (std + 1e-5).toFloat()
            for (t in 0 until T) frames[t][m] = ((frames[t][m] - mean) / denom).toFloat()
        }

        // Flatten [T, N_MELS] → [N_MELS, T] (transpose for model input)
        val result = FloatArray(N_MELS * T)
        for (t in 0 until T) {
            for (m in 0 until N_MELS) {
                result[m * T + t] = frames[t][m]
            }
        }
        return result
    }

    /** Compute power spectrum via naive DFT (production should use FFTW via JNI). */
    private fun computePowerSpectrum(frame: FloatArray): FloatArray {
        val N = frame.size
        val halfN = N / 2 + 1
        val spectrum = FloatArray(halfN)
        for (k in 0 until halfN) {
            var re = 0.0
            var im = 0.0
            for (n in frame.indices) {
                val angle = 2.0 * PI * k * n / N
                re += frame[n] * cos(angle)
                im -= frame[n] * sin(angle)
            }
            spectrum[k] = (re * re + im * im).toFloat()
        }
        return spectrum
    }

    /** Apply mel filterbank to linear frequency spectrum. */
    private fun applyMelFilterbank(spectrum: FloatArray, nMels: Int, sampleRate: Int): FloatArray {
        val fMax = sampleRate / 2.0
        val melMin = hzToMel(0.0)
        val melMax = hzToMel(fMax)
        val melPoints = FloatArray(nMels + 2) { i ->
            melToHz(melMin + i * (melMax - melMin) / (nMels + 1)).toFloat()
        }

        val result = FloatArray(nMels)
        val fftBins = spectrum.size
        for (m in 0 until nMels) {
            var energy = 0f
            for (k in spectrum.indices) {
                val freq = k.toFloat() * sampleRate / (2 * (fftBins - 1))
                val lower = melPoints[m]
                val center = melPoints[m + 1]
                val upper = melPoints[m + 2]
                val weight = when {
                    freq >= lower && freq <= center -> (freq - lower) / (center - lower)
                    freq > center && freq <= upper  -> (upper - freq) / (upper - center)
                    else                            -> 0f
                }
                energy += spectrum[k] * weight
            }
            result[m] = energy
        }
        return result
    }

    private fun hzToMel(hz: Double) = 2595.0 * Math.log10(1.0 + hz / 700.0)
    private fun melToHz(mel: Double) = 700.0 * (Math.pow(10.0, mel / 2595.0) - 1.0)

    private fun estimateConfidence(logits: Array<FloatArray>): Float {
        if (logits.isEmpty()) return 0f
        var sumMax = 0f
        for (frame in logits) {
            val maxProb = frame.max()
            sumMax += maxProb
        }
        return (sumMax / logits.size).coerceIn(0f, 1f)
    }

    fun release() {
        sessionCache.values.forEach { runCatching { it.close() } }
        sessionCache.clear()
        vocabCache.clear()
        ioNamesCache.clear()
        Log.d(TAG, "STTModule released")
    }

    fun isLanguageLoaded(languageCode: String): Boolean =
        sessionCache.containsKey(languageCode) && vocabCache.containsKey(languageCode)
}
