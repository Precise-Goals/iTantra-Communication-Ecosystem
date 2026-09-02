package com.itantra

import com.itantra.core.download.HashUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the SHA-256 header-normalization and comparison logic that replaced
 * ModelRegistry's literal "placeholder_sha256_*" strings with real integrity checks.
 */
class HashUtilsUnitTest {

    private val validHex64 = "2084d321e1d2752f2b64ed3012ba27751df01a80da46f52920098cdcb7e35648"

    @Test
    fun testNormalizeHashHeaderPlainHex() {
        assertEquals(validHex64, HashUtils.normalizeHashHeader(validHex64))
    }

    @Test
    fun testNormalizeHashHeaderStripsQuotes() {
        assertEquals(validHex64, HashUtils.normalizeHashHeader("\"$validHex64\""))
    }

    @Test
    fun testNormalizeHashHeaderStripsSha256Prefix() {
        assertEquals(validHex64, HashUtils.normalizeHashHeader("sha256:$validHex64"))
    }

    @Test
    fun testNormalizeHashHeaderQuotedWithPrefix() {
        assertEquals(validHex64, HashUtils.normalizeHashHeader("\"sha256:$validHex64\""))
    }

    @Test
    fun testNormalizeHashHeaderLowercasesMixedCase() {
        val mixed = validHex64.replaceRange(0, 8, validHex64.substring(0, 8).uppercase())
        assertEquals(validHex64, HashUtils.normalizeHashHeader(mixed))
    }

    @Test
    fun testNormalizeHashHeaderRejectsWrongLength() {
        assertNull(HashUtils.normalizeHashHeader(validHex64.dropLast(1)))
        assertNull(HashUtils.normalizeHashHeader("${validHex64}ab"))
    }

    @Test
    fun testNormalizeHashHeaderRejectsNonHex() {
        assertNull(HashUtils.normalizeHashHeader("W" + validHex64.drop(1)))
    }

    @Test
    fun testNormalizeHashHeaderRejectsWeakEtag() {
        // A typical non-LFS weak ETag, e.g. from GitHub raw content — not a SHA-256 at all.
        assertNull(HashUtils.normalizeHashHeader("W/\"a1b2c3d4\""))
    }

    @Test
    fun testNormalizeHashHeaderNullInput() {
        assertNull(HashUtils.normalizeHashHeader(null))
    }

    @Test
    fun testHashesMatchCaseInsensitive() {
        assertTrue(HashUtils.hashesMatch(validHex64, validHex64.uppercase()))
    }

    @Test
    fun testHashesMatchDetectsMismatch() {
        val tampered = "0" + validHex64.drop(1)
        assertFalse(HashUtils.hashesMatch(validHex64, tampered))
    }
}
