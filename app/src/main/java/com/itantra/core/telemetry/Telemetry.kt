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
        // ── send path ──
        var captureEndNs: Long = 0,
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
        val featureMs: Double get() = ns(captureEndNs, featureDoneNs)
        /** ONNX session only, milliseconds. */
        val inferMs: Double get() = ns(featureDoneNs, inferDoneNs)
        /** Rubric metric 3. Below 1.0 means faster than real time. */
        val rtf: Double get() =
            if (audioDurationMs <= 0) 0.0 else (sttLatencyMs / audioDurationMs)

        private fun ns(a: Long, b: Long): Double =
            if (a == 0L || b == 0L || b < a) 0.0 else (b - a) / 1_000_000.0
    }

    private val open = ConcurrentHashMap<Long, Utterance>()

    /** Rolling median input for the UI. Bounded; oldest dropped. */
    private val recentRtf = ArrayDeque<Double>()

    /**
     * Clock offset to the peer, milliseconds: peerWallClock + offset = ourWallClock.
     * Estimated as RTT/2 from the SocketTransport ping loop. Zero until measured.
     */
    @Volatile var peerClockOffsetMs: Long = 0

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
        Log.d(TAG, "utt=${u.id} lang=${u.lang} rtf=%.3f stt=%.0fms feat=%.0fms infer=%.0fms tts=%.0fms"
            .format(u.rtf, u.sttLatencyMs, u.featureMs, u.inferMs, u.ttsLatencyMs))
        appendCsv(context, u)
    }

    private fun appendCsv(context: Context, u: Utterance) {
        try {
            val f = File(context.filesDir, "telemetry.csv")
            if (!f.exists()) {
                f.appendText("id,lang,audio_ms,chars,stt_ms,feature_ms,infer_ms,rtf,tts_ms,tts_audio_ms\n")
            }
            f.appendText(
                "%d,%s,%d,%d,%.1f,%.1f,%.1f,%.4f,%.1f,%d\n".format(
                    u.id, u.lang, u.audioDurationMs, u.charCount,
                    u.sttLatencyMs, u.featureMs, u.inferMs, u.rtf,
                    u.ttsLatencyMs, u.ttsAudioDurationMs
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "telemetry csv write failed: ${e.message}")
        }
    }
}
