package com.itantra.domain.model

/**
 * Represents a discovered or connected peer device in the iTantra mesh network.
 * Populated by WifiDirectManager or BluetoothRFCOMMManager and emitted via NetworkCallbacks.
 */
data class PeerDevice(
    /** Wi-Fi Direct MAC address or Bluetooth MAC address */
    val deviceId: String,
    /** Human-readable device name */
    val deviceName: String,
    /** Signal strength in dBm (more negative = weaker). Used for Radar screen positioning. */
    val rssi: Int = -70,
    /** Active transport protocol for this peer */
    val connectionType: ConnectionType = ConnectionType.WIFI_DIRECT,
    /** Last measured round-trip time in milliseconds */
    val latencyMs: Long = 0L,
    /** Epoch ms of the last received TransceiverMessage from this peer */
    val lastMessageAt: Long = 0L,
    /** Whether this peer is currently actively connected */
    val isConnected: Boolean = false
)

enum class ConnectionType {
    WIFI_DIRECT,
    BLUETOOTH
}
