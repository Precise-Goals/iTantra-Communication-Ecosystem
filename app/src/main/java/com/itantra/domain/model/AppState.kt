package com.itantra.domain.model

/**
 * Alert event emitted by Domain A when an ALERT-type TransceiverMessage is received.
 * Triggers the full-screen non-dismissible overlay in Domain B.
 */
data class AlertEvent(
    val message: TransceiverMessage,
    val receivedAt: Long = System.currentTimeMillis()
)

/**
 * Operating mode for the transceiver.
 */
enum class ConnectionMode {
    /** Half-duplex: user holds PTT button to speak. Explicit send action required. */
    PUSH_TO_TALK,
    /** Full-duplex: VAD continuously monitors; sends on detected pause. */
    PHONE_MODE
}

/**
 * Current state of the STT/VAD pipeline.
 */
enum class AudioPipelineState {
    IDLE,
    LISTENING,    // VAD active, waiting for speech
    CAPTURING,    // Speech detected, recording
    PROCESSING,   // STT inference running
    ERROR
}

/**
 * Overall system connection state.
 */
enum class NetworkState {
    DISCONNECTED,
    DISCOVERING,
    CONNECTING,
    CONNECTED_WIFI,
    CONNECTED_BLUETOOTH,
    RECONNECTING
}

/**
 * ML inference delegate currently active.
 */
enum class InferenceDelegate {
    NNAPI,
    GPU,
    XNNPACK_CPU
}
