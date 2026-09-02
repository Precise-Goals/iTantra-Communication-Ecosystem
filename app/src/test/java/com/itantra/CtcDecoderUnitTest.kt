package com.itantra

import com.itantra.core.audio.CtcDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the real tokenizer-backed CTC decode path (replacing the old stub that
 * always returned "" regardless of the model's output).
 */
class CtcDecoderUnitTest {

    private val sampleTokensFile = """
        <blk> 0
        ▁ 1
        h 2
        i 3
        ▁hi 4
    """.trimIndent()

    @Test
    fun testParseTokensBuildsIdIndexedVocab() {
        val vocab = CtcDecoder.parseTokens(sampleTokensFile)
        assertEquals(5, vocab.size)
        assertEquals("<blk>", vocab[0])
        assertEquals("▁", vocab[1])
        assertEquals("h", vocab[2])
        assertEquals("i", vocab[3])
        assertEquals("▁hi", vocab[4])
    }

    @Test
    fun testParseTokensIgnoresBlankLines() {
        val vocab = CtcDecoder.parseTokens("<blk> 0\n\n  \nh 1\n")
        assertEquals(2, vocab.size)
        assertEquals("<blk>", vocab[0])
        assertEquals("h", vocab[1])
    }

    @Test
    fun testDecodeTokenReplacesWordBoundaryMarkerWithSpace() {
        val vocab = CtcDecoder.parseTokens(sampleTokensFile)
        assertEquals(" ", CtcDecoder.decodeToken(1, vocab))
        assertEquals(" hi", CtcDecoder.decodeToken(4, vocab))
    }

    @Test
    fun testDecodeTokenOutOfRangeReturnsEmpty() {
        val vocab = CtcDecoder.parseTokens(sampleTokensFile)
        assertEquals("", CtcDecoder.decodeToken(99, vocab))
    }

    @Test
    fun testGreedyDecodeMergesRepeatsAndDropsBlank() {
        val vocab = CtcDecoder.parseTokens(sampleTokensFile)
        // Frame-by-frame argmax sequence: blk, h, h, blk, i, blk, blk -> "h" + "i" = "hi"
        val logits = arrayOf(
            frameFavoring(0), // <blk>
            frameFavoring(2), // h
            frameFavoring(2), // h (repeat, merged)
            frameFavoring(0), // <blk>
            frameFavoring(3), // i
            frameFavoring(0), // <blk>
            frameFavoring(0)  // <blk>
        )
        val text = CtcDecoder.greedyDecode(logits, vocab)
        assertEquals("hi", text)
    }

    @Test
    fun testGreedyDecodeEmptyLogitsReturnsEmptyString() {
        val vocab = CtcDecoder.parseTokens(sampleTokensFile)
        val text = CtcDecoder.greedyDecode(emptyArray(), vocab)
        assertTrue(text.isEmpty())
    }

    @Test
    fun testGreedyDecodeAllBlankReturnsEmptyString() {
        val vocab = CtcDecoder.parseTokens(sampleTokensFile)
        val logits = arrayOf(frameFavoring(0), frameFavoring(0), frameFavoring(0))
        val text = CtcDecoder.greedyDecode(logits, vocab)
        assertTrue(text.isEmpty())
    }

    /** Builds a 5-wide logits frame where [id] has the highest score. */
    private fun frameFavoring(id: Int): FloatArray {
        val frame = FloatArray(5) { 0.01f }
        frame[id] = 10f
        return frame
    }
}
