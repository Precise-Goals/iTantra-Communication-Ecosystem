package com.itantra

import com.itantra.core.audio.TextPostProcessor
import org.junit.Assert.assertEquals
import org.junit.Test

class TextPostProcessorUnitTest {

    @Test
    fun testSentenceFormation() {
        assertEquals("नमस्ते दुनिया।", TextPostProcessor.finish("  नमस्ते   दुनिया ", "hi"))
        assertEquals("আমি ভাত খাই।", TextPostProcessor.finish("আমি ভাত খাই", "bn"))
        assertEquals("मी घरी जातो.", TextPostProcessor.finish("मी घरी जातो", "mr"))
        assertEquals("Hello world.", TextPostProcessor.finish("hello world", "en"))
        assertEquals("Are you there?", TextPostProcessor.finish("are you there?", "en"))
        assertEquals("नमस्ते।", TextPostProcessor.finish("नमस्ते।", "hi"))
        assertEquals("", TextPostProcessor.finish("   ", "hi"))
    }
}
