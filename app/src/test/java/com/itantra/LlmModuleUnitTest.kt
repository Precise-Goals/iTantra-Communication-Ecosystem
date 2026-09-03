package com.itantra

import com.itantra.core.ai.LlmModule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the ABI-gating logic that decides whether real Phi-3 inference (LlmModule) is even
 * attempted — the llamacpp-kotlin 0.4.0 AAR only ships native libs for arm64-v8a and x86_64
 * (confirmed by inspecting the AAR's jni/ contents), so armeabi-v7a-only devices must fall back
 * to TacticalAiEngine honestly instead of crashing on a missing native library.
 */
class LlmModuleUnitTest {

    @Test
    fun testArm64AloneIsSupported() {
        assertTrue(LlmModule.isAbiSupported(listOf("arm64-v8a")))
    }

    @Test
    fun testX86_64AloneIsSupported() {
        assertTrue(LlmModule.isAbiSupported(listOf("x86_64")))
    }

    @Test
    fun testRealisticArm64DeviceAbiList() {
        // Build.SUPPORTED_ABIS on a real 64-bit ARM device typically lists both, most-preferred first.
        assertTrue(LlmModule.isAbiSupported(listOf("arm64-v8a", "armeabi-v7a", "armeabi")))
    }

    @Test
    fun testArmeabiV7aOnlyIsNotSupported() {
        // The real constraint this test guards: 32-bit-only devices get no native lib at all.
        assertFalse(LlmModule.isAbiSupported(listOf("armeabi-v7a", "armeabi")))
    }

    @Test
    fun testEmptyAbiListIsNotSupported() {
        assertFalse(LlmModule.isAbiSupported(emptyList()))
    }

    @Test
    fun testUnknownAbiIsNotSupported() {
        assertFalse(LlmModule.isAbiSupported(listOf("mips")))
    }
}
