package com.itantra.domain.contracts

import com.itantra.domain.model.AppResult
import com.itantra.domain.model.PeerDevice
import com.itantra.domain.model.TransceiverMessage

/**
 * Contract interface between Domain A (NetworkOrchestrator) and Domain B (MainViewModel).
 *
 * FROZEN POST SPRINT 1 — Do not modify signatures unilaterally.
 * All implementations emit on Dispatchers.IO; consumers collect on Dispatchers.Main.
 */
interface NetworkCallbacks {
    /** A new peer device has been discovered via Wi-Fi Direct or Bluetooth scan. */
    fun onNodeDiscovered(device: PeerDevice)

    /** A peer device has successfully connected and the transport socket is open. */
    fun onNodeConnected(device: PeerDevice)

    /** A previously connected peer has disconnected or timed out. */
    fun onNodeDisconnected(deviceId: String)

    /** A TransceiverMessage has been received from a peer and decoded from Protobuf. */
    fun onTextReceived(message: TransceiverMessage)

    /** A network-layer error has occurred (connection drop, socket error, etc.). */
    fun onNetworkError(error: AppResult.Error)

    /** Current connection latency measurement for a peer (RTT in ms). */
    fun onLatencyMeasured(deviceId: String, latencyMs: Long)
}
