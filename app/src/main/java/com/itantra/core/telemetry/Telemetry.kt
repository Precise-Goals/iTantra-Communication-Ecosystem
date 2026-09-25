package com.itantra.core.telemetry

import android.content.Context
import android.util.Log
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Per-utterance timing record for the PS-26173 Latency criterion.
 *
 * The rubric asks for four numbers this codebase could not previously produce:
 *   1. delay between words said and STT completion
 *   2. delay between text received and audio played
 *   3. RTF (real time factor)
 *   4. sentence said on phone A -> same sentence starts as audio on phone B
 *
 * All timestamps are System.nanoTime() on the device that recorded them, except
 * [peerClockOffsetMs] which converts phone A's wall clock to phone B's.
 *
 * Deliberately NOT routed through AudioCallbacks/NetworkCallbacks: both are frozen
 * post-Sprint-1 contracts.
 */
object Telemetry {

    private const val TAG = "Telemetry"

    /** Monotonic id so send-side and receive-side rows can be joined. */
    private val nextId = AtomicLong(1)

    data class Utterance(
        val id: Long,
        var lang: String = "",
        /** Wire sequence of the TransceiverMessage this row belongs to — set on send once the
         *  message's own sequence is assigned, and on receive from the decoded message (T11). */
        var sequence: Int = 0,
        /** Device UUID that originated the message (this device on send, message.senderId on
         *  receive) — the other half of the (sender, sequence) join key (T11). */
        var senderId: String = "",
        // ── send path ──
        var captureEndNs: Long = 0,
        /** When STT processing actually started: after any queue wait and model load (T71). */
        var sttStartNs: Long = 0,
        var featureDoneNs: Long = 0,
        var inferDoneNs: Long = 0,
        var txNs: Long = 0,
        var audioDurationMs: Long = 0,
        var charCount: Int = 0,
        // ── receive path ──
        var rxNs: Long = 0,
        var ttsDoneNs: Long = 0,
        var firstAudioFrameNs: Long = 0,
        var ttsAudioDurationMs: Long = 0
    ) {
        /** Rubric metric 1, milliseconds. */
        val sttLatencyMs: Double get() = ns(captureEndNs, inferDoneNs)
        /** Rubric metric 2, milliseconds. */
        val ttsLatencyMs: Double get() = ns(rxNs, firstAudioFrameNs)
        /** Feature extraction only, milliseconds. */
        val featureMs: Double get() = ns(procStartNs, featureDoneNs)
        /** Queue wait + model load before STT processing began, milliseconds (T71). */
        val waitMs: Double get() = ns(captureEndNs, sttStartNs)
        /** Receive -> synthesis finished, milliseconds. Excludes playback-queue wait, which
         *  ttsLatencyMs includes (T71). */
        val ttsSynthMs: Double get() = ns(rxNs, ttsDoneNs)
        /** Start of processing: sttStartNs when recorded, else the older captureEndNs stamp. */
        private val procStartNs: Long get() = if (sttStartNs != 0L) sttStartNs else captureEndNs
        /** ONNX session only, milliseconds. */
        val inferMs: Double get() = ns(featureDoneNs, inferDoneNs)
        /** Rubric metric 3. Below 1.0 means faster than real time. */
        val rtf: Double get() =
            // Processing time only: queue wait and model load are not the model's speed (T71).
            if (audioDurationMs <= 0) 0.0 else (ns(procStartNs, inferDoneNs) / audioDurationMs)
        /** Rubric metric 4 (send side), wall-clock epoch ms of the VAD cut. 0 until captureEndNs
         *  is stamped (T11). */
        val speechEndEpochMs: Long get() = if (captureEndNs == 0L) 0L else Telemetry.nsToEpochMs(captureEndNs)
        /** Rubric metric 4 (receive side), wall-clock epoch ms of the first played audio frame.
         *  0 until firstAudioFrameNs is stamped (T11). */
        val firstAudioEpochMs: Long get() = if (firstAudioFrameNs == 0L) 0L else Telemetry.nsToEpochMs(firstAudioFrameNs)

        private fun ns(a: Long, b: Long): Double =
            if (a == 0L || b == 0L || b < a) 0.0 else (b - a) / 1_000_000.0
    }

    private val open = ConcurrentHashMap<Long, Utterance>()

    /** Rolling median input for the UI. Bounded; oldest dropped. */
    private val recentRtf = ArrayDeque<Double>()

    /**
     * Clock offset to the peer, milliseconds: offset(peer − us). Derived via an NTP-style
     * exchange in SocketTransport (t0 = our send time, t1 = peer's clock on its ACK, t2 = our
     * receive time): offset = t1 − (t0 + t2) / 2, rtt = t2 − t0 (T11). Zero until measured.
     * Add offset to our epoch reading to express it on the peer's clock.
     */
    @Volatile var peerClockOffsetMs: Long = 0

    /** Round-trip time to the peer, milliseconds, from the same exchange as [peerClockOffsetMs]. */
    @Volatile var peerRttMs: Long = 0

    /**
     * NTP-style offset/RTT from one ping/ACK round trip. t0/t2 are on our clock (send, receive);
     * t1 is the peer's clock, as carried by its ACK. Pulled out of SocketTransport so the formula
     * has a single, unit-tested definition (T11).
     */
    fun computeOffsetAndRtt(t0: Long, t1: Long, t2: Long): Pair<Long, Long> =
        (t1 - (t0 + t2) / 2) to (t2 - t0)

    /**
     * Converts a monotonic [System.nanoTime] reading into a wall-clock epoch-ms estimate, by
     * pairing a fresh nanoTime/currentTimeMillis reading now and projecting backwards. Accurate
     * to within the gap between when [monotonicNs] was captured and when this is called (T11).
     */
    fun nsToEpochMs(monotonicNs: Long): Long {
        val nowNs = System.nanoTime()
        val nowEpoch = System.currentTimeMillis()
        return nowEpoch - (nowNs - monotonicNs) / 1_000_000
    }

    fun begin(lang: String): Utterance {
        val u = Utterance(id = nextId.getAndIncrement(), lang = lang)
        open[u.id] = u
        return u
    }

    fun get(id: Long): Utterance? = open[id]

    /** Median of the last 20 RTF values, for display. 0.0 if none yet. */
    fun medianRtf(): Double = synchronized(recentRtf) {
        if (recentRtf.isEmpty()) return 0.0
        val sorted = recentRtf.sorted()
        sorted[sorted.size / 2]
    }

    /** Close the record, log it, append a CSV row. */
    fun complete(context: Context, u: Utterance) {
        open.remove(u.id)
        if (u.rtf > 0) synchronized(recentRtf) {
            recentRtf.addLast(u.rtf)
            while (recentRtf.size > 20) recentRtf.removeFirst()
        }
        Log.d(TAG, "utt=${u.id} lang=${u.lang} rtf=%.3f stt=%.0fms wait=%.0fms feat=%.0fms infer=%.0fms tts=%.0fms synth=%.0fms"
            .format(u.rtf, u.sttLatencyMs, u.waitMs, u.featureMs, u.inferMs, u.ttsLatencyMs, u.ttsSynthMs))
        appendCsv(context, u)
    }

    private fun appendCsv(context: Context, u: Utterance) {
        try {
            val f = File(context.filesDir, "telemetry.csv")
            if (!f.exists()) {
                f.appendText(
                    "id,lang,audio_ms,chars,stt_ms,wait_ms,feature_ms,infer_ms,rtf,tts_ms,tts_synth_ms,tts_audio_ms," +
                        "sequence,sender_id,speech_end_epoch_ms,first_audio_epoch_ms,peer_offset_ms,peer_rtt_ms\n"
                )
            }
            f.appendText(
                "%d,%s,%d,%d,%.1f,%.1f,%.1f,%.1f,%.4f,%.1f,%.1f,%d,%d,%s,%d,%d,%d,%d\n".format(
                    u.id, u.lang, u.audioDurationMs, u.charCount,
                    u.sttLatencyMs, u.waitMs, u.featureMs, u.inferMs, u.rtf,
                    u.ttsLatencyMs, u.ttsSynthMs, u.ttsAudioDurationMs,
                    u.sequence, u.senderId, u.speechEndEpochMs, u.firstAudioEpochMs,
                    peerClockOffsetMs, peerRttMs
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "telemetry csv write failed: ${e.message}")
        }
    }
}
