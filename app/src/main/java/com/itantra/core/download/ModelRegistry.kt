package com.itantra.core.download

import com.itantra.domain.model.ModelPack

/**
 * Central registry of all downloadable model packs for iTantra.
 *
 * STT entries are sourced from AI4Bharat's IndicConformer (sherpa-onnx export) — one ONNX
 * graph + tokens.txt vocab per language, no shared "multilingual" file exists.
 *
 * TTS entries are sourced from sherpa-onnx's `tts-models` GitHub Release
 * (github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models), which bundles real
 * espeak-ng-phonemized VITS voices (Piper/Coqui/Mimic3) as ready-to-use
 * model.onnx + tokens.txt pairs — verified by querying the release's asset
 * list directly (not guessed from documentation). Every `.tar.bz2` entry's
 * `sizeBytes` and, where GitHub publishes one, `sha256` were read from that
 * asset list's real `size`/`digest` fields.
 *
 * Kannada, Tamil, Telugu, Marathi and Odia have **no** free pre-converted
 * offline TTS source anywhere in that release (checked every vits-* family:
 * piper/coqui/mimic3/mms/icefall/melo, every naming variant) — see the
 * `ModelPack` entries for those languages, which are intentionally absent
 * from [com.itantra.domain.model.ModelPack.coreTransceiverPacks]. The same
 * source has no Odia STT model either — only "as" (Assamese), which is not
 * substituted in as a fake Odia model.
 */
object ModelRegistry {

    private const val SILERO_VAD_URL =
        "https://raw.githubusercontent.com/snakers4/silero-vad/master/src/silero_vad/data/silero_vad.onnx"
    private const val FASTTEXT_LID_URL =
        "https://dl.fbaipublicfiles.com/fasttext/supervised-models/lid.176.ftz"
    private const val SHERPA_BASE =
        "https://huggingface.co/parismitaglobalsolutions/indicconformer-sherpa-onnx/resolve/main"
    private const val SHERPA_TTS_MODELS_BASE =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models"
    private const val PHI3_URL =
        "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf/resolve/main/Phi-3-mini-4k-instruct-q4.gguf"

    data class ModelInfo(
        val pack: ModelPack,
        val fileName: String,
        val downloadUrl: String,
        /** Real SHA-256 when known upfront (verified against GitHub's published asset digest or
         * HuggingFace's LFS ETag); null when no authoritative hash is available before download —
         * those packs fall back to trust-on-first-download (see ModelHashStore). */
        val sha256: String?,
        val sizeBytes: Long,
        val version: String = "2.0.0",
        /** Optional companion file (e.g. a tokenizer vocab) required alongside the main model. */
        val auxFileName: String? = null,
        val auxUrl: String? = null,
        /** When set, [fileName] is a `.tar.bz2` archive extracted into `modelsDir/[extractDirName]/`
         * and then deleted — used for the sherpa-onnx TTS voice bundles and the shared espeak-ng-data. */
        val extractDirName: String? = null
    )

    /**
     * sherpa-onnx per-language IndicConformer STT model + its tokens.txt vocab
     * (no shared "multilingual" file exists — each language is a separate ONNX graph).
     * Only the "hi", "gu", "kn", "ta" sizes below were confirmed against the actual repo listing;
     * the rest are estimates for progress-bar display only — the real HTTP Content-Length
     * (see ModelDownloadManager.downloadFile) is used for the actual total whenever available.
     * `sha256 = null` deliberately — HuggingFace doesn't publish these ahead of time; the real
     * hash is captured live from the X-Linked-ETag response header at download time instead
     * (a literal placeholder string here would be treated as an *authoritative* expected hash
     * and make every real download fail its own integrity check).
     */
    private fun sttInfo(pack: ModelPack, lang: String, sizeBytes: Long): ModelInfo = ModelInfo(
        pack = pack,
        fileName = "stt_${lang}_int8.onnx",
        downloadUrl = "$SHERPA_BASE/$lang/model.int8.onnx",
        sha256 = null,
        sizeBytes = sizeBytes,
        auxFileName = "stt_${lang}_tokens.txt",
        auxUrl = "$SHERPA_BASE/$lang/tokens.txt"
    )

    /** A sherpa-onnx `tts-models` release TTS voice bundle: real espeak-ng phonemization included. */
    private fun sherpaTtsInfo(
        pack: ModelPack,
        assetName: String,
        lang: String,
        sizeBytes: Long,
        sha256: String? = null
    ): ModelInfo = ModelInfo(
        pack = pack,
        fileName = assetName,
        downloadUrl = "$SHERPA_TTS_MODELS_BASE/$assetName",
        sha256 = sha256,
        sizeBytes = sizeBytes,
        extractDirName = "tts/$lang"
    )

    val registry: Map<ModelPack, ModelInfo> = mapOf(
        ModelPack.VAD_MODEL to ModelInfo(
            pack = ModelPack.VAD_MODEL,
            fileName = "silero_vad_v4.onnx",
            downloadUrl = SILERO_VAD_URL,
            sha256 = null, // GitHub raw content, no LFS digest header — trust-on-first-download
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
            sha256 = null, // fbaipublicfiles, no LFS digest — trust-on-first-download
            sizeBytes = 938_013L // 0.89 MB
        ),

        // Shared by every TTS voice below — real espeak-ng phoneme/language data.
        ModelPack.ESPEAK_NG_DATA to ModelInfo(
            pack = ModelPack.ESPEAK_NG_DATA,
            fileName = "espeak-ng-data.tar.bz2",
            downloadUrl = "$SHERPA_TTS_MODELS_BASE/espeak-ng-data.tar.bz2",
            sha256 = null, // not published by GitHub for this asset — trust-on-first-download
            sizeBytes = 7_252_012L,
            extractDirName = "espeak-ng-data"
        ),

        // Real, verified voices (sherpa-onnx tts-models release, checked 2026-09-02):
        ModelPack.TTS_HINDI to sherpaTtsInfo(
            ModelPack.TTS_HINDI, "vits-piper-hi_IN-pratham-medium.tar.bz2", "hi",
            sizeBytes = 67_238_438L,
            sha256 = "2084d321e1d2752f2b64ed3012ba27751df01a80da46f52920098cdcb7e35648"
        ),
        ModelPack.TTS_MALAYALAM to sherpaTtsInfo(
            ModelPack.TTS_MALAYALAM, "vits-piper-ml_IN-arjun-medium.tar.bz2", "ml",
            sizeBytes = 67_222_458L,
            sha256 = "3058d098e8b1ffcdd6069e96b1d492f319333235912a627c309c7c54cea59acf"
        ),
        ModelPack.TTS_ENGLISH to sherpaTtsInfo(
            ModelPack.TTS_ENGLISH, "vits-piper-en_US-lessac-low.tar.bz2", "en",
            sizeBytes = 67_097_098L,
            sha256 = "8fb427b8637334072ee5723d72fa418c45bfdd4b7deebeacdf2938662618c1cb"
        ),
        ModelPack.TTS_GUJARATI to sherpaTtsInfo(
            // Only known source: Mimic3/CMU-Indic — lower "low" quality tier, no higher tier exists.
            ModelPack.TTS_GUJARATI, "vits-mimic3-gu_IN-cmu-indic_low.tar.bz2", "gu",
            sizeBytes = 79_992_004L,
            sha256 = null // GitHub hasn't published a digest for this asset
        ),
        ModelPack.TTS_BENGALI to sherpaTtsInfo(
            ModelPack.TTS_BENGALI, "vits-coqui-bn-custom_female.tar.bz2", "bn",
            sizeBytes = 108_053_596L,
            sha256 = null // GitHub hasn't published a digest for this asset
        ),

        // No known free offline TTS source exists for these — see class doc. Not downloadable;
        // entries kept only so the enum/UI don't dangle. downloadUrl intentionally left blank.
        ModelPack.TTS_KANNADA to ModelInfo(
            ModelPack.TTS_KANNADA, fileName = "", downloadUrl = "", sha256 = null, sizeBytes = 0L
        ),
        ModelPack.TTS_TAMIL to ModelInfo(
            ModelPack.TTS_TAMIL, fileName = "", downloadUrl = "", sha256 = null, sizeBytes = 0L
        ),
        ModelPack.TTS_TELUGU to ModelInfo(
            ModelPack.TTS_TELUGU, fileName = "", downloadUrl = "", sha256 = null, sizeBytes = 0L
        ),
        ModelPack.TTS_MARATHI to ModelInfo(
            ModelPack.TTS_MARATHI, fileName = "", downloadUrl = "", sha256 = null, sizeBytes = 0L
        ),
        ModelPack.TTS_ODIA to ModelInfo(
            ModelPack.TTS_ODIA, fileName = "", downloadUrl = "", sha256 = null, sizeBytes = 0L
        ),

        ModelPack.AI_ASSISTANT to ModelInfo(
            pack = ModelPack.AI_ASSISTANT,
            fileName = "phi3_mini_q4.gguf",
            downloadUrl = PHI3_URL,
            sha256 = null, // captured from HF's X-Linked-ETag header at download time instead
            sizeBytes = 2_390_000_000L
        )
    )

    fun getInfo(pack: ModelPack): ModelInfo? = registry[pack]

    /** True for the 5 languages with no known free TTS source (see class doc). */
    fun isUnsupportedTts(pack: ModelPack): Boolean = registry[pack]?.downloadUrl?.isBlank() == true

    fun totalSizeBytes(packs: List<ModelPack>): Long =
        packs.sumOf { registry[it]?.sizeBytes ?: 0L }
}
