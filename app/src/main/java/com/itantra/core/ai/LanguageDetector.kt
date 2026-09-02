package com.itantra.core.ai

/**
 * High-precision on-device Language Identification and Code-Switching Detector.
 *
 * Detects:
 * 1. Native Indic Scripts (Devanagari, Bengali, Gujarati, Tamil, Telugu, Kannada, Malayalam, Odia)
 * 2. Romanized Code-Switching Dialects:
 *    - Hinglish (Hindi written in Latin script)
 *    - Manglish (Marathi written in Latin script)
 *    - Tanglish (Tamil written in Latin script)
 *    - Telugish (Telugu written in Latin script)
 *    - Kannadish (Kannada written in Latin script)
 *    - Banglish (Bengali written in Latin script)
 *    - Pure English
 */
object LanguageDetector {

    data class DetectionResult(
        val languageCode: String,      // BCP-47: hi, mr, bn, gu, ta, te, kn, ml, or, en
        val dialectName: String,       // e.g. "Hinglish", "Hindi", "Marathi", "English"
        val isCodeSwitched: Boolean,   // true if written in Latin script representing an Indic language
        val confidence: Float
    )

    // Common Romanized Hinglish vocabulary
    private val HINGLISH_TOKENS = setOf(
        "kya", "kaise", "kahan", "kidhar", "madad", "paani", "pani", "bhai", "chahiye",
        "raha", "rahi", "rahe", "hai", "hain", "nahi", "nahin", "aayegi", "aayega",
        "kare", "karo", "karna", "jaldi", "bachao", "aag", "dard", "chot", "khana",
        "doctor", "aspataal", "batao", "suno", "namaste", "haan", "theek", "thik",
        "ruk", "jao", "dekh", "mujhe", "hume", "hum", "aap", "tum", "mera", "meri",
        "bataiye", "bhejo", "bhej", "phas", "phase", "surakshit", "kripya", "turant"
    )

    // Common Romanized Marathi vocabulary
    private val MARATHI_LATIN_TOKENS = setOf(
        "kasa", "kashi", "ahe", "aahe", "nahit", "kuthe", "madat", "pahije", "kay",
        "karu", "kara", "aani", "ani", "sang", "sanga", "lavkar", "jau", "yeu",
        "aamhi", "amhi", "tula", "mala", "aapan", "bhau", "khup", "thamb"
    )

    // Common Romanized Tamil vocabulary
    private val TAMIL_LATIN_TOKENS = setOf(
        "vanakkam", "epdi", "eppadi", "irukinga", "iruku", "irukku", "thanni", "venum",
        "udhavi", "udhavi", "kudunga", "enga", "inge", "ange", "seekiram", "vaanga", "solunga"
    )

    // Common Romanized Telugu vocabulary
    private val TELUGU_LATIN_TOKENS = setOf(
        "namaskaram", "ela", "unnaru", "sahayam", "kavali", "neelu", "ekkada",
        "cheppandi", "randi", "tvaraga", "baga", "ledu", "undhi"
    )

    // Common Romanized Kannada vocabulary
    private val KANNADA_LATIN_TOKENS = setOf(
        "namaskara", "hegidira", "sahaya", "beku", "neeru", "elli", "heli", "banni",
        "begane", "illa", "ide"
    )

    // Common Romanized Bengali vocabulary
    private val BENGALI_LATIN_TOKENS = setOf(
        "kemon", "achen", "sahajjo", "sahajjyo", "dorakar", "jol", "kothay", "bolun",
        "ashun", "taratari", "nei", "ache"
    )

    fun detect(text: String): DetectionResult {
        if (text.isBlank()) return DetectionResult("en", "English", false, 1.0f)

        var devanagariCount = 0
        var bengaliCount = 0
        var gujaratiCount = 0
        var tamilCount = 0
        var teluguCount = 0
        var kannadaCount = 0
        var malayalamCount = 0
        var odiaCount = 0
        var latinCount = 0
        var hasMarathiSpecificChar = false

        for (char in text) {
            val code = char.code
            when (code) {
                in 0x0900..0x097F -> {
                    devanagariCount++
                    if (code == 0x0933) hasMarathiSpecificChar = true // ळ
                }
                in 0x0980..0x09FF -> bengaliCount++
                in 0x0A80..0x0AFF -> gujaratiCount++
                in 0x0B80..0x0BFF -> tamilCount++
                in 0x0C00..0x0C7F -> teluguCount++
                in 0x0C80..0x0CFF -> kannadaCount++
                in 0x0D00..0x0D7F -> malayalamCount++
                in 0x0B00..0x0B7F -> odiaCount++
                in 0x0041..0x005A, in 0x0061..0x007A -> latinCount++
            }
        }

        val total = devanagariCount + bengaliCount + gujaratiCount + tamilCount +
                teluguCount + kannadaCount + malayalamCount + odiaCount + latinCount

        if (total == 0) return DetectionResult("en", "English", false, 0.5f)

        // 1. Direct Native Indic Script Match
        if (devanagariCount > 0 && devanagariCount >= latinCount) {
            val lower = text.lowercase()
            val isMarathi = hasMarathiSpecificChar ||
                    lower.contains("आहे") || lower.contains("नाही") || lower.contains("काय") ||
                    lower.contains("कसे") || lower.contains("आणि") || lower.contains("करा")
            return if (isMarathi) {
                DetectionResult("mr", "Marathi", false, 0.95f)
            } else {
                DetectionResult("hi", "Hindi", false, 0.95f)
            }
        }
        if (bengaliCount > 0 && bengaliCount >= latinCount) return DetectionResult("bn", "Bengali", false, 0.95f)
        if (gujaratiCount > 0 && gujaratiCount >= latinCount) return DetectionResult("gu", "Gujarati", false, 0.95f)
        if (tamilCount > 0 && tamilCount >= latinCount) return DetectionResult("ta", "Tamil", false, 0.95f)
        if (teluguCount > 0 && teluguCount >= latinCount) return DetectionResult("te", "Telugu", false, 0.95f)
        if (kannadaCount > 0 && kannadaCount >= latinCount) return DetectionResult("kn", "Kannada", false, 0.95f)
        if (malayalamCount > 0 && malayalamCount >= latinCount) return DetectionResult("ml", "Malayalam", false, 0.95f)
        if (odiaCount > 0 && odiaCount >= latinCount) return DetectionResult("or", "Odia", false, 0.95f)

        // 2. Latin-Script Code-Switching (Hinglish, Manglish, Tanglish, etc.)
        val words = text.lowercase().split(Regex("""[\s,?.!]+""")).filter { it.isNotBlank() }
        var hinglishHits = 0
        var marathiHits = 0
        var tamilHits = 0
        var teluguHits = 0
        var kannadaHits = 0
        var bengaliHits = 0

        for (word in words) {
            if (HINGLISH_TOKENS.contains(word)) hinglishHits++
            if (MARATHI_LATIN_TOKENS.contains(word)) marathiHits++
            if (TAMIL_LATIN_TOKENS.contains(word)) tamilHits++
            if (TELUGU_LATIN_TOKENS.contains(word)) teluguHits++
            if (KANNADA_LATIN_TOKENS.contains(word)) kannadaHits++
            if (BENGALI_LATIN_TOKENS.contains(word)) bengaliHits++
        }

        return when {
            hinglishHits > 0 && hinglishHits >= maxOf(marathiHits, tamilHits, teluguHits, kannadaHits, bengaliHits) ->
                DetectionResult("hi", "Hinglish", true, 0.92f)
            marathiHits > 0 ->
                DetectionResult("mr", "Marathi-English", true, 0.90f)
            tamilHits > 0 ->
                DetectionResult("ta", "Tanglish", true, 0.90f)
            teluguHits > 0 ->
                DetectionResult("te", "Telugu-English", true, 0.90f)
            kannadaHits > 0 ->
                DetectionResult("kn", "Kannada-English", true, 0.90f)
            bengaliHits > 0 ->
                DetectionResult("bn", "Banglish", true, 0.90f)
            else ->
                DetectionResult("en", "English", false, 0.95f)
        }
    }
}
