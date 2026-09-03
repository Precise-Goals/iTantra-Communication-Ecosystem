package com.itantra.domain.model

/**
 * Represents a peer device discovered in the iTantra P2P mesh network.
 * Stored locally in Room DB via PeerRegistryRepository.
 */
data class PeerDevice(
    /** Wi-Fi Direct MAC or Bluetooth MAC address — used as primary key */
    val deviceId: String,
    /** User-set display name OR auto-generated from build fingerprint hash */
    val deviceName: String,
    /** Signal strength in dBm (more negative = weaker). Used for Radar positioning. */
    val rssi: Int = -70,
    /** Active transport protocol */
    val connectionType: ConnectionType = ConnectionType.WIFI_DIRECT,
    /** Last measured round-trip time in milliseconds */
    val latencyMs: Long = 0L,
    /** Epoch ms of the last received TransceiverMessage from this peer */
    val lastSeenAt: Long = System.currentTimeMillis(),
    /** Whether this peer is currently actively connected */
    val isConnected: Boolean = false,
    /** Whether the local user has authorized this peer for communication */
    val isAuthorized: Boolean = false,
    /** Whether this is a newly discovered peer not yet seen before */
    val isNew: Boolean = false
)

enum class ConnectionType {
    WIFI_DIRECT,
    BLUETOOTH
}
