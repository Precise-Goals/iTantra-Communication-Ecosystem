package com.itantra.core.audio

/**
 * Pure, Android/ONNX-free CTC decoding logic shared by [STTModule] and unit tests.
 * Kept separate from STTModule so it can be exercised without an ONNX session or Context.
 */
object CtcDecoder {

    /** Parses a sherpa-onnx `tokens.txt` file's *content* (`<token> <id>` per line) into an id-indexed array. */
    fun parseTokens(fileContent: String): Array<String> {
        var maxId = -1
        val entries = mutableListOf<Pair<Int, String>>()
        for (line in fileContent.lineSequence()) {
            if (line.isBlank()) continue
            val sep = line.lastIndexOf(' ')
            if (sep <= 0) continue
            val token = line.substring(0, sep)
            val id = line.substring(sep + 1).trim().toIntOrNull() ?: continue
            entries.add(id to token)
            if (id > maxId) maxId = id
        }
        if (maxId < 0) return emptyArray()
        val vocab = Array(maxId + 1) { "" }
        for ((id, token) in entries) vocab[id] = token
        return vocab
    }

    /** Resolves a token id to its subword string via [vocab], merging `▁` (word boundary) into a space. */
    fun decodeToken(token: Int, vocab: Array<String>): String =
        vocab.getOrNull(token)?.replace("▁", " ") ?: ""

    /** Greedy CTC decode: argmax per time step, merge repeated, remove blank, resolve via [vocab]. */
    fun greedyDecode(logits: Array<FloatArray>, vocab: Array<String>, blankId: Int = 0): String {
        val sb = StringBuilder()
        var prevToken = -1

        for (frame in logits) {
            val token = frame.indices.maxByOrNull { frame[it] } ?: blankId
            if (token != blankId && token != prevToken) {
                sb.append(decodeToken(token, vocab))
            }
            prevToken = token
        }
        return sb.toString().trim()
    }
}
