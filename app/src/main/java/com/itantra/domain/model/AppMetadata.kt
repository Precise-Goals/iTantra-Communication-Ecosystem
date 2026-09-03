package com.itantra.domain.model

/**
 * Static and runtime metadata for the iTantra application.
 * Satisfies SIH 2026 Problem Statement 26173 requirements.
 */
object AppMetadata {
    const val APP_NAME = "iTantra"
    const val VERSION_NAME = "2.0.0"
    const val VERSION_CODE = 2
    const val PROBLEM_STATEMENT_ID = "26173"
    const val PROBLEM_STATEMENT_TITLE =
        "iTantra - Indian Multilingual TTS & STT Aided Neural Transceiver Radio Access for low bitrate links"
    const val EVENT = "Smart India Hackathon 2026 (SIH 2026)"
    const val THEME = "Disaster Management, Low Bitrate Links & Inclusive Communication"

    // Hardware specifications
    const val MIN_RAM_TARGET = "4GB+ RAM (Optimized for Low & Mid-range Android smartphones)"
    const val TARGET_ARCHITECTURES = "arm64-v8a, armeabi-v7a, x86_64"
    const val MIN_ANDROID_SDK = "Android 8.0 (API 26)"
    const val TARGET_ANDROID_SDK = "Android 15 (API 35)"

    // Network & Transport
    const val PRIMARY_TRANSPORT = "Wi-Fi Direct P2P (High-Throughput Mesh)"
    const val FALLBACK_TRANSPORT = "Bluetooth RFCOMM / SPP (Ultra-Low-Power)"
    const val PACKET_STREAMING = "Low-bitrate Protobuf Lite + Chunked Streaming (8-16 kbps)"

    // Neural Models
    const val STT_MODEL = "AI4Bharat IndicConformer (INT8 ONNX, ~150MB)"
    const val TTS_MODEL = "AI4Bharat IndicTTS VITS (INT8 ONNX, ~14MB per language)"
    const val VAD_MODEL = "Silero VAD v4 (ONNX, ~2MB)"
    const val LID_MODEL = "FastText Language ID (lid.176.ftz, ~900KB)"
    const val AI_MODEL = "Phi-3 Mini 3.8B Q4 Quantized (Offline GGUF / ONNX, ~2.2GB)"

    // Supported Indic Languages
    val SUPPORTED_LANGUAGES = listOf(
        LanguageInfo("hi", "Hindi", "हिन्दी"),
        LanguageInfo("mr", "Marathi", "मराठी"),
        LanguageInfo("gu", "Gujarati", "ગુજરાતી"),
        LanguageInfo("kn", "Kannada", "ಕನ್ನಡ"),
        LanguageInfo("ml", "Malayalam", "മലയാളം"),
        LanguageInfo("ta", "Tamil", "தமிழ்"),
        LanguageInfo("te", "Telugu", "తెలుగు"),
        LanguageInfo("or", "Odia", "ଓଡ଼ିଆ"),
        LanguageInfo("bn", "Bengali", "বাংলা"),
        LanguageInfo("en", "English", "English")
    )
}

data class LanguageInfo(
    val code: String,
    val englishName: String,
    val nativeName: String
)
