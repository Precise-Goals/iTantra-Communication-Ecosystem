package com.itantra.core.audio

/**
 * IndicTextNormalizer — Lightweight offline text normalization for Indian languages & English.
 *
 * Cleans and converts raw STT transcriptions before feeding into neural TTS (MeloTTS / Piper / VITS):
 * 1. Expands currency symbols (e.g., "₹100" -> "एक सौ रुपये" in Hindi, "one hundred rupees" in English).
 * 2. Expands numerical digits to natural spoken words.
 * 3. Expands common unit abbreviations (km, hr, kg).
 * 4. Cleanses non-pronounceable punctuation and emojis.
 */
object IndicTextNormalizer {

    private val HINDI_DIGITS = arrayOf("शून्य", "एक", "दो", "तीन", "चार", "पाँच", "छह", "सात", "आठ", "नौ")
    private val MARATHI_DIGITS = arrayOf("शून्य", "एक", "दोन", "तीन", "चार", "पाच", "सहा", "सात", "आठ", "नऊ")
    private val ENGLISH_DIGITS = arrayOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine")

    private val HINDI_NUMBERS = mapOf(
        10 to "दस", 20 to "बीस", 30 to "तीस", 40 to "चालीस", 50 to "पचास",
        60 to "साठ", 70 to "सत्तर", 80 to "अस्सी", 90 to "नब्बे", 100 to "सौ",
        500 to "पाँच सौ", 1000 to "हज़ार"
    )

    private val ENGLISH_NUMBERS = mapOf(
        10 to "ten", 20 to "twenty", 30 to "thirty", 40 to "forty", 50 to "fifty",
        60 to "sixty", 70 to "seventy", 80 to "eighty", 90 to "ninety", 100 to "one hundred",
        500 to "five hundred", 1000 to "one thousand"
    )

    /**
     * Normalize text for the designated target language before feeding to TTS.
     */
    fun normalize(text: String, lang: String): String {
        if (text.isBlank()) return text

        var normalized = text.trim()

        // 1. Remove emojis and unusual symbols
        normalized = normalized.replace(Regex("[^\\p{L}\\p{N}\\p{P}\\p{Z}\\n]"), " ")

        // 2. Normalize Currency (₹ or Rs.)
        normalized = normalizeCurrency(normalized, lang)

        // 3. Normalize common units
        normalized = normalizeUnits(normalized, lang)

        // 4. Normalize standalone numbers
        normalized = normalizeNumbers(normalized, lang)

        // 5. Clean duplicate whitespace
        return normalized.replace(Regex("\\s+"), " ").trim()
    }

    private fun normalizeCurrency(text: String, lang: String): String {
        val currencyRegex = Regex("(?:₹|Rs\\.?|INR)\\s*(\\d+)")
        return currencyRegex.replace(text) { match ->
            val amountStr = match.groupValues[1]
            val num = amountStr.toIntOrNull()
            when (lang) {
                "hi" -> {
                    val spoken = if (num != null && HINDI_NUMBERS.containsKey(num)) HINDI_NUMBERS[num]!!
                    else digitsToSpoken(amountStr, HINDI_DIGITS)
                    "$spoken रुपये"
                }
                "mr" -> {
                    val spoken = digitsToSpoken(amountStr, MARATHI_DIGITS)
                    "$spoken रुपये"
                }
                "en" -> {
                    val spoken = if (num != null && ENGLISH_NUMBERS.containsKey(num)) ENGLISH_NUMBERS[num]!!
                    else digitsToSpoken(amountStr, ENGLISH_DIGITS)
                    "$spoken rupees"
                }
                else -> {
                    val spoken = digitsToSpoken(amountStr, HINDI_DIGITS)
                    "$spoken रुपये"
                }
            }
        }
    }

    private fun normalizeUnits(text: String, lang: String): String {
        var res = text
        when (lang) {
            "hi" -> {
                res = res.replace(Regex("(?i)\\b(\\d+)\\s*km/h\\b"), "$1 किलोमीटर प्रति घंटा")
                res = res.replace(Regex("(?i)\\b(\\d+)\\s*km\\b"), "$1 किलोमीटर")
                res = res.replace(Regex("(?i)\\b(\\d+)\\s*kg\\b"), "$1 किलो")
                res = res.replace(Regex("(?i)\\bSOS\\b"), "एस ओ एस")
            }
            "mr" -> {
                res = res.replace(Regex("(?i)\\b(\\d+)\\s*km/h\\b"), "$1 किलोमीटर प्रति तास")
                res = res.replace(Regex("(?i)\\b(\\d+)\\s*km\\b"), "$1 किलोमीटर")
                res = res.replace(Regex("(?i)\\b(\\d+)\\s*kg\\b"), "$1 किलो")
                res = res.replace(Regex("(?i)\\bSOS\\b"), "एस ओ एस")
            }
            "en" -> {
                res = res.replace(Regex("(?i)\\b(\\d+)\\s*km/h\\b"), "$1 kilometers per hour")
                res = res.replace(Regex("(?i)\\b(\\d+)\\s*km\\b"), "$1 kilometers")
                res = res.replace(Regex("(?i)\\b(\\d+)\\s*kg\\b"), "$1 kilograms")
            }
        }
        return res
    }

    private fun normalizeNumbers(text: String, lang: String): String {
        val numberRegex = Regex("\\b(\\d+)\\b")
        val digitsMap = when (lang) {
            "hi" -> HINDI_DIGITS
            "mr" -> MARATHI_DIGITS
            "en" -> ENGLISH_DIGITS
            else -> HINDI_DIGITS
        }

        return numberRegex.replace(text) { match ->
            val numStr = match.groupValues[1]
            val num = numStr.toIntOrNull()
            if (lang == "hi" && num != null && HINDI_NUMBERS.containsKey(num)) {
                HINDI_NUMBERS[num]!!
            } else if (lang == "en" && num != null && ENGLISH_NUMBERS.containsKey(num)) {
                ENGLISH_NUMBERS[num]!!
            } else {
                digitsToSpoken(numStr, digitsMap)
            }
        }
    }

    private fun digitsToSpoken(numStr: String, digits: Array<String>): String {
        val sb = StringBuilder()
        for (ch in numStr) {
            val d = ch - '0'
            if (d in 0..9) {
                if (sb.isNotEmpty()) sb.append(" ")
                sb.append(digits[d])
            }
        }
        return sb.toString()
    }
}
