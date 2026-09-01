package com.itantra.domain.model

/**
 * Domain-layer representation of a message transmitted or received via the iTantra network.
 * Wraps the Protobuf-generated TransceiverProto.TransceiverMessage with UI-specific fields.
 *
 * Note: [direction] is a UI-only field and is NOT included in the Protobuf wire format.
 */
data class TransceiverMessage(
    val type: MessageType,
    val text: String,
    /** Source language in BCP-47 format (e.g., "hi", "ta", "en") */
    val srcLang: String,
    /** Destination TTS language in BCP-47 format */
    val dstLang: String,
    val senderId: String,
    /** Unix epoch milliseconds when STT inference completed on sender */
    val timestamp: Long = System.currentTimeMillis(),
    /** STT model confidence score [0.0–1.0]; 0f for non-SPEECH types */
    val confidence: Float = 0f,
    val sequence: Int = 0,
    /** UI-only: whether this message was sent by this device or received from a peer */
    val direction: Direction = Direction.RECEIVED
)

enum class MessageType {
    SPEECH,
    ALERT,
    ACK,
    PING
}

enum class Direction {
    SENT,
    RECEIVED
}

/**
 * Supported Indic languages with their BCP-47 codes and display names.
 */
enum class IndicLanguage(val code: String, val displayName: String, val nativeName: String) {
    HINDI("hi", "Hindi", "हिंदी"),
    GUJARATI("gu", "Gujarati", "ગુજરાતી"),
    MARATHI("mr", "Marathi", "मराठी"),
    KANNADA("kn", "Kannada", "ಕನ್ನಡ"),
    MALAYALAM("ml", "Malayalam", "മലയാളം"),
    TAMIL("ta", "Tamil", "தமிழ்"),
    TELUGU("te", "Telugu", "తెలుగు"),
    ODIA("or", "Odia", "ଓଡ଼ିଆ"),
    BENGALI("bn", "Bengali", "বাংলা"),
    ENGLISH("en", "English", "English");

    companion object {
        fun fromCode(code: String): IndicLanguage =
            entries.find { it.code == code } ?: HINDI
    }
}

/**
 * Predefined SOS alert templates for the Emergency Broadcast screen.
 */
enum class AlertTemplate(val displayName: String, val templateText: Map<String, String>) {
    MEDICAL_EMERGENCY(
        "Medical Emergency",
        mapOf(
            "hi" to "चिकित्सा आपातकाल! तत्काल सहायता की आवश्यकता है।",
            "en" to "Medical Emergency! Immediate assistance required.",
            "ta" to "மருத்துவ அவசரநிலை! உடனடி உதவி தேவை.",
            "te" to "వైద్య అత్యవసర పరిస్థితి! వెంటనే సహాయం అవసరం.",
            "bn" to "চিকিৎসা জরুরি অবস্থা! অবিলম্বে সাহায্য প্রয়োজন।"
        )
    ),
    FIRE(
        "Fire",
        mapOf(
            "hi" to "आग लगी है! सभी को तुरंत निकलें।",
            "en" to "Fire! Evacuate immediately.",
            "ta" to "தீ! உடனடியாக வெளியேறுங்கள்.",
            "te" to "అగ్ని! వెంటనే వెళ్ళిపోండి.",
            "bn" to "আগুন! অবিলম্বে সরে যান।"
        )
    ),
    STRUCTURAL_FAILURE(
        "Structural Failure",
        mapOf(
            "hi" to "संरचनात्मक विफलता! इमारत से बाहर निकलें।",
            "en" to "Structural Failure! Exit the building immediately.",
            "ta" to "கட்டமைப்பு சேதம்! உடனடியாக கட்டிடத்தை விட்டு வெளியேறுங்கள்.",
            "te" to "నిర్మాణ వైఫల్యం! భవనం నుండి వెంటనే బయటకు రండి.",
            "bn" to "কাঠামোগত ব্যর্থতা! অবিলম্বে ভবন থেকে বের হন।"
        )
    ),
    EVACUATION(
        "Evacuation",
        mapOf(
            "hi" to "निकासी! सभी लोग तुरंत सुरक्षित स्थान पर जाएं।",
            "en" to "Evacuation order! All personnel move to safe zone immediately.",
            "ta" to "வெளியேற்றம்! அனைவரும் உடனடியாக பாதுகாப்பான இடத்திற்கு செல்லுங்கள்.",
            "te" to "తరలింపు! అందరూ వెంటనే సురక్షిత ప్రాంతానికి వెళ్ళండి.",
            "bn" to "সরিয়ে নেওয়া! সকলে অবিলম্বে নিরাপদ অঞ্চলে যান।"
        )
    )
}
