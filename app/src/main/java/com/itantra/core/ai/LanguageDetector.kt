package com.itantra.core.ai

/**
 * On-device Language Identification (LID) for 10 Scheduled Indian Languages.
 *
 * Uses Unicode script range analysis combined with fast token n-grams,
 * providing zero-latency, deterministic language classification.
 * Matches ISO 639-1 / BCP-47 codes:
 * hi (Hindi), mr (Marathi), bn (Bengali), gu (Gujarati),
 * ta (Tamil), te (Telugu), kn (Kannada), ml (Malayalam),
 * or (Odia), en (English).
 */
object LanguageDetector {

    data class DetectionResult(
        val languageCode: String,
        val languageName: String,
        val confidence: Float
    )

    fun detect(text: String): DetectionResult {
        if (text.isBlank()) return DetectionResult("en", "English", 1.0f)

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
                    if (code == 0x0933) hasMarathiSpecificChar = true // Marathi letter LLA 'ळ'
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

        if (total == 0) return DetectionResult("en", "English", 0.5f)

        return when {
            devanagariCount > 0 && devanagariCount >= maxOf(bengaliCount, gujaratiCount, tamilCount, teluguCount, kannadaCount, malayalamCount, latinCount) -> {
                val conf = (devanagariCount.toFloat() / total).coerceIn(0.7f, 1.0f)
                val lower = text.lowercase()
                val isMarathi = hasMarathiSpecificChar ||
                        lower.contains("आहे") || lower.contains("नाही") || lower.contains("काय") ||
                        lower.contains("कसे") || lower.contains("आणि") || lower.contains("करा")
                if (isMarathi) {
                    DetectionResult("mr", "Marathi", conf)
                } else {
                    DetectionResult("hi", "Hindi", conf)
                }
            }
            bengaliCount > 0 && bengaliCount >= total / 3 ->
                DetectionResult("bn", "Bengali", (bengaliCount.toFloat() / total).coerceIn(0.7f, 1.0f))
            gujaratiCount > 0 && gujaratiCount >= total / 3 ->
                DetectionResult("gu", "Gujarati", (gujaratiCount.toFloat() / total).coerceIn(0.7f, 1.0f))
            tamilCount > 0 && tamilCount >= total / 3 ->
                DetectionResult("ta", "Tamil", (tamilCount.toFloat() / total).coerceIn(0.7f, 1.0f))
            teluguCount > 0 && teluguCount >= total / 3 ->
                DetectionResult("te", "Telugu", (teluguCount.toFloat() / total).coerceIn(0.7f, 1.0f))
            kannadaCount > 0 && kannadaCount >= total / 3 ->
                DetectionResult("kn", "Kannada", (kannadaCount.toFloat() / total).coerceIn(0.7f, 1.0f))
            malayalamCount > 0 && malayalamCount >= total / 3 ->
                DetectionResult("ml", "Malayalam", (malayalamCount.toFloat() / total).coerceIn(0.7f, 1.0f))
            odiaCount > 0 && odiaCount >= total / 3 ->
                DetectionResult("or", "Odia", (odiaCount.toFloat() / total).coerceIn(0.7f, 1.0f))
            else ->
                DetectionResult("en", "English", 0.95f)
        }
    }
}
