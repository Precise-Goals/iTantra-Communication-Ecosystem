package com.itantra.core.ai

import android.content.Context
import android.util.Log

/**
 * ModelEngine — Native C++ & ONNX Runtime TinyML acceleration bridge.
 *
 * Implements deliverable 2 from the SIH Voice Transceiver Architecture:
 * - Bridges Kotlin audio buffers to the native C++ layer (`native-lib.cpp`).
 * - Manages direct memory buffers for low Real-Time Factor (RTF) inference.
 * - Supports both Saaras STT (150M parameter multilingual Indic model) and
 *   MeloTTS / Piper INT8 acoustic models.
 */
class ModelEngine(private val context: Context) {

    companion object {
        private const val TAG = "ModelEngine"
        private var isNativeLibraryLoaded = false

        init {
            try {
                System.loadLibrary("itantra_native")
                isNativeLibraryLoaded = true
                Log.i(TAG, "Native library libitantra_native.so loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "libitantra_native.so not bundled; utilizing ONNX Runtime Java engine directly")
            }
        }
    }

    private var nativeSessionHandle: Long = 0L

    fun initialize(): Boolean {
        return if (isNativeLibraryLoaded) {
            try {
                initEngine()
            } catch (e: Exception) {
                Log.w(TAG, "Error initializing native engine: ${e.message}")
                false
            }
        } else {
            false
        }
    }

    fun loadModel(modelPath: String, numThreads: Int = 4): Boolean {
        if (!isNativeLibraryLoaded) return false
        return try {
            nativeSessionHandle = loadModelNative(modelPath, numThreads)
            nativeSessionHandle != 0L
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model natively: ${e.message}")
            false
        }
    }

    fun runSTT(audioBuffer: FloatArray, sampleCount: Int, lang: String): String {
        if (!isNativeLibraryLoaded || nativeSessionHandle == 0L) return ""
        return try {
            runSTTNative(nativeSessionHandle, audioBuffer, sampleCount, lang)
        } catch (e: Exception) {
            Log.e(TAG, "Native STT error: ${e.message}")
            ""
        }
    }

    fun runTTS(text: String, lang: String, speed: Float = 1.0f): FloatArray? {
        if (!isNativeLibraryLoaded || nativeSessionHandle == 0L) return null
        return try {
            runTTSNative(nativeSessionHandle, text, lang, speed)
        } catch (e: Exception) {
            Log.e(TAG, "Native TTS error: ${e.message}")
            null
        }
    }

    fun release() {
        if (isNativeLibraryLoaded && nativeSessionHandle != 0L) {
            try {
                releaseNative(nativeSessionHandle)
                nativeSessionHandle = 0L
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing native model: ${e.message}")
            }
        }
    }

    // ── Native JNI Declarations ─────────────────────────────────────────
    private external fun initEngine(): Boolean
    private external fun loadModelNative(modelPath: String, numThreads: Int): Long
    private external fun runSTTNative(sessionHandle: Long, audioBuffer: FloatArray, sampleCount: Int, langCode: String): String
    private external fun runTTSNative(sessionHandle: Long, text: String, langCode: String, speed: Float): FloatArray?
    private external fun releaseNative(sessionHandle: Long)
}
