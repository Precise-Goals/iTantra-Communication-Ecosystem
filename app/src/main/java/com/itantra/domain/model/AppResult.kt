package com.itantra.domain.model

/**
 * Generic result wrapper for all Domain A → Domain B data flows.
 * Typed error propagation via sealed class eliminates runtime type casting.
 */
sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    data class Error(val code: ErrorCode, val message: String) : AppResult<Nothing>()
    object Loading : AppResult<Nothing>()
}

/**
 * Exhaustive error codes for all failure modes in the iTantra pipeline.
 * Each code maps to a specific UI message in MainViewModel's error handler.
 */
enum class ErrorCode {
    MODEL_LOAD_FAILED,
    NETWORK_TIMEOUT,
    NETWORK_DROPPED,
    AUDIO_FOCUS_LOST,
    VAD_ERROR,
    STT_INFERENCE_FAILED,
    TTS_SYNTHESIS_FAILED,
    SOCKET_ERROR,
    PERMISSION_DENIED,
    BLUETOOTH_UNAVAILABLE,
    WIFI_DIRECT_UNAVAILABLE
}

/** Convenience extension to check success */
fun <T> AppResult<T>.isSuccess(): Boolean = this is AppResult.Success

/** Convenience extension to get data or null */
fun <T> AppResult<T>.getOrNull(): T? = (this as? AppResult.Success)?.data
