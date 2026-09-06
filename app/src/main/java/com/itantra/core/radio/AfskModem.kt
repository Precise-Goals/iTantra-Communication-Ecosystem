package com.itantra.core.radio

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Bell 202 AFSK modem — 1200 baud, 1200 Hz mark / 2200 Hz space.
 *
 * This is the layer that turns bytes into sound and back. Because both tones sit inside the
 * 300-3000 Hz voice passband, an ordinary FM handheld transceiver carries them as if they were
 * speech: no radio firmware change, no data port, no modification of any kind. The radio never
 * knows it is carrying data. This is the same technique APRS has used over VHF since the 1980s.
 *
 * Everything here is pure Kotlin operating on [FloatArray] PCM at [SAMPLE_RATE], which is the
 * same 16 kHz mono format `AudioCaptureModule` already captures and `AudioPlaybackManager`
 * already plays. That makes the modem unit-testable on the JVM with no device or radio present
 * — see `AfskModemUnitTest`, which round-trips real payloads through added Gaussian noise.
 *
 * At 1200 baud a 60-byte payload occupies roughly 0.9 s of airtime including preamble, which is
 * why compressing iTantra's Protobuf frames matters so much on this link.
 */
object AfskModem {

    /** Matches the rest of the iTantra audio pipeline, so no resampling is ever needed. */
    const val SAMPLE_RATE: Int = 16000

    const val BAUD: Double = 1200.0

    /** Binary 1. */
    const val MARK_HZ: Double = 1200.0

    /** Binary 0. */
    const val SPACE_HZ: Double = 2200.0

    /**
     * 13.333 samples per symbol at 16 kHz.
     *
     * Deliberately fractional: forcing an integer would require resampling the whole pipeline
     * away from 16 kHz. Both the modulator and the demodulator carry the fraction in an
     * accumulator instead, so timing error never accumulates across a frame.
     */
    const val SAMPLES_PER_SYMBOL: Double = SAMPLE_RATE / BAUD

    /**
     * Default output amplitude.
     *
     * Deliberately below full scale. Radio microphone inputs clip and distort easily, and an
     * over-driven AFSK signal loses far more packets than a quiet one — the demodulator
     * compares relative tone energy, so it does not need a loud input.
     */
    const val DEFAULT_AMPLITUDE: Float = 0.6f

    /**
     * Modulate a bit stream into continuous-phase FSK audio.
     *
     * Phase is carried across symbol boundaries rather than restarted per symbol. A phase
     * discontinuity would splatter energy across the spectrum, wasting transmit power and
     * bleeding into adjacent channels — and the sharp click is exactly what a receiving
     * correlator mistakes for a bit transition.
     */
    fun modulate(bits: BooleanArray, amplitude: Float = DEFAULT_AMPLITUDE): FloatArray {
        if (bits.isEmpty()) return FloatArray(0)

        val out = FloatArray(ceil(bits.size * SAMPLES_PER_SYMBOL).toInt())
        val twoPi = 2.0 * PI
        var phase = 0.0
        var n = 0

        for (i in bits.indices) {
            val frequency = if (bits[i]) MARK_HZ else SPACE_HZ
            val phaseStep = twoPi * frequency / SAMPLE_RATE
            // Absolute end position keeps the fractional symbol length exact over long frames.
            val symbolEnd = (i + 1) * SAMPLES_PER_SYMBOL
            while (n < symbolEnd && n < out.size) {
                out[n] = (amplitude * sin(phase)).toFloat()
                phase += phaseStep
                if (phase >= twoPi) phase -= twoPi
                n++
            }
        }
        return out
    }

    /** Airtime in milliseconds for a given number of on-air bits. */
    fun airtimeMs(bitCount: Int): Long = (bitCount * 1000.0 / BAUD).toLong()
}

/**
 * Streaming AFSK demodulator: PCM samples in, on-air bits out.
 *
 * Uses a non-coherent correlator — the standard approach for AFSK1200 and the reason this works
 * over an FM voice channel at all. For every incoming sample it correlates the last symbol's
 * worth of audio against both tones in quadrature and compares their magnitudes:
 *
 * ```
 * mark  = sqrt(I_1200^2 + Q_1200^2)
 * space = sqrt(I_2200^2 + Q_2200^2)
 * bit   = (mark - space) > 0
 * ```
 *
 * Taking magnitude from both quadrature components makes the decision independent of the
 * incoming signal's phase, which matters because nothing synchronises the two radios' carriers
 * and the audio path inverts polarity on some handsets and cables.
 *
 * Not thread-safe; feed from a single reader coroutine.
 */
class AfskDemodulator(private val onBit: (Boolean) -> Unit) {

    private val windowSize = Math.round(AfskModem.SAMPLES_PER_SYMBOL).toInt()

    // Quadrature reference tones, precomputed once for the correlator window.
    private val markCos = DoubleArray(windowSize)
    private val markSin = DoubleArray(windowSize)
    private val spaceCos = DoubleArray(windowSize)
    private val spaceSin = DoubleArray(windowSize)

    /** Circular buffer holding the most recent [windowSize] samples. */
    private val window = DoubleArray(windowSize)
    private var writeIndex = 0
    private var samplesSeen = 0L

    /** Low-pass state for the mark-minus-space decision variable. */
    private var filtered = 0.0

    /** Symbol-timing accumulator, in samples. */
    private var symbolPhase = 0.0
    private var lastSign = false
    private var hasLastSign = false

    /**
     * Smoothing applied to the decision variable before slicing.
     *
     * The correlator output ripples at the tone difference frequency; without smoothing that
     * ripple crosses zero mid-symbol and the clock recovery chases it. Higher values track
     * faster but pass more noise.
     */
    private val decisionSmoothing = 0.28

    init {
        val twoPi = 2.0 * PI
        for (k in 0 until windowSize) {
            val markAngle = twoPi * AfskModem.MARK_HZ * k / AfskModem.SAMPLE_RATE
            val spaceAngle = twoPi * AfskModem.SPACE_HZ * k / AfskModem.SAMPLE_RATE
            markCos[k] = cos(markAngle)
            markSin[k] = sin(markAngle)
            spaceCos[k] = cos(spaceAngle)
            spaceSin[k] = sin(spaceAngle)
        }
    }

    fun reset() {
        window.fill(0.0)
        writeIndex = 0
        samplesSeen = 0
        filtered = 0.0
        symbolPhase = 0.0
        lastSign = false
        hasLastSign = false
    }

    /** Feed a block of PCM samples at [AfskModem.SAMPLE_RATE]. */
    fun push(samples: FloatArray) {
        for (sample in samples) push(sample)
    }

    /** Feed one PCM sample. */
    fun push(sample: Float) {
        window[writeIndex] = sample.toDouble()
        writeIndex = (writeIndex + 1) % windowSize
        samplesSeen++

        // Wait until the correlator window is full, or the first symbol decodes from silence.
        if (samplesSeen < windowSize) return

        var markI = 0.0
        var markQ = 0.0
        var spaceI = 0.0
        var spaceQ = 0.0

        // writeIndex now points at the oldest sample in the circular buffer.
        for (k in 0 until windowSize) {
            val value = window[(writeIndex + k) % windowSize]
            markI += value * markCos[k]
            markQ += value * markSin[k]
            spaceI += value * spaceCos[k]
            spaceQ += value * spaceSin[k]
        }

        val markMagnitude = sqrt(markI * markI + markQ * markQ)
        val spaceMagnitude = sqrt(spaceI * spaceI + spaceQ * spaceQ)
        val decision = markMagnitude - spaceMagnitude

        filtered += (decision - filtered) * decisionSmoothing
        val sign = filtered > 0.0

        // Clock recovery. Every level transition marks a symbol boundary, so we re-centre the
        // sampling instant half a symbol later — the point of maximum eye opening. Between
        // transitions the accumulator free-runs, which is why NRZI's guaranteed transition
        // density matters: without it a long run of identical bits would let timing drift.
        if (hasLastSign && sign != lastSign) {
            symbolPhase = AfskModem.SAMPLES_PER_SYMBOL / 2.0
        }
        lastSign = sign
        hasLastSign = true

        symbolPhase += 1.0
        if (symbolPhase >= AfskModem.SAMPLES_PER_SYMBOL) {
            symbolPhase -= AfskModem.SAMPLES_PER_SYMBOL
            onBit(sign)
        }
    }
}
