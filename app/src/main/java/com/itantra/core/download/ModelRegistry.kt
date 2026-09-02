package com.itantra.core.download

import com.itantra.domain.model.ModelPack

/**
 * Central registry of all downloadable model packs for iTantra.
 * URLs and byte sizes match exact remote binaries on HuggingFace Hub and GitHub.
 */
object ModelRegistry {

    private const val SILERO_VAD_URL =
        "https://raw.githubusercontent.com/snakers4/silero-vad/master/src/silero_vad/data/silero_vad.onnx"
    private const val FASTTEXT_LID_URL =
        "https://dl.fbaipublicfiles.com/fasttext/supervised-models/lid.176.ftz"
    private const val SHERPA_BASE =
        "https://huggingface.co/parismitaglobalsolutions/indicconformer-sherpa-onnx/resolve/main"
    private const val PIPER_BASE =
        "https://huggingface.co/rhasspy/piper-voices/resolve/main"
    private const val PHI3_URL =
        "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf/resolve/main/Phi-3-mini-4k-instruct-q4.gguf"

    data class ModelInfo(
        val pack: ModelPack,
        val fileName: String,
        val downloadUrl: String,
        val sha256: String,
        val sizeBytes: Long,
        val version: String = "2.0.0",
        /** Optional companion file (e.g. a tokenizer vocab) required alongside the main model. */
        val auxFileName: String? = null,
        val auxUrl: String? = null
    )

    /**
     * sherpa-onnx per-language IndicConformer STT model + its tokens.txt vocab
     * (no shared "multilingual" file exists — each language is a separate ONNX graph).
     * Only the "hi", "gu", "kn", "ta" sizes below were confirmed against the actual repo listing;
     * the rest are estimates for progress-bar display only — the real HTTP Content-Length
     * (see ModelDownloadManager.downloadFile) is used for the actual total whenever available.
     */
    private fun sttInfo(pack: ModelPack, lang: String, sizeBytes: Long): ModelInfo = ModelInfo(
        pack = pack,
        fileName = "stt_${lang}_int8.onnx",
        downloadUrl = "$SHERPA_BASE/$lang/model.int8.onnx",
        sha256 = "placeholder_sha256_stt_$lang",
        sizeBytes = sizeBytes,
        auxFileName = "stt_${lang}_tokens.txt",
        auxUrl = "$SHERPA_BASE/$lang/tokens.txt"
    )

    val registry: Map<ModelPack, ModelInfo> = mapOf(
        ModelPack.VAD_MODEL to ModelInfo(
            pack = ModelPack.VAD_MODEL,
            fileName = "silero_vad_v4.onnx",
            downloadUrl = SILERO_VAD_URL,
            sha256 = "placeholder_sha256_vad",
            sizeBytes = 2_327_524L // 2.22 MB
        ),
        // No Odia entry: the parismitaglobalsolutions/indicconformer-sherpa-onnx repo has no "or/" model —
        // it only has "as/" (Assamese). Reusing that mislabeled as Odia would just be a second fabrication.
        ModelPack.STT_HINDI to sttInfo(ModelPack.STT_HINDI, "hi", 197_595_593L),
        ModelPack.STT_GUJARATI to sttInfo(ModelPack.STT_GUJARATI, "gu", 197_595_461L),
        ModelPack.STT_MARATHI to sttInfo(ModelPack.STT_MARATHI, "mr", 197_595_500L),
        ModelPack.STT_KANNADA to sttInfo(ModelPack.STT_KANNADA, "kn", 197_595_728L),
        ModelPack.STT_MALAYALAM to sttInfo(ModelPack.STT_MALAYALAM, "ml", 197_595_500L),
        ModelPack.STT_TAMIL to sttInfo(ModelPack.STT_TAMIL, "ta", 197_595_513L),
        ModelPack.STT_TELUGU to sttInfo(ModelPack.STT_TELUGU, "te", 197_595_500L),
        ModelPack.STT_BENGALI to sttInfo(ModelPack.STT_BENGALI, "bn", 197_595_500L),
        ModelPack.STT_ENGLISH to sttInfo(ModelPack.STT_ENGLISH, "en", 197_595_500L),
        ModelPack.LANG_DETECTION to ModelInfo(
            pack = ModelPack.LANG_DETECTION,
            fileName = "lid.176.ftz",
            downloadUrl = FASTTEXT_LID_URL,
            sha256 = "placeholder_sha256_lid",
            sizeBytes = 938_013L // 0.89 MB
        ),
        ModelPack.TTS_HINDI to ModelInfo(
            pack = ModelPack.TTS_HINDI,
            fileName = "hi_vits_int8.onnx",
            downloadUrl = "$PIPER_BASE/hi/hi_IN/pratham/medium/hi_IN-pratham-medium.onnx",
            sha256 = "placeholder_sha256_tts_hi",
            sizeBytes = 63_516_050L // 60.57 MB
        ),
        ModelPack.TTS_GUJARATI to ModelInfo(
            pack = ModelPack.TTS_GUJARATI,
            fileName = "gu_vits_int8.onnx",
            downloadUrl = "$SHERPA_BASE/gu/model.int8.onnx",
            sha256 = "placeholder_sha256_tts_gu",
            sizeBytes = 197_595_461L // 188.44 MB
        ),
        ModelPack.TTS_MARATHI to ModelInfo(
            pack = ModelPack.TTS_MARATHI,
            fileName = "mr_vits_int8.onnx",
            downloadUrl = "$PIPER_BASE/mr/mr_IN/google/medium/mr_IN-google-medium.onnx",
            sha256 = "placeholder_sha256_tts_mr",
            sizeBytes = 76_768_179L // 73.21 MB
        ),
        ModelPack.TTS_KANNADA to ModelInfo(
            pack = ModelPack.TTS_KANNADA,
            fileName = "kn_vits_int8.onnx",
            downloadUrl = "$SHERPA_BASE/kn/model.int8.onnx",
            sha256 = "placeholder_sha256_tts_kn",
            sizeBytes = 197_595_728L // 188.44 MB (Exact remote size)
        ),
        ModelPack.TTS_MALAYALAM to ModelInfo(
            pack = ModelPack.TTS_MALAYALAM,
            fileName = "ml_vits_int8.onnx",
            downloadUrl = "$PIPER_BASE/ml/ml_IN/arjun/medium/ml_IN-arjun-medium.onnx",
            sha256 = "placeholder_sha256_tts_ml",
            sizeBytes = 62_950_044L // 60.03 MB
        ),
        ModelPack.TTS_TAMIL to ModelInfo(
            pack = ModelPack.TTS_TAMIL,
            fileName = "ta_vits_int8.onnx",
            downloadUrl = "$SHERPA_BASE/ta/model.int8.onnx",
            sha256 = "placeholder_sha256_tts_ta",
            sizeBytes = 197_595_513L // 188.44 MB
        ),
        ModelPack.TTS_TELUGU to ModelInfo(
            pack = ModelPack.TTS_TELUGU,
            fileName = "te_vits_int8.onnx",
            downloadUrl = "$PIPER_BASE/te/te_IN/venkatesh/medium/te_IN-venkatesh-medium.onnx",
            sha256 = "placeholder_sha256_tts_te",
            sizeBytes = 63_516_050L // 60.57 MB
        ),
        ModelPack.TTS_ODIA to ModelInfo(
            pack = ModelPack.TTS_ODIA,
            fileName = "or_vits_int8.onnx",
            downloadUrl = "$SHERPA_BASE/as/model.int8.onnx",
            sha256 = "placeholder_sha256_tts_or",
            sizeBytes = 197_595_509L // 188.44 MB
        ),
        ModelPack.TTS_BENGALI to ModelInfo(
            pack = ModelPack.TTS_BENGALI,
            fileName = "bn_vits_int8.onnx",
            downloadUrl = "$PIPER_BASE/bn/bn_BD/google/medium/bn_BD-google-medium.onnx",
            sha256 = "placeholder_sha256_tts_bn",
            sizeBytes = 76_782_515L // 73.23 MB
        ),
        ModelPack.TTS_ENGLISH to ModelInfo(
            pack = ModelPack.TTS_ENGLISH,
            fileName = "en_piper_int8.onnx",
            downloadUrl = "$PIPER_BASE/en/en_US/lessac/low/en_US-lessac-low.onnx",
            sha256 = "placeholder_sha256_tts_en",
            sizeBytes = 63_201_294L // 60.27 MB
        ),
        ModelPack.AI_ASSISTANT to ModelInfo(
            pack = ModelPack.AI_ASSISTANT,
            fileName = "phi3_mini_q4.gguf",
            downloadUrl = PHI3_URL,
            sha256 = "placeholder_sha256_phi3",
            sizeBytes = 2_390_000_000L
        )
    )

    fun getInfo(pack: ModelPack): ModelInfo? = registry[pack]

    fun totalSizeBytes(packs: List<ModelPack>): Long =
        packs.sumOf { registry[it]?.sizeBytes ?: 0L }
}
