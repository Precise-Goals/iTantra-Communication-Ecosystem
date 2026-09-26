package com.itantra.core.audio

/**
 * Turns a raw STT decode into a sentence (T42).
 *
 * The problem statement asks the STT module to "form the sentences detected". Neither the CTC
 * nor the TDT decode produces punctuation. Each VAD phrase is already a sentence (the pause is
 * the boundary), so this only normalises whitespace, adds a sentence terminator and capitalises
 * English. Deliberately rule-based: a punctuation-restoration model would cost more RAM and
 * latency than the whole rest of the pipeline.
 */
object TextPostProcessor {

    /** Languages whose modern orthography ends a sentence with the danda. Marathi and Gujarati
     *  use a full stop in modern writing, so they are deliberately not listed. */
    private val DANDA_LANGS = setOf("hi", "bn", "or")

    private val TERMINATORS = charArrayOf('.', '!', '?', '।', '॥')

    fun finish(raw: String, languageCode: String): String {
        var s = raw.trim().replace(Regex("\\s+"), " ")
        if (s.isEmpty()) return s
        if (s.last() !in TERMINATORS) s += if (languageCode in DANDA_LANGS) "।" else "."
        // Latin script capitalises; Indic scripts have no case.
        if (languageCode == "en") s = s.replaceFirstChar { it.uppercase() }
        return s
    }
}
