package com.itantra.domain.model

import kotlinx.serialization.Serializable

/**
 * Defines the core downloadable neural model engines for iTantra.
 * Minimalized to the 4 essential on-device engines required for full multilingual operation.
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
        displayName = "Voice Activity Detector (VAD)",
        description = "Silero VAD v4 — ultra-low power pause and stoppage detection (~2MB)",
        sizeMb = 2,
        isRequired = true,
        requiredFor = "Transceiver"
    ),
    STT_INDIC_CONFORMER(
        displayName = "Speech-to-Text Engine (STT)",
        description = "AI4Bharat IndicConformer ONNX (meetsync/indic-conformer-onnx-sherpa) — 10 Indic languages + English",
        sizeMb = 188,
        isRequired = true,
        requiredFor = "Transceiver"
    ),
    TTS_INDIC_MODEL(
        displayName = "Text-to-Speech Engine (TTS)",
        description = "AI4Bharat Indic-Parler-TTS / IndicTTS — Multilingual natural acoustic voice engine",
        sizeMb = 60,
        isRequired = true,
        requiredFor = "Transceiver"
    ),
    AI_ASSISTANT(
        displayName = "AI Assistant (Qwen2.5-0.5B)",
        description = "Qwen2.5-0.5B-Instruct ONNX (INT4) — 100% offline multilingual generative intelligence for 22+ languages",
        sizeMb = 350,
        isRequired = false,
        requiredFor = "AI Assistant"
    );

    companion object {
        /** Returns compulsory packs needed for core Transceiver */
        fun coreTransceiverPacks(): List<ModelPack> = listOf(
            VAD_MODEL,
            STT_INDIC_CONFORMER,
            TTS_INDIC_MODEL
        )

        /** All essential packs for full system capability */
        fun allEssentialPacks(): List<ModelPack> = listOf(
            VAD_MODEL,
            STT_INDIC_CONFORMER,
            TTS_INDIC_MODEL,
            AI_ASSISTANT
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
