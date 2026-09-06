package com.itantra.core.radio

/**
 * HDLC-style framing for the AFSK radio link, following AX.25 / APRS conventions.
 *
 * A radio channel gives us no packet boundaries, no addressing and no integrity guarantee —
 * just a continuous stream of noisy bits, most of which are not ours. This layer supplies all
 * three:
 *
 * ```
 * [preamble flags][FLAG] payload + FCS (bit-stuffed) [FLAG][trailer flags]
 * ```
 *
 * Four mechanisms, each solving a specific problem:
 *
 * 1. **Flags (0x7E)** delimit frames. The byte 0x7E sent LSB-first is `0 1 1 1 1 1 1 0` — six
 *    consecutive 1 bits, a pattern made unique by bit stuffing.
 * 2. **Bit stuffing** inserts a 0 after any five consecutive 1s in the payload, so payload data
 *    can never counterfeit a flag. The receiver removes it.
 * 3. **NRZI** encodes a 0 bit as a level *transition* and a 1 as no transition. This guarantees
 *    frequent transitions for the demodulator's clock recovery (a long run of identical bits
 *    would otherwise starve the PLL) and makes the link immune to inverted audio polarity,
 *    which varies between radios and cables.
 * 4. **CRC-16-CCITT (X.25 FCS)** catches the bit errors that a weak-signal radio link will
 *    produce constantly. Without it, noise decodes as plausible-looking garbage text and gets
 *    handed to TTS.
 *
 * All functions here are pure Kotlin with no Android dependencies, so the whole framing layer
 * is unit-testable on the JVM — see `HdlcFramerUnitTest`.
 */
object HdlcFramer {

    /** Frame delimiter byte. LSB-first on air this is `0 1 1 1 1 1 1 0`. */
    const val FLAG: Int = 0x7E

    /**
     * Reflected CRC-16-CCITT polynomial (0x1021 bit-reversed), as used by X.25 and AX.25.
     * Reflected form lets us shift right and match the LSB-first bit order used on air.
     */
    private const val CRC_POLYNOMIAL = 0x8408

    private const val CRC_INIT = 0xFFFF

    /**
     * Flags sent before the opening flag, to give a VOX-keyed transmitter time to rise.
     *
     * A VOX circuit needs roughly 200-400 ms of audio before it has keyed the carrier, and
     * anything transmitted during that window is lost. At 1200 baud one flag is 8 bits =
     * 6.67 ms, so 48 flags buys ~320 ms of throwaway preamble. Reduce this to ~8 when keying
     * PTT over a hardware cable, where there is no attack delay to cover.
     */
    const val DEFAULT_PREAMBLE_FLAGS: Int = 48

    /** Flags sent after the closing flag, so the transmitter does not drop mid-checksum. */
    const val DEFAULT_TRAILER_FLAGS: Int = 4

    /**
     * Largest payload accepted in one frame.
     *
     * This is a deliberate airtime limit rather than a protocol one: at 1200 baud, 256 bytes
     * already occupies ~1.8 seconds of a shared half-duplex channel. iTantra's compressed
     * Protobuf frames are 20-60 bytes, so this leaves generous headroom.
     */
    const val MAX_PAYLOAD_BYTES: Int = 256

    /** Number of bytes of frame check sequence appended to every payload. */
    const val FCS_BYTES: Int = 2

    /**
     * Compute the X.25 frame check sequence over [data].
     *
     * Initialised to all ones and finally complemented, so that trailing zero bytes and a
     * dropped carrier are both detected rather than producing a valid-looking zero CRC.
     */
    fun fcs(data: ByteArray): Int {
        var crc = CRC_INIT
        for (byte in data) {
            crc = crc xor (byte.toInt() and 0xFF)
            repeat(8) {
                crc = if (crc and 1 != 0) {
                    (crc ushr 1) xor CRC_POLYNOMIAL
                } else {
                    crc ushr 1
                }
            }
        }
        return crc.inv() and 0xFFFF
    }

    /**
     * Encode [payload] into the on-air bit stream, ready to hand to
     * [AfskModem.modulate].
     *
     * The returned bits are already NRZI-encoded, so they represent physical tone choices
     * rather than logical data bits.
     *
     * @throws IllegalArgumentException if [payload] exceeds [MAX_PAYLOAD_BYTES].
     */
    fun encode(
        payload: ByteArray,
        preambleFlags: Int = DEFAULT_PREAMBLE_FLAGS,
        trailerFlags: Int = DEFAULT_TRAILER_FLAGS
    ): BooleanArray {
        require(payload.size <= MAX_PAYLOAD_BYTES) {
            "Payload ${payload.size} B exceeds MAX_PAYLOAD_BYTES ($MAX_PAYLOAD_BYTES)"
        }

        val checksum = fcs(payload)
        // FCS is transmitted low byte first, matching the LSB-first bit order on air.
        val body = payload + byteArrayOf(
            (checksum and 0xFF).toByte(),
            ((checksum ushr 8) and 0xFF).toByte()
        )

        val bits = ArrayList<Boolean>(
            (preambleFlags + trailerFlags + 1) * 8 + body.size * 9 + 8
        )

        repeat(preambleFlags) { appendFlag(bits) }
        appendStuffed(bits, body)
        appendFlag(bits)
        repeat(trailerFlags) { appendFlag(bits) }

        return nrziEncode(bits.toBooleanArray())
    }

    /** Append one unstuffed flag byte, LSB-first. */
    private fun appendFlag(bits: MutableList<Boolean>) {
        for (i in 0 until 8) {
            bits.add((FLAG ushr i) and 1 == 1)
        }
    }

    /** Append [body] LSB-first, inserting a 0 bit after every five consecutive 1s. */
    private fun appendStuffed(bits: MutableList<Boolean>, body: ByteArray) {
        var consecutiveOnes = 0
        for (byte in body) {
            val value = byte.toInt() and 0xFF
            for (i in 0 until 8) {
                val bit = (value ushr i) and 1 == 1
                bits.add(bit)
                if (bit) {
                    consecutiveOnes++
                    if (consecutiveOnes == 5) {
                        bits.add(false)
                        consecutiveOnes = 0
                    }
                } else {
                    consecutiveOnes = 0
                }
            }
        }
    }

    /**
     * NRZI-encode a logical bit stream: a 0 bit flips the output level, a 1 leaves it alone.
     */
    fun nrziEncode(bits: BooleanArray): BooleanArray {
        val out = BooleanArray(bits.size)
        var level = false
        for (i in bits.indices) {
            if (!bits[i]) level = !level
            out[i] = level
        }
        return out
    }

    /**
     * Convenience wrapper that runs a complete bit stream through a [HdlcDecoder] and returns
     * every valid frame found. Intended for tests and offline analysis; live receive should
     * feed [HdlcDecoder] incrementally instead.
     */
    fun decodeAll(onAirBits: BooleanArray): List<ByteArray> {
        val frames = mutableListOf<ByteArray>()
        val decoder = HdlcDecoder { frames.add(it) }
        decoder.push(onAirBits)
        return frames
    }
}

/**
 * Streaming HDLC receiver.
 *
 * Fed one on-air bit at a time, it undoes NRZI, locates flag-delimited segments, removes bit
 * stuffing, reassembles bytes and verifies the checksum, invoking [onFrame] for each payload
 * that passes. Frames failing CRC are discarded without notification — on a weak radio link
 * that is the normal case, not an error worth surfacing.
 *
 * Not thread-safe; feed it from a single reader coroutine.
 */
class HdlcDecoder(private val onFrame: (ByteArray) -> Unit) {

    /** Rolling window of the last 8 decoded bits, earliest bit in the most significant place. */
    private var flagWindow = 0

    /** Logical bits accumulated since the last flag, still bit-stuffed. */
    private val segment = ArrayList<Boolean>(HdlcFramer.MAX_PAYLOAD_BYTES * 9)

    /** Previous NRZI line level, used to recover the logical bit from a transition. */
    private var lastLevel = false

    /** False until the first bit arrives, so we do not invent a transition at startup. */
    private var primed = false

    /**
     * Longest segment tolerated before assumed to be noise rather than a frame.
     *
     * Without this, a channel carrying no flags accumulates unboundedly — a real risk on an
     * open squelch listening to static for hours.
     */
    private val maxSegmentBits =
        (HdlcFramer.MAX_PAYLOAD_BYTES + HdlcFramer.FCS_BYTES) * 9 + 16

    fun reset() {
        flagWindow = 0
        segment.clear()
        lastLevel = false
        primed = false
    }

    /** Feed a block of on-air bits. */
    fun push(bits: BooleanArray) {
        for (bit in bits) push(bit)
    }

    /** Feed a single on-air (NRZI line level) bit. */
    fun push(level: Boolean) {
        // NRZI: no transition means a logical 1, a transition means a logical 0.
        val bit = if (!primed) {
            primed = true
            lastLevel = level
            // The very first bit has no predecessor to compare against. Treat it as a 1; any
            // resulting error is confined to the preamble, which carries no payload.
            true
        } else {
            val decoded = (level == lastLevel)
            lastLevel = level
            decoded
        }

        segment.add(bit)
        flagWindow = ((flagWindow shl 1) or (if (bit) 1 else 0)) and 0xFF

        // 0x7E as an LSB-first bit sequence is 0,1,1,1,1,1,1,0. Shifting left with the
        // earliest-received bit in the high position makes that sequence read as 0x7E here.
        if (flagWindow == HdlcFramer.FLAG) {
            // Drop the eight flag bits themselves; what precedes them is the frame body.
            val bodyBits = segment.subList(0, maxOf(0, segment.size - 8))
            if (bodyBits.isNotEmpty()) {
                finishSegment(bodyBits.toBooleanArray())
            }
            segment.clear()
            return
        }

        if (segment.size > maxSegmentBits) {
            // No flag for longer than a maximum-length frame: this is static, not a packet.
            segment.clear()
        }
    }

    /** Destuff, reassemble and checksum one flag-delimited segment. */
    private fun finishSegment(stuffed: BooleanArray) {
        val destuffed = destuff(stuffed) ?: return

        // A frame must carry at least one payload byte plus the two FCS bytes.
        val byteCount = destuffed.size / 8
        if (byteCount <= HdlcFramer.FCS_BYTES) return

        val bytes = ByteArray(byteCount)
        for (i in 0 until byteCount * 8) {
            if (destuffed[i]) {
                bytes[i / 8] = (bytes[i / 8].toInt() or (1 shl (i % 8))).toByte()
            }
        }

        val payload = bytes.copyOfRange(0, byteCount - HdlcFramer.FCS_BYTES)
        val received = (bytes[byteCount - 2].toInt() and 0xFF) or
            ((bytes[byteCount - 1].toInt() and 0xFF) shl 8)

        if (HdlcFramer.fcs(payload) == received) {
            onFrame(payload)
        }
    }

    /**
     * Remove stuffed zero bits. Returns null if the segment contains six consecutive 1s
     * outside a flag, which means the bit stream is corrupt rather than merely noisy.
     */
    private fun destuff(bits: BooleanArray): BooleanArray? {
        val out = ArrayList<Boolean>(bits.size)
        var consecutiveOnes = 0
        var i = 0
        while (i < bits.size) {
            val bit = bits[i]
            if (consecutiveOnes == 5) {
                // The transmitter guarantees a 0 here. A 1 means we lost sync.
                if (bit) return null
                consecutiveOnes = 0
                i++
                continue
            }
            out.add(bit)
            consecutiveOnes = if (bit) consecutiveOnes + 1 else 0
            i++
        }
        return out.toBooleanArray()
    }
}
