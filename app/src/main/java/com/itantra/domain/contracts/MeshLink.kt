package com.itantra.domain.contracts

import com.itantra.domain.model.AppResult
import kotlinx.coroutines.flow.SharedFlow

/**
 * Transport-agnostic contract for anything that can carry a framed iTantra payload
 * between devices.
 *
 * This exists so the pipeline (STT -> Protobuf -> transmit -> TTS) can stay identical
 * regardless of the radio underneath it. Implementations differ by orders of magnitude
 * in both range and throughput:
 *
 * | Implementation        | MTU     | Throughput | Typical range |
 * | --------------------- | ------- | ---------- | ------------- |
 * | SocketTransport (TCP) | 64 KB   | ~50 Mbps   | 100-200 m     |
 * | BluetoothRFCOMM       | ~1 KB   | ~1 Mbps    | 10-30 m       |
 * | AfskRadioLink (VHF)   | 256 B   | 1200 baud  | 5-15 km       |
 *
 * Implementations MUST reject a frame larger than [mtu] rather than truncating it — a
 * silently clipped frame fails its checksum on the far side and looks identical to a bad
 * radio link, which is painful to diagnose in the field.
 *
 * This is deliberately separate from [NetworkCallbacks], which is frozen post-Sprint 1 and
 * speaks in decoded [com.itantra.domain.model.TransceiverMessage] terms. A MeshLink sits one
 * layer below: it moves opaque bytes and knows nothing about Protobuf.
 */
interface MeshLink {

    /** Short human-readable name for UI and logs, e.g. "VHF/AFSK1200". */
    val name: String

    /** Largest payload this link can carry in a single frame, in bytes. */
    val mtu: Int

    /** True once [start] has succeeded and the link is able to send and receive. */
    val isRunning: Boolean

    /**
     * Decoded, integrity-checked payloads arriving on this link.
     *
     * Only frames that passed their checksum are emitted. Corrupt frames are dropped
     * silently, which on a noisy radio channel is routine rather than exceptional.
     */
    val incoming: SharedFlow<ByteArray>

    /** Acquire hardware and begin receiving. Idempotent. */
    fun start(): AppResult<Unit>

    /** Release hardware. Idempotent, and safe to call from any thread. */
    fun stop()

    /**
     * Transmit one frame, suspending until the payload has physically left the device.
     *
     * On a half-duplex link this blocks for the full airtime of the frame (roughly one
     * second for a 60-byte payload at 1200 baud) and receive is deaf for that period.
     */
    suspend fun send(frame: ByteArray): AppResult<Unit>
}
