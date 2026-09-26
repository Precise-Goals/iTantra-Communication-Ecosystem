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
 * Kannada, Tamil, Telugu, Marathi and Odia had **no** free pre-converted
 * offline TTS source anywhere in that release (checked every vits-* family:
 * piper/coqui/mimic3/mms/icefall/melo, every naming variant). As of T17b they
 * are **self-converted** from `facebook/mms-tts-{kan,tam,tel,mar,ory}` using
 * sherpa-onnx's documented MMS conversion procedure
 * (k2-fsa.github.io/sherpa/onnx/tts/mms.html) and hosted by the team —
 * see `mmsTtsInfo` and `ITANTRA_MODELS_BASE` below. **Licence: CC-BY-NC 4.0**
 * (non-commercial), inherited from `facebook/mms-tts`; the other TTS voices
 * above are more permissively licensed piper/coqui/mimic3 voices. They are
 * included in [com.itantra.domain.model.ModelPack.coreTransceiverPacks] like
 * every other TTS voice, so the app's one "Download the Pack" button covers
 * all ten languages' TTS. The same mirror still has no Odia STT model either
 * — only "as" (Assamese), which is not substituted in as a fake Odia model
 * (see T64).
 */
object ModelRegistry {

    private const val SILERO_VAD_URL =
        "https://raw.githubusercontent.com/snakers4/silero-vad/v6.2.3/src/silero_vad/data/silero_vad.onnx"
    private const val SHERPA_BASE =
        "https://huggingface.co/parismitaglobalsolutions/indicconformer-sherpa-onnx/resolve/main"
    private const val SHERPA_TTS_MODELS_BASE =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models"
    /** Team-hosted exports: the T17b MMS voices, self-converted since no prebuilt source exists
     *  for these languages anywhere in the sherpa-onnx tts-models release (see class doc). */
    private const val ITANTRA_MODELS_BASE =
        "https://huggingface.co/Chgauravpc/itantra/resolve/main"

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
     * (no shared "multilingual" *model* file exists — each language is a separate ONNX graph).
     *
     * The tokenizer is a different story: verified via HuggingFace's real file-listing API
     * (`api.github.com`-style `/api/models/...` — not the resolve/main HTTP-status guesswork
     * this repo's registry used to rely on) that there is **no per-language tokens.txt** except
     * `en/tokens.txt` — every other language shares one root-level `tokens.txt`. The previous
     * version of this registry pointed every language at `$lang/tokens.txt`, which 404s for all
     * 8 non-English languages: the ~188MB model would download fine, then the pack would be
     * marked Failed on the tokens.txt 404, leaving an orphaned .onnx file that `isModelPresent()`
     * correctly refuses to count as "downloaded" — from the outside this looked exactly like
     * "the app can't remember my downloads."
     *
     * Only the "hi", "gu", "kn", "ta" sizes below were confirmed against the actual repo listing;
     * the rest are estimates for progress-bar display only — the real HTTP Content-Length
     * (see ModelDownloadManager.downloadFile) is used for the actual total whenever available.
     * `sha256 = null` deliberately — HuggingFace doesn't publish these ahead of time; the real
     * hash is captured live from the X-Linked-ETag response header at download time instead
     * (a literal placeholder string here would be treated as an *authoritative* expected hash
     * and make every real download fail its own integrity check).
     */
    private fun sttInfo(pack: ModelPack, lang: String, sizeBytes: Long): ModelInfo {
        val tokensUrl = if (lang == "en") "$SHERPA_BASE/en/tokens.txt" else "$SHERPA_BASE/tokens.txt"
        return ModelInfo(
            pack = pack,
            fileName = "stt_${lang}_int8.onnx",
            downloadUrl = "$SHERPA_BASE/$lang/model.int8.onnx",
            sha256 = null,
            sizeBytes = sizeBytes,
            auxFileName = "stt_${lang}_tokens.txt",
            auxUrl = tokensUrl
        )
    }

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

    /**
     * A team-hosted MMS voice (T17b): converted from `facebook/mms-tts-<lang>` with sherpa-onnx's
     * documented MMS conversion (no espeak-ng phonemization — these are character-frontend
     * models), hosted on our own repo because no prebuilt source exists for these languages.
     * Same shape as [sherpaTtsInfo] but pointing at [ITANTRA_MODELS_BASE] instead.
     */
    private fun mmsTtsInfo(
        pack: ModelPack,
        assetName: String,
        lang: String,
        sizeBytes: Long,
        sha256: String
    ): ModelInfo = ModelInfo(
        pack = pack,
        fileName = assetName,
        downloadUrl = "$ITANTRA_MODELS_BASE/$assetName",
        sha256 = sha256,
        sizeBytes = sizeBytes,
        extractDirName = "tts/$lang"
    )

    /**
     * T78 — the shared SraVaani INT8 encoder + decoder_joint pair + SentencePiece vocab (as a flat
     * `<piece> <id>` file), serving all nine Indic languages `hi gu mr kn ml ta te bn or` from one
     * download. Packaged as a single `.tar.bz2` (like [mmsTtsInfo]'s voices) rather than as two
     * separate registry entries, because [ModelInfo]/[ModelDownloadManager] only support one main
     * file + one auxiliary file per pack — a `.tar.bz2` with all three files reuses the existing
     * bundle-extraction path instead of extending that shape for a single pack. Uploaded to
     * [ITANTRA_MODELS_BASE] (same repo as the T17b MMS voices); size and sha256 below are the real,
     * verified values (`docs/evaluation/sravaani/tdt/README.md` — quantized in Colab, sha256
     * confirmed via the HTTP response's `X-Linked-ETag` after upload, not assumed).
     *
     * Extracts to `modelsDir/stt/sravaani/` containing `encoder-sravaani.int8.onnx`,
     * `decoder_joint-sravaani.int8.onnx`, and `sravaani_tokens.txt` — [STTModule]'s
     * `SRAVAANI_ENCODER_FILE`/`SRAVAANI_DECODER_JOINT_FILE`/`SRAVAANI_TOKENS_FILE` constants point
     * at these exact paths.
     *
     * Wired into [registry] below for [ModelPack.STT_SRAVAANI] (S5a). Takes `pack` as a
     * parameter (matching [sttInfo]/[sherpaTtsInfo]/[mmsTtsInfo]'s shape).
     */
    private fun sravaaniTdtInfo(pack: ModelPack): ModelInfo = ModelInfo(
        pack = pack,
        fileName = "sravaani_tdt.tar.bz2",
        downloadUrl = "$ITANTRA_MODELS_BASE/sravaani_tdt.tar.bz2",
        sha256 = "287816154cd5c966e04b9588ad966b2d05246832418e79881efbc62f87371471",
        sizeBytes = 401_576_328L,
        extractDirName = "stt/sravaani"
    )

    val registry: Map<ModelPack, ModelInfo> = mapOf(
        ModelPack.VAD_MODEL to ModelInfo(
            pack = ModelPack.VAD_MODEL,
            fileName = "silero_vad_v4.onnx",
            downloadUrl = SILERO_VAD_URL,
            sha256 = "1a153a22f4509e292a94e67d6f9b85e8deb25b4988682b7e174c65279d8788e3", // pinned to v6.2.3 (T62)
            sizeBytes = 2_327_524L // 2.22 MB
        ),

        // STT models: English on IndicConformer, 9 Indic languages on shared SraVaani INT8 TDT (T78 Step 7)
        ModelPack.STT_ENGLISH to sttInfo(ModelPack.STT_ENGLISH, "en", 197_595_500L),
        ModelPack.STT_SRAVAANI to sravaaniTdtInfo(ModelPack.STT_SRAVAANI),

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
            ModelPack.TTS_HINDI, "vits-piper-hi_IN-pratham-medium-int8.tar.bz2", "hi",
            sizeBytes = 20_987_965L,
            sha256 = "20f568c56207c13b9a0d9478aec8b7d1449122e618aeebc7211f6abc942b58b7"
        ),
        ModelPack.TTS_MALAYALAM to sherpaTtsInfo(
            ModelPack.TTS_MALAYALAM, "vits-piper-ml_IN-arjun-medium-int8.tar.bz2", "ml",
            sizeBytes = 20_838_242L,
            sha256 = "4d0b2a58157604b589cddc54884ffd2618b097d15155b254c34bd21830659832"
        ),
        ModelPack.TTS_ENGLISH to sherpaTtsInfo(
            ModelPack.TTS_ENGLISH, "vits-piper-en_US-lessac-low-int8.tar.bz2", "en",
            sizeBytes = 21_070_568L,
            sha256 = "af63fbe60d8bdcfccdee61ba057304a11dfc077145da383d4d351ec3c594d5e2"
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

        // T17b: no prebuilt source exists for these five in the sherpa-onnx tts-models release
        // (see class doc), so they are self-converted from facebook/mms-tts and team-hosted.
        // Licence: CC-BY-NC 4.0 (non-commercial), inherited from facebook/mms-tts.
        ModelPack.TTS_KANNADA to mmsTtsInfo(
            ModelPack.TTS_KANNADA, "vits-mms-kan.tar.bz2", "kn",
            sizeBytes = 107_749_707L,
            sha256 = "db403e47d3a683193cf2a7328c0ed97441dfbed20483e8288de9fe12d5e5de33"
        ),
        ModelPack.TTS_TAMIL to mmsTtsInfo(
            ModelPack.TTS_TAMIL, "vits-mms-tam.tar.bz2", "ta",
            sizeBytes = 107_732_553L,
            sha256 = "1e027bf470004f42f5eb9d56148bd3d16cb6f0e520f5ac4d919e745e9675461c"
        ),
        ModelPack.TTS_TELUGU to mmsTtsInfo(
            ModelPack.TTS_TELUGU, "vits-mms-tel.tar.bz2", "te",
            sizeBytes = 107_766_292L,
            sha256 = "aa2f19cfb3b1609b09a7c8bd9e47f94dbfd2d7db6fb0c4d271338e1d3e6e226e"
        ),
        ModelPack.TTS_MARATHI to mmsTtsInfo(
            ModelPack.TTS_MARATHI, "vits-mms-mar.tar.bz2", "mr",
            sizeBytes = 107_755_725L,
            sha256 = "efb33aac21897d36bb46b7e721a9b9f479487a339e138bc399a3dccfb436899b"
        ),
        ModelPack.TTS_ODIA to mmsTtsInfo(
            ModelPack.TTS_ODIA, "vits-mms-ory.tar.bz2", "or",
            sizeBytes = 107_748_147L,
            sha256 = "1cbe41e41364a1fc07fadbe0057a523d45b42300bf65ad23b95b252c2c7279c1"
        )
    )

    fun getInfo(pack: ModelPack): ModelInfo? = registry[pack]

    /** True for a TTS pack with no source at all (downloadUrl left blank). As of T17b, every
     *  language has a source (sherpa-onnx tts-models or the team-hosted MMS exports below), so
     *  this is currently always false — kept as a guard in case a future language is added
     *  without one. */
    fun isUnsupportedTts(pack: ModelPack): Boolean = registry[pack]?.downloadUrl?.isBlank() == true

    fun totalSizeBytes(packs: List<ModelPack>): Long =
        packs.sumOf { registry[it]?.sizeBytes ?: 0L }
}
