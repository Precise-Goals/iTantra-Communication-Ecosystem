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
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import kotlin.math.ln
import kotlin.math.PI

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
        /** STT sessions kept resident (T46). 2 covers switching back and forth between two languages. */
        private const val MAX_CACHED_LANGUAGES = 2
        const val SAMPLE_RATE = 16000
        private const val N_MELS = 80
        /** T78: SraVaani's encoder needs 128 mel bins (confirmed via T77 Step 2 and the real
         *  preproc.pt params) — every other STFT field (N_FFT, FRAME_LENGTH, HOP_LENGTH, preemph,
         *  log guard, per-feature normalization) is identical to IndicConformer's. */
        internal const val SRAVAANI_N_MELS = 128
        private const val FRAME_LENGTH = 400   // 25ms window at 16kHz
        private const val HOP_LENGTH = 160     // 10ms hop at 16kHz
        /** FFT size: next power of two at or above FRAME_LENGTH. NeMo pads the 400-sample
         *  window to 512 rather than transforming 400 points directly. */
        private const val N_FFT = 512

        /** Candidate input names for the acoustic feature tensor, in priority order. */
        private val FEATURE_INPUT_ALIASES = listOf("audio_signal", "x", "features", "input", "waveform")
        /** Candidate input names for the sequence-length tensor, in priority order. */
        private val LENGTH_INPUT_ALIASES = listOf("length", "x_lens", "input_length", "x_length")
    }

    private val ortEnv: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }
    // Bounded LRU (T46). Each session is a ~197MB native allocation; caching every language a
    // user ever tapped held them all for the process lifetime. Eviction only happens inside
    // ensureLoaded(), which holds inferenceLock, so a session is never closed while in use.
    private val sessionCache = object : LinkedHashMap<String, OrtSession>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, OrtSession>): Boolean {
            if (size > MAX_CACHED_LANGUAGES) {
                runCatching { eldest.value.close() }
                vocabCache.remove(eldest.key)
                ioNamesCache.remove(eldest.key)
                Log.d(TAG, "Evicted STT session '${eldest.key}' (LRU)")
                return true
            }
            return false
        }
    }
    private val vocabCache = mutableMapOf<String, Array<String>>()
    private val ioNamesCache = mutableMapOf<String, IoNames>()

    /** One inference or model load at a time (T65). The feature extractor reuses member scratch
     *  buffers and the caches are plain HashMaps, so concurrent calls corrupt each other. */
    private val inferenceLock = kotlinx.coroutines.sync.Mutex()

    private data class IoNames(val featureInput: String, val lengthInput: String?, val outputName: String)

    /** Set by the caller before transcribe(); used to stamp feature-extraction timing. */
    @Volatile var currentUtterance: com.itantra.core.telemetry.Telemetry.Utterance? = null

    // ── Precomputed FFT and mel filterbank ────────────────────────────────
    // Built once on first use. The previous implementation recomputed a naive O(N^2) DFT and
    // rebuilt the entire mel filterbank on every single frame, which cost ~950ms per 3s of
    // audio and put RTF above 1.0 before the ONNX session even ran.

    /** Bit-reversal permutation table for the radix-2 FFT. */
    private val fftReverse: IntArray by lazy {
        val rev = IntArray(N_FFT)
        var j = 0
        for (i in 1 until N_FFT) {
            var bit = N_FFT shr 1
            while (j >= bit) { j -= bit; bit = bit shr 1 }
            j += bit
            rev[i] = j
        }
        rev
    }

    /** Symmetric Hann window, length FRAME_LENGTH. NeMo's FilterbankFeatures builds its window
     *  with `window_fn(win_length, periodic=False)` — confirmed from the installed nemo_toolkit
     *  source (T23) — not torch.hann_window's own periodic default. */
    private val hannWindow: FloatArray by lazy {
        FloatArray(FRAME_LENGTH) { i ->
            (0.5 * (1.0 - kotlin.math.cos(2.0 * PI * i / (FRAME_LENGTH - 1)))).toFloat()
        }
    }

    /** Sparse mel filterbank: for each mel band, the first FFT bin and its triangular weights. */
    private class MelBank(val startBin: IntArray, val weights: Array<FloatArray>)

    /** T78: built once per distinct mel-bin count (80 for IndicConformer, 128 for SraVaani),
     *  not once per class instance — [buildMelBank] takes no other per-call parameters, so the
     *  bin count alone determines the bank. */
    private val melBankCache = mutableMapOf<Int, MelBank>()
    private fun melBankFor(nMels: Int): MelBank = melBankCache.getOrPut(nMels) { buildMelBank(nMels) }

    /** Scratch buffers, reused across frames to keep allocation constant per utterance. */
    private val fftRe = DoubleArray(N_FFT)
    private val fftIm = DoubleArray(N_FFT)
    private val powerSpectrum = FloatArray(N_FFT / 2 + 1)

    /**
     * Build the mel filterbank once, with Slaney area normalization — librosa's `norm="slaney"`,
     * which is what NeMo's AudioToMelSpectrogramPreprocessor uses. Without it the wide
     * high-frequency bands carry systematically more energy than the encoder saw in training.
     * The Hz<->mel warping itself (hzToMel/melToHz below) is also librosa's Slaney scale
     * (`htk=False`, the call's default since NeMo never passes `htk=True`) — a separate thing
     * from the area normalization, and previously wrong (T23).
     */
    private fun buildMelBank(nMels: Int): MelBank {
        val nBins = N_FFT / 2 + 1
        val melMin = hzToMel(0.0)
        val melMax = hzToMel(SAMPLE_RATE / 2.0)
        val melPoints = DoubleArray(nMels + 2) { i ->
            melToHz(melMin + i * (melMax - melMin) / (nMels + 1))
        }
        val binFreq = DoubleArray(nBins) { k -> k.toDouble() * SAMPLE_RATE / N_FFT }

        val starts = IntArray(nMels)
        val weightRows = Array(nMels) { FloatArray(0) }

        for (m in 0 until nMels) {
            val lower = melPoints[m]
            val center = melPoints[m + 1]
            val upper = melPoints[m + 2]
            // Slaney normalization: scale each triangle by 2/(upper-lower) so filters have
            // equal area rather than equal peak height.
            val enorm = 2.0 / (upper - lower)

            var first = -1
            var last = -1
            for (k in 0 until nBins) {
                val f = binFreq[k]
                if (f > lower && f < upper) {
                    if (first < 0) first = k
                    last = k
                }
            }
            if (first < 0) { starts[m] = 0; weightRows[m] = FloatArray(0); continue }

            val w = FloatArray(last - first + 1)
            for (k in first..last) {
                val f = binFreq[k]
                val v = if (f <= center) (f - lower) / (center - lower)
                        else (upper - f) / (upper - center)
                w[k - first] = (v * enorm).toFloat()
            }
            starts[m] = first
            weightRows[m] = w
        }
        return MelBank(starts, weightRows)
    }

    /**
     * In-place radix-2 Cooley-Tukey FFT over [fftRe]/[fftIm], then power spectrum into
     * [powerSpectrum]. Caller must have filled fftRe with the windowed, zero-padded frame
     * and zeroed fftIm.
     */
    private fun fftPowerInPlace() {
        for (i in 1 until N_FFT) {
            val j = fftReverse[i]
            if (i < j) {
                var t = fftRe[i]; fftRe[i] = fftRe[j]; fftRe[j] = t
                t = fftIm[i]; fftIm[i] = fftIm[j]; fftIm[j] = t
            }
        }
        var len = 2
        while (len <= N_FFT) {
            val ang = -2.0 * PI / len
            val wr = kotlin.math.cos(ang)
            val wi = kotlin.math.sin(ang)
            var i = 0
            while (i < N_FFT) {
                var cr = 1.0
                var ci = 0.0
                for (k in 0 until len / 2) {
                    val u = i + k
                    val v = i + k + len / 2
                    val tr = fftRe[v] * cr - fftIm[v] * ci
                    val ti = fftRe[v] * ci + fftIm[v] * cr
                    fftRe[v] = fftRe[u] - tr; fftIm[v] = fftIm[u] - ti
                    fftRe[u] += tr;           fftIm[u] += ti
                    val nr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = nr
                }
                i += len
            }
            len = len shl 1
        }
        for (k in powerSpectrum.indices) {
            powerSpectrum[k] = (fftRe[k] * fftRe[k] + fftIm[k] * fftIm[k]).toFloat()
        }
    }

    /**
     * Ensure the session + tokenizer for [languageCode] are loaded (downloading is handled
     * separately by ModelDownloadManager — this only loads what's already on disk).
     * @return true if the language is ready to transcribe, false if the model/vocab is missing.
     */
    suspend fun ensureLoaded(languageCode: String): Boolean =
        inferenceLock.withLock { ensureLoadedUnlocked(languageCode) }

    private suspend fun ensureLoadedUnlocked(languageCode: String): Boolean = withContext(Dispatchers.Default) {
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
    ): AppResult<String> = inferenceLock.withLock { transcribeUnlocked(audioBuffer, languageCode) }

    private suspend fun transcribeUnlocked(
        audioBuffer: FloatArray,
        languageCode: String
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
            currentUtterance?.featureDoneNs = System.nanoTime()
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
                currentUtterance?.charCount = text.length
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
     * Extract [nMels]-dimensional log-mel spectrogram features from raw PCM audio, matching
     * NeMo's AudioToMelSpectrogramPreprocessor as configured for the AI4Bharat IndicConformer
     * checkpoints — confirmed field-by-field against the real checkpoint config and the
     * installed nemo_toolkit's FilterbankFeatures source (T23), not assumed defaults.
     *
     * T78: [nMels] defaults to 80 (IndicConformer) so this call is byte-for-byte unchanged for
     * every existing caller; pass [SRAVAANI_N_MELS] (128) for the SraVaani path. Every other STFT
     * field (N_FFT, FRAME_LENGTH, HOP_LENGTH, preemph, log guard, normalization) is identical
     * between the two models (confirmed against SraVaani's real preproc.pt in T77/T78 Step 2).
     */
    @androidx.annotation.VisibleForTesting
    internal fun extractLogMelSpectrogram(input: FloatArray, nMels: Int = N_MELS): FloatArray {
        // NeMo applies preemph=0.97 once to the whole signal before framing, not per-frame.
        val audio = FloatArray(input.size)
        if (input.isNotEmpty()) {
            audio[0] = input[0]
            for (i in 1 until input.size) audio[i] = input[i] - 0.97f * input[i - 1]
        }

        val bank = melBankFor(nMels)
        // NeMo's log_zero_guard_value default is 2**-24, not the 1e-10 used previously.
        val logGuard = 5.9604645e-8

        // torch.stft(..., center=True, pad_mode="constant"): frame t is centered at original
        // sample t*HOP_LENGTH, spanning FRAME_LENGTH samples either side of it, with any sample
        // index outside the audio treated as zero. Physical frame count is 1 + size/HOP_LENGTH.
        val half = FRAME_LENGTH / 2
        val numFrames = audio.size / HOP_LENGTH + 1
        val frames = Array(numFrames) { FloatArray(nMels) }

        for (t in 0 until numFrames) {
            val frameStart = t * HOP_LENGTH - half
            for (i in 0 until FRAME_LENGTH) {
                val srcIdx = frameStart + i
                val sample = if (srcIdx in audio.indices) audio[srcIdx] else 0f
                fftRe[i] = (sample * hannWindow[i]).toDouble()
                fftIm[i] = 0.0
            }
            for (i in FRAME_LENGTH until N_FFT) { fftRe[i] = 0.0; fftIm[i] = 0.0 }

            fftPowerInPlace()

            for (m in 0 until nMels) {
                val w = bank.weights[m]
                val s = bank.startBin[m]
                var energy = 0f
                for (k in w.indices) energy += powerSpectrum[s + k] * w[k]
                frames[t][m] = ln(energy.toDouble() + logGuard).toFloat()
            }
        }

        // Per-feature (per-mel-channel) normalization across this utterance's frames — NeMo's
        // AudioToMelSpectrogramPreprocessor default ("normalize: per_feature"). IndicConformer is
        // NeMo-trained, so it expects normalized input; without this the encoder saw
        // out-of-distribution magnitudes and collapsed to the same predicted token regardless of
        // audio content (confirmed on-device: three different-length recordings all decoded to
        // the same single repeated character).
        //
        // NeMo's get_seq_len() reports one fewer "valid" frame than the physical frame count
        // above (floor(size/hop) vs. 1 + floor(size/hop)) — an artifact of its batch-padding
        // math. It normalizes over only the valid frames, then masks the last physical frame to
        // exactly zero. Reproduced here because the golden reference (T29) comes from that exact
        // code path, and unbiased (N-1) variance, per NeMo's normalize_batch.
        val validLen = numFrames - 1
        for (m in 0 until nMels) {
            var mean = 0.0
            for (t in 0 until validLen) mean += frames[t][m]
            mean /= validLen
            var variance = 0.0
            for (t in 0 until validLen) {
                val d = frames[t][m] - mean
                variance += d * d
            }
            val std = kotlin.math.sqrt(variance / (validLen - 1))
            val denom = (std + 1e-5).toFloat()
            for (t in 0 until validLen) frames[t][m] = ((frames[t][m] - mean) / denom).toFloat()
            frames[validLen][m] = 0f
        }

        // Flatten [T, nMels] → [nMels, T] (transpose for model input)
        val result = FloatArray(nMels * numFrames)
        for (t in 0 until numFrames) {
            for (m in 0 until nMels) {
                result[m * numFrames + t] = frames[t][m]
            }
        }
        return result
    }

    // librosa's Slaney mel scale (htk=False): linear below 1kHz, log above. Confirmed against
    // the installed librosa's hz_to_mel/mel_to_hz source (T23) — this is NOT the HTK formula
    // (2595*log10(1+f/700)) this file used before; NeMo calls librosa.filters.mel() without
    // htk=True, so Slaney's scale is what the checkpoint was trained against.
    private val MEL_F_SP = 200.0 / 3.0
    private val MEL_MIN_LOG_HZ = 1000.0
    private val MEL_MIN_LOG_MEL = MEL_MIN_LOG_HZ / MEL_F_SP
    private val MEL_LOG_STEP = Math.log(6.4) / 27.0

    private fun hzToMel(hz: Double): Double =
        if (hz < MEL_MIN_LOG_HZ) hz / MEL_F_SP
        else MEL_MIN_LOG_MEL + Math.log(hz / MEL_MIN_LOG_HZ) / MEL_LOG_STEP

    private fun melToHz(mel: Double): Double =
        if (mel < MEL_MIN_LOG_MEL) MEL_F_SP * mel
        else MEL_MIN_LOG_HZ * Math.exp(MEL_LOG_STEP * (mel - MEL_MIN_LOG_MEL))

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
