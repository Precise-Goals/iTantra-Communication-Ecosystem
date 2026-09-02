package com.itantra.domain.model

import kotlinx.serialization.Serializable

/**
 * Defines all downloadable model packs for iTantra.
 * Each pack is independently downloadable and tracked by DownloadState.
 */
enum class ModelPack(
    val displayName: String,
    val description: String,
    /** Approximate download size in MB */
    val sizeMb: Int,
    /** Whether this pack is required for core Transceiver feature */
    val isRequired: Boolean,
    /** Feature gate: which screen requires this pack */
    val requiredFor: String
) {
    VAD_MODEL(
        "Voice Activity Detector",
        "Silero VAD — detects speech pauses in real-time",
        sizeMb = 2,
        isRequired = true,
        requiredFor = "Transceiver"
    ),
    STT_INDIC_CONFORMER(
        "Speech-to-Text Engine",
        "AI4Bharat IndicConformer — all 10 Indian languages",
        sizeMb = 150,
        isRequired = true,
        requiredFor = "Transceiver"
    ),
    LANG_DETECTION(
        "Language Auto-Detector",
        "FastText LID — identifies spoken language automatically",
        sizeMb = 1,
        isRequired = false,
        requiredFor = "Transceiver"
    ),
    ESPEAK_NG_DATA(
        "Speech Phonemizer Data",
        "Shared espeak-ng phoneme data — required by every voice pack below for real speech synthesis",
        sizeMb = 7,
        isRequired = true,
        requiredFor = "Transceiver"
    ),
    TTS_HINDI(
        "Hindi Voice Pack",
        "sherpa-onnx (Piper, real espeak-ng phonemization) — Hindi natural voice",
        sizeMb = 64,
        isRequired = false,
        requiredFor = "Transceiver"
    ),
    TTS_GUJARATI(
        "Gujarati Voice Pack",
        "sherpa-onnx (Mimic3/CMU-Indic, real phonemization) — Gujarati voice, lower quality tier (only source found)",
        sizeMb = 76,
        isRequired = false,
        requiredFor = "Transceiver"
    ),
    TTS_MARATHI(
        "Marathi Voice Pack",
        "Unsupported — no free offline TTS source found (checked Piper/Coqui/Mimic3/MMS)",
        sizeMb = 0,
        isRequired = false,
        requiredFor = "Transceiver"
    ),
    TTS_KANNADA(
        "Kannada Voice Pack",
        "Unsupported — no free offline TTS source found (checked Piper/Coqui/Mimic3/MMS)",
        sizeMb = 0,
        isRequired = false,
        requiredFor = "Transceiver"
    ),
    TTS_MALAYALAM(
        "Malayalam Voice Pack",
        "sherpa-onnx (Piper, real espeak-ng phonemization) — Malayalam natural voice",
        sizeMb = 64,
        isRequired = false,
        requiredFor = "Transceiver"
    ),
    TTS_TAMIL(
        "Tamil Voice Pack",
        "Unsupported — no free offline TTS source found (checked Piper/Coqui/Mimic3/MMS)",
        sizeMb = 0,
        isRequired = false,
        requiredFor = "Transceiver"
    ),
    TTS_TELUGU(
        "Telugu Voice Pack",
        "Unsupported — no free offline TTS source found (checked Piper/Coqui/Mimic3/MMS)",
        sizeMb = 0,
        isRequired = false,
        requiredFor = "Transceiver"
    ),
    TTS_ODIA(
        "Odia Voice Pack",
        "Unsupported — no free offline TTS source found (checked Piper/Coqui/Mimic3/MMS)",
        sizeMb = 0,
        isRequired = false,
        requiredFor = "Transceiver"
    ),
    TTS_BENGALI(
        "Bengali Voice Pack",
        "sherpa-onnx (Coqui, real phonemization) — Bengali natural voice",
        sizeMb = 103,
        isRequired = false,
        requiredFor = "Transceiver"
    ),
    TTS_ENGLISH(
        "English Voice Pack",
        "sherpa-onnx (Piper, real espeak-ng phonemization) — English natural voice",
        sizeMb = 64,
        isRequired = false,
        requiredFor = "Transceiver"
    ),
    AI_ASSISTANT(
        "AI Assistant (Phi-3 Mini)",
        "Phi-3 Mini Q4 — offline AI assistant, India-aware (MIT License)",
        sizeMb = 2200,
        isRequired = false,
        requiredFor = "AI Assistant"
    );

    companion object {
        /**
         * Returns compulsory packs needed for full multilingual Transceiver & auto-LID.
         *
         * Only the languages with a real, verified TTS source are included — Kannada, Tamil,
         * Telugu, Marathi and Odia are deliberately absent: no free offline TTS source exists
         * for them (see [com.itantra.core.download.ModelRegistry]'s class doc). Their `ModelPack`
         * entries stay in the enum (so nothing else dangles) but aren't offered as downloadable.
         */
        fun coreTransceiverPacks(): List<ModelPack> = listOf(
            VAD_MODEL,
            STT_INDIC_CONFORMER,
            LANG_DETECTION,
            ESPEAK_NG_DATA,
            TTS_HINDI,
            TTS_GUJARATI,
            TTS_MALAYALAM,
            TTS_BENGALI,
            TTS_ENGLISH
        )
    }
}

/**
 * Tracks the download state of a model pack.
 */
sealed class DownloadState {
    /** Pack is available for download but not yet initiated */
    data object NotDownloaded : DownloadState()
    /** Active download in progress */
    data class Downloading(
        val progressPercent: Float,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : DownloadState()
    /** Pack fully downloaded and verified */
    data object Downloaded : DownloadState()
    /** Download failed with reason */
    data class Failed(val reason: String) : DownloadState()
    /** Download queued but not yet started */
    data object Queued : DownloadState()
}

@Serializable
data class ModelEntry(
    val pack: String,
    val fileName: String,
    val downloadUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val version: String
)
