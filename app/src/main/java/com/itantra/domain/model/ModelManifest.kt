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
    TTS_HINDI(
        "Hindi Voice Pack",
        "AI4Bharat IndicTTS VITS — Hindi natural voice",
        sizeMb = 15,
        isRequired = false,
        requiredFor = "Transceiver"
    ),
    TTS_GUJARATI(
        "Gujarati Voice Pack",
        "AI4Bharat IndicTTS VITS — Gujarati natural voice",
        sizeMb = 14,
        isRequired = false,
        requiredFor = "Transceiver"
    ),
    TTS_MARATHI(
        "Marathi Voice Pack",
        "AI4Bharat IndicTTS VITS — Marathi natural voice",
        sizeMb = 14,
        isRequired = false,
        requiredFor = "Transceiver"
    ),
    TTS_KANNADA(
        "Kannada Voice Pack",
        "AI4Bharat IndicTTS VITS — Kannada natural voice",
        sizeMb = 14,
        isRequired = false,
        requiredFor = "Transceiver"
    ),
    TTS_MALAYALAM(
        "Malayalam Voice Pack",
        "AI4Bharat IndicTTS VITS — Malayalam natural voice",
        sizeMb = 14,
        isRequired = false,
        requiredFor = "Transceiver"
    ),
    TTS_TAMIL(
        "Tamil Voice Pack",
        "AI4Bharat IndicTTS VITS — Tamil natural voice",
        sizeMb = 15,
        isRequired = false,
        requiredFor = "Transceiver"
    ),
    TTS_TELUGU(
        "Telugu Voice Pack",
        "AI4Bharat IndicTTS VITS — Telugu natural voice",
        sizeMb = 15,
        isRequired = false,
        requiredFor = "Transceiver"
    ),
    TTS_ODIA(
        "Odia Voice Pack",
        "AI4Bharat IndicTTS VITS — Odia natural voice",
        sizeMb = 13,
        isRequired = false,
        requiredFor = "Transceiver"
    ),
    TTS_BENGALI(
        "Bengali Voice Pack",
        "AI4Bharat IndicTTS VITS — Bengali natural voice",
        sizeMb = 14,
        isRequired = false,
        requiredFor = "Transceiver"
    ),
    TTS_ENGLISH(
        "English Voice Pack",
        "Piper TTS — English natural voice",
        sizeMb = 12,
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
        /** Returns compulsory packs needed for full multilingual Transceiver & auto-LID */
        fun coreTransceiverPacks(): List<ModelPack> = listOf(
            VAD_MODEL,
            STT_INDIC_CONFORMER,
            LANG_DETECTION,
            TTS_HINDI,
            TTS_GUJARATI,
            TTS_MARATHI,
            TTS_KANNADA,
            TTS_MALAYALAM,
            TTS_TAMIL,
            TTS_TELUGU,
            TTS_ODIA,
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
