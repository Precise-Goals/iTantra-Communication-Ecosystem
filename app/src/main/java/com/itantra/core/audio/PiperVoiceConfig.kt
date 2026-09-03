package com.itantra.core.audio

/**
 * Piper Voice Configuration for each supported language.
 *
 * Single-speaker models (3 ONNX inputs: input, input_lengths, scales):
 *   hi_vits_int8.onnx, mr_vits_int8.onnx, ml_vits_int8.onnx,
 *   bn_vits_int8.onnx, en_piper_int8.onnx, te_vits_int8.onnx
 *
 * Multi-speaker models (4 ONNX inputs: input, input_lengths, scales, sid):
 *   gu_vits_int8.onnx, kn_vits_int8.onnx, ta_vits_int8.onnx, or_vits_int8.onnx
 */
object PiperVoiceConfig {

    private val MULTI_SPEAKER_LANGS = setOf("gu", "kn", "ta", "or")

    fun isMultiSpeaker(langCode: String): Boolean = langCode in MULTI_SPEAKER_LANGS

    fun getDefaultSpeakerId(langCode: String): Long = 0L

    /**
     * Encode text into Piper phoneme ID sequence.
     * Uses Unicode codepoint offset mapping per script range.
     * Wraps in BOS (1) + phoneme_ids + EOS (2) per Piper spec.
     */
    fun encodeText(text: String, langCode: String): LongArray {
        val normalized = normalizeText(text, langCode)
        val BOS = 1L
        val EOS = 2L

        val charIds = mutableListOf<Long>()
        for (cp in normalized.codePoints().toArray()) {
            val id = getPhonemeId(cp, langCode)
            if (id != null) charIds.add(id)
        }

        if (charIds.isEmpty()) return longArrayOf()

        return longArrayOf(BOS) + charIds.toLongArray() + longArrayOf(EOS)
    }

    private fun normalizeText(text: String, langCode: String): String {
        val nfc = java.text.Normalizer.normalize(text.trim(), java.text.Normalizer.Form.NFC)
        return nfc.filter { it.code > 31 }
    }

    private fun getPhonemeId(cp: Int, langCode: String): Long? = when {
        cp == 0x20 -> 3L
        cp in PUNCT_MAP -> PUNCT_MAP[cp]
        // Latin letters (English or loanwords in any Indic script)
        cp in 0x61..0x7A -> (cp - 0x61 + 21).toLong()
        cp in 0x41..0x5A -> (cp - 0x41 + 21).toLong()
        // Indic script ranges
        langCode in setOf("hi","mr") && cp in 0x0900..0x097F -> (cp - 0x0900 + 21).toLong()
        langCode == "ml" && cp in 0x0D00..0x0D7F -> (cp - 0x0D00 + 21).toLong()
        langCode == "bn" && cp in 0x0980..0x09FF -> (cp - 0x0980 + 21).toLong()
        langCode == "te" && cp in 0x0C00..0x0C7F -> (cp - 0x0C00 + 21).toLong()
        langCode == "kn" && cp in 0x0C80..0x0CFF -> (cp - 0x0C80 + 21).toLong()
        langCode == "gu" && cp in 0x0A80..0x0AFF -> (cp - 0x0A80 + 21).toLong()
        langCode == "ta" && cp in 0x0B80..0x0BFF -> (cp - 0x0B80 + 21).toLong()
        langCode == "or" && cp in 0x0B00..0x0B7F -> (cp - 0x0B00 + 21).toLong()
        else -> null
    }

    private val PUNCT_MAP: Map<Int, Long> = mapOf(
        '!'.code to 4L, '"'.code to 5L, '\''.code to 6L, '('.code to 7L,
        ')'.code to 8L, ','.code to 9L, '-'.code to 10L, '.'.code to 11L,
        ':'.code to 12L, ';'.code to 13L, '?'.code to 14L
    )
}

private operator fun LongArray.plus(other: LongArray): LongArray {
    val result = LongArray(size + other.size)
    System.arraycopy(this, 0, result, 0, size)
    System.arraycopy(other, 0, result, size, other.size)
    return result
}
