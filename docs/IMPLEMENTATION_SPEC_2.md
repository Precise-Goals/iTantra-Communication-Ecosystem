# iTantra — Implementation Spec, Part 2

> Continues [`IMPLEMENTATION_SPEC.md`](IMPLEMENTATION_SPEC.md). **Read §0 of Part 1 first — the rules contract applies here unchanged.**
> Covers the tasks Part 1 left unspecified. As of 2026-09-22 · PS-26173
> **Revised 2026-09-23:** Group G adds T62–T69 from the second audit; T33 and T50 are amended.
> **Revised 2026-09-24:** T45 rewritten after the first device measurements; T70–T72 added at the end of Group G. T62 and T65 are implemented on `feature/latency-pipeline`.
> **Revised 2026-09-24 (evening):** T70, T45, T72, T71 implemented in PR #17. **Group H** (before Group F) adds T73 and T74 and replaces the T43 and T46 specs with versions anchored to PR #17's code. See [`IMPROVEMENT_PLAN.md` §10](IMPROVEMENT_PLAN.md#10-addendum--second-audit-2026-09-23).

---

## Additional rules for this file

Part 1's rules apply in full. Three more, specific to what is in here:

9. **`AudioCallbacks` and `NetworkCallbacks` are frozen.** `domain/contracts/AudioCallbacks.kt` line 8 says *"FROZEN POST SPRINT 1 — Do not modify signatures unilaterally."* Several tasks below would be easier if you added a parameter. **Do not.** The designs here deliberately route around those interfaces. If you think a task needs a signature change, stop and report.
10. **Tasks marked 🔬 require real data you must fetch, not invent.** A guessed config value or a made-up URL is worse than leaving the task undone. Where a task says "fetch", run the given command and use its output.
11. **Tasks marked 🎨 touch Compose UI whose current text is not quoted here** because it was not read when this spec was written. For those, open the file, find the existing equivalent pattern, and match it. Do not invent a new visual style.

---

# Group A — Instrumentation (T08–T12)

This group creates the measurement layer. **Do it before the optimisation tasks** so their effect is visible.

## T08–T12 · Telemetry

**New file:** `app/src/main/java/com/itantra/core/telemetry/Telemetry.kt`
**Criterion:** LAT / DOC

### Why a singleton rather than a callback

The natural design is to add timing parameters to `AudioCallbacks.onSTTResult`. **That interface is frozen.** A process-wide object that modules write to directly avoids touching it, and also lets the send and receive paths — which live in different classes — write into the same record.

### CREATE this file exactly

```kotlin
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
 * Deliberately NOT routed through AudioCallbacks: that interface is frozen post-Sprint 1.
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
```

### Wire the send path

**File:** `core/service/ITantraForegroundService.kt`

ANCHOR — the `onSpeechReady` lambda:

```kotlin
            onSpeechReady = { audioBuffer, lang ->
                sttModule.ensureLoaded(lang)
                sttModule.transcribe(audioBuffer, lang)
            }
```

REPLACEMENT:

```kotlin
            onSpeechReady = { audioBuffer, lang ->
                val utt = com.itantra.core.telemetry.Telemetry.begin(lang)
                utt.captureEndNs = System.nanoTime()
                utt.audioDurationMs = audioBuffer.size * 1000L / 16000L
                sttModule.ensureLoaded(lang)
                sttModule.currentUtterance = utt
                sttModule.transcribe(audioBuffer, lang)
                utt.inferDoneNs = System.nanoTime()
                com.itantra.core.telemetry.Telemetry.complete(this@ITantraForegroundService, utt)
            }
```

**File:** `core/audio/STTModule.kt`

Add a class member near the top of the class body:

```kotlin
    /** Set by the caller before transcribe(); used to stamp feature-extraction timing. */
    @Volatile var currentUtterance: com.itantra.core.telemetry.Telemetry.Utterance? = null
```

ANCHOR inside `transcribe`:

```kotlin
            val features = extractLogMelSpectrogram(audioBuffer)
            val numFrames = features.size / N_MELS
```

REPLACEMENT:

```kotlin
            val features = extractLogMelSpectrogram(audioBuffer)
            currentUtterance?.featureDoneNs = System.nanoTime()
            val numFrames = features.size / N_MELS
```

Also stamp the character count. ANCHOR:

```kotlin
                val confidence = estimateConfidence(logits[0])
```

REPLACEMENT:

```kotlin
                currentUtterance?.charCount = text.length
                val confidence = estimateConfidence(logits[0])
```

### Wire the receive path

**File:** `core/service/ITantraForegroundService.kt`, inside `onTextReceived`.

ANCHOR (this is the post-T13, post-T43 form — do those first):

```kotlin
                val synth = ttsModule.synthesize(message.text, targetLang)
                if (synth != null) {
                    audioPlayback.play(synth.samples, synth.sampleRate, isAlert)
                }
```

REPLACEMENT:

```kotlin
                val utt = com.itantra.core.telemetry.Telemetry.begin(targetLang)
                utt.rxNs = rxStampNs
                val synth = ttsModule.synthesize(message.text, targetLang)
                utt.ttsDoneNs = System.nanoTime()
                if (synth != null) {
                    utt.ttsAudioDurationMs =
                        synth.samples.size * 1000L / synth.sampleRate
                    audioPlayback.play(synth.samples, synth.sampleRate, isAlert) {
                        utt.firstAudioFrameNs = System.nanoTime()
                        com.itantra.core.telemetry.Telemetry
                            .complete(this@ITantraForegroundService, utt)
                    }
                }
```

Capture `rxStampNs` at the very top of `onTextReceived`, as its first statement:

```kotlin
            val rxStampNs = System.nanoTime()
```

### `firstAudioFrameNs` must be stamped at playback, not synthesis

**File:** `core/audio/AudioPlaybackManager.kt`

The rubric asks when audio was **played**, not when it was ready. Add an optional callback.

ANCHOR (post-T38 form):

```kotlin
    fun play(waveform: FloatArray, sampleRate: Int, isAlert: Boolean = false) {
        playbackQueue.trySend(PlaybackItem(waveform, sampleRate, isAlert))
    }
```

REPLACEMENT:

```kotlin
    fun play(
        waveform: FloatArray,
        sampleRate: Int,
        isAlert: Boolean = false,
        onFirstFrame: (() -> Unit)? = null
    ) {
        playbackQueue.trySend(PlaybackItem(waveform, sampleRate, isAlert, onFirstFrame))
    }
```

Add `val onFirstFrame: (() -> Unit)? = null` to the `PlaybackItem` data class, pass it into `playNormal`/`playAlert`, and invoke it **immediately after `track.play()` and before the first `track.write(...)`**.

### VERIFY

```bash
./gradlew :app:compileDebugKotlin
```

Then on device, send three utterances and pull the CSV:

```bash
adb shell run-as com.itantra.debug cat files/telemetry.csv
```

It must have a header row and three data rows with non-zero `rtf`.

### DO NOT

- Do not add parameters to `AudioCallbacks`. It is frozen.
- Do not stamp `firstAudioFrameNs` after `track.write(...)` returns — `WRITE_BLOCKING` returns only when playback has drained, which measures the wrong thing entirely.

---

## T11 · Peer clock offset

**File:** `core/network/SocketTransport.kt`
**Criterion:** LAT — needed for the cross-device number.

The ping loop already measures round-trip latency. ANCHOR:

```kotlin
                            val latency = System.currentTimeMillis() - message.timestamp
```

Directly after the existing `onLatencyMeasured` call in that branch, add:

```kotlin
                            // Offset ~= RTT/2. Lets phone B express phone A's send time on its
                            // own clock, which is what the cross-device latency metric needs
                            // without NTP or external timing gear.
                            com.itantra.core.telemetry.Telemetry.peerClockOffsetMs = latency / 2
```

### DO NOT

Do not attempt real clock synchronisation. RTT/2 is the standard approximation and is accurate enough at these magnitudes. State the method on the slide.

---

# Group B — Capture and VAD (T32–T35, T41, T51–T53)

## T32 · Adaptive noise floor with hysteresis

**File:** `core/audio/VADModule.kt`
**Criterion:** ACC

### ANCHOR — the entire energy branch

```kotlin
            var sumSq = 0.0
            for (sample in audioChunk) {
                sumSq += sample * sample
            }
            val rms = kotlin.math.sqrt(sumSq / audioChunk.size).toFloat()
            val prob = if (rms > 0.025f) 0.85f else 0.05f
            val isCurrentSpeech = prob >= SPEECH_THRESHOLD
```

### REPLACEMENT

```kotlin
            var sumSq = 0.0
            for (sample in audioChunk) {
                sumSq += sample * sample
            }
            val rms = kotlin.math.sqrt(sumSq / audioChunk.size).toFloat()

            // Adaptive floor with hysteresis. The previous fixed 0.025 threshold failed both
            // ways: in a noisy environment RMS never drops below it so the detector never
            // releases and every utterance hit the 30s cap; in a quiet room a soft speaker
            // never crossed it at all.
            if (!isSpeechActive) {
                // Track the floor only while we believe there is no speech, so speech energy
                // cannot drag the floor up after itself.
                noiseFloor = if (noiseFloor <= 0f) rms
                             else noiseFloor * (1f - FLOOR_ADAPT) + rms * FLOOR_ADAPT
            }
            val floor = noiseFloor.coerceAtLeast(MIN_FLOOR)
            val onThreshold = floor * ON_RATIO    // ~ +9 dB
            val offThreshold = floor * OFF_RATIO  // ~ +4 dB

            val isCurrentSpeech = if (isSpeechActive) rms > offThreshold else rms > onThreshold
            val prob = if (isCurrentSpeech) 0.85f else 0.05f
```

Add to the companion object:

```kotlin
        /** EMA rate for the noise floor. Slow enough to ignore a single loud chunk. */
        private const val FLOOR_ADAPT = 0.05f
        /** Absolute floor, so a silent room does not give a threshold of zero. */
        private const val MIN_FLOOR = 0.002f
        /** Rising edge at roughly +9 dB over the floor. */
        private const val ON_RATIO = 2.8f
        /** Falling edge at roughly +4 dB. Hysteresis stops chattering mid-word. */
        private const val OFF_RATIO = 1.6f
```

Add as a class member beside `isSpeechActive`:

```kotlin
    /** Rolling estimate of the background level, updated only while not in speech. */
    private var noiseFloor: Float = 0f
```

Add `noiseFloor = 0f` to the body of `resetState()`.

### VERIFY

On device, in a quiet room and then with background noise: speech must be detected in both, and the detector must release within ~1 s of the speaker stopping in both.

### DO NOT

Do not remove `SPEECH_THRESHOLD` — the neural branch still uses it.

---

## T33 + T34 · Capture source and effects

**File:** `core/audio/AudioCaptureModule.kt`
**Criterion:** ACC

### ANCHOR

```kotlin
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )
```

### REPLACEMENT

```kotlin
        audioRecord = AudioRecord(
            // VOICE_RECOGNITION is the source Android tunes for ASR. MIC applies processing
            // intended for human listeners that smears the spectrum the encoder relies on.
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )
```

Then, after the existing `audioRecord?.startRecording()` line, add:

```kotlin
        // Platform DSP where the device offers it. Both are no-ops on hardware without support.
        audioRecord?.audioSessionId?.let { sessionId ->
            runCatching {
                if (android.media.audiofx.NoiseSuppressor.isAvailable()) {
                    android.media.audiofx.NoiseSuppressor.create(sessionId)?.enabled = true
                }
                if (android.media.audiofx.AutomaticGainControl.isAvailable()) {
                    android.media.audiofx.AutomaticGainControl.create(sessionId)?.enabled = true
                }
            }.onFailure { Log.w(TAG, "audio effects unavailable: ${it.message}") }
        }
```

### DC offset removal

ANCHOR:

```kotlin
                // Normalize PCM Short → Float [-1.0, 1.0]
                val floatChunk = FloatArray(samplesRead) { i ->
                    rawBuffer[i].toFloat() / Short.MAX_VALUE
                }
```

REPLACEMENT:

```kotlin
                // Normalize PCM Short → Float [-1.0, 1.0], with a one-pole DC blocker.
                // A DC offset survives per-feature normalization and biases the lowest mel bins.
                val floatChunk = FloatArray(samplesRead)
                for (i in 0 until samplesRead) {
                    val x = rawBuffer[i].toFloat() / Short.MAX_VALUE
                    val y = x - dcPrevIn + DC_R * dcPrevOut
                    dcPrevIn = x
                    dcPrevOut = y
                    floatChunk[i] = y
                }
```

Add to the companion object:

```kotlin
        /** One-pole DC blocker coefficient. 0.995 at 16kHz ≈ 8Hz corner. */
        private const val DC_R = 0.995f
```

Add as class members:

```kotlin
    private var dcPrevIn = 0f
    private var dcPrevOut = 0f
```

Reset both to `0f` in `stopCapture()`.

### DO NOT

Do not attach `AcousticEchoCanceler` in this task. For PTT it can attenuate the near-end speaker.

> **Amended 2026-09-23.** The original note said "this is a push-to-talk radio, not a speakerphone". Once T37 wires phone mode, it **is** a speakerphone: the microphone stays open while received TTS plays on the loudspeaker, and the phone re-transmits what it hears. That is handled by the echo gate in **T63**, which must land with T37. `VOICE_RECOGNITION` stays correct for capture in both modes because T63 discards input during playback.

---

## T41 · Adaptive endpointing

**File:** `core/audio/AudioCaptureModule.kt`
**Criterion:** LAT

### ANCHOR

```kotlin
    private val silenceChunksForEndOfSpeech = (VADModule.SILENCE_DURATION_MS / 100).toInt() // 8 chunks
```

### REPLACEMENT

```kotlin
    // Endpoint sooner once enough speech has been captured to be confident it was a real
    // utterance. The flat 800ms wait was a hard floor under the "words said -> STT complete"
    // metric for every utterance. Short fragments keep the long window to avoid cutting off
    // a hesitant speaker mid-sentence.
    private val silenceChunksLong = (VADModule.SILENCE_DURATION_MS / 100).toInt()  // 8 = 800ms
    private val silenceChunksShort = 5                                             // 500ms
    private val confidentSpeechChunks = 8                                          // 800ms of speech

    private var speechChunkCount = 0

    private val silenceChunksForEndOfSpeech: Int
        get() = if (speechChunkCount >= confidentSpeechChunks) silenceChunksShort
                else silenceChunksLong
```

Increment `speechChunkCount` wherever a speech chunk is appended, and reset it to `0` everywhere `speechBuffer.clear()` is called — there are three such places (`startCapture`'s end-of-speech branch, `flushAndTranscribe`, `stopCapture`). **Find all three.**

### VERIFY

Measure `sttLatencyMs` from the telemetry CSV before and after. It should drop by roughly 300 ms on normal-length utterances.

---

## T51 + T52 + T53 · Idle power

**File:** `core/audio/AudioCaptureModule.kt`
**Criterion:** EFF — this is the "CPU usage during idle listening" metric, which applies in PHONE_MODE.

### T51 — stop allocating per chunk

Covered by the T34 replacement above if you hoist the array. Change:

```kotlin
                val floatChunk = FloatArray(samplesRead)
```

to reuse a preallocated member. Add as a class member:

```kotlin
    /** Reused across chunks. At 10 chunks/sec a fresh 1600-float array was ~64KB/s of garbage. */
    private val chunkScratch = FloatArray(CHUNK_SIZE)
```

and use `chunkScratch` in place of `floatChunk`, **but** you must then copy before appending to `speechBuffer`:

```kotlin
                            speechBuffer.add(chunkScratch.copyOf(samplesRead))
```

**This copy is mandatory.** `speechBuffer` retains its entries; appending the shared scratch array would give you N references to one buffer that keeps being overwritten.

### T52 — running total

ANCHOR (appears twice — in the speech branch and in the end-of-speech branch):

```kotlin
                        if (speechBuffer.sumOf { it.size } < MAX_SPEECH_BUFFER_SAMPLES) {
```

Maintain an `Int` counter alongside `speechBuffer`, incremented on append and zeroed on clear, and compare against that instead. Do the same for the `FloatArray(speechBuffer.sumOf { it.size })` allocations — there are two, in `startCapture` and `flushAndTranscribe`.

### T53 — fewer wakeups

ANCHOR:

```kotlin
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            .coerceAtLeast(CHUNK_SIZE * 2)
```

REPLACEMENT:

```kotlin
        // A deeper AudioRecord buffer lets the reader block longer per wakeup. At idle the
        // wakeup count dominates power draw, not the arithmetic inside the loop.
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            .coerceAtLeast(CHUNK_SIZE * 8)
```

### DO NOT

Do not change `CHUNK_SIZE`. VAD cadence and the 800 ms endpoint arithmetic both assume 100 ms chunks. Only the *AudioRecord* buffer depth changes.

### VERIFY

```bash
adb shell dumpsys cpuinfo | grep itantra
```

Measure over a 10-minute idle window in phone mode, before and after.

---

# Group C — Latency and compliance (T40, T42, T45, T46)

## T40 · Streaming TTS

**File:** `core/service/ITantraForegroundService.kt`
**Criterion:** LAT

### ANCHOR (post-T13/T43/telemetry form)

```kotlin
                val synth = ttsModule.synthesize(message.text, targetLang)
```

### REPLACEMENT

```kotlin
                // Synthesize and play clause by clause so the first audio starts while the
                // rest is still being generated. Previously the whole message was synthesized
                // before a single sample played, which is the bulk of receive-side latency.
                val segments = splitForSynthesis(message.text)
                var first = true
                for (segment in segments) {
                    val part = ttsModule.synthesize(segment, targetLang) ?: continue
                    if (first) { utt.ttsDoneNs = System.nanoTime(); first = false }
                    audioPlayback.play(part.samples, part.sampleRate, isAlert) {
                        if (utt.firstAudioFrameNs == 0L) {
                            utt.firstAudioFrameNs = System.nanoTime()
                        }
                    }
                }
                com.itantra.core.telemetry.Telemetry.complete(this@ITantraForegroundService, utt)
```

Add this private function to the service:

```kotlin
    /**
     * Split on sentence and clause boundaries for streaming synthesis.
     *
     * Includes the Devanagari danda (।) and double danda (॥), which are the sentence
     * terminators in Hindi and Marathi — splitting on ASCII punctuation alone would treat a
     * whole Hindi paragraph as one segment and defeat the purpose.
     *
     * Segments shorter than MIN_SEGMENT_CHARS are merged forward: a one-word segment costs
     * more in per-call model overhead than it saves in latency.
     */
    private fun splitForSynthesis(text: String): List<String> {
        val parts = text.split(Regex("(?<=[।॥.!?])\\s+")).filter { it.isNotBlank() }
        if (parts.size <= 1) return listOf(text)
        val out = mutableListOf<String>()
        var buf = StringBuilder()
        for (p in parts) {
            buf.append(if (buf.isEmpty()) "" else " ").append(p)
            if (buf.length >= MIN_SEGMENT_CHARS) { out.add(buf.toString()); buf = StringBuilder() }
        }
        if (buf.isNotEmpty()) out.add(buf.toString())
        return out
    }
```

Add to the service's companion object:

```kotlin
        private const val MIN_SEGMENT_CHARS = 40
```

### DO NOT

Do not split ALERT messages. An alert must play as one uninterrupted unit — guard with `if (isAlert) listOf(message.text) else splitForSynthesis(message.text)`.

---

## T42 · Sentence formation

**File:** `core/audio/CtcDecoder.kt` or a new `core/audio/TextPostProcessor.kt`
**Criterion:** REQ — the PS says the STT module should *"form the sentences detected"*.

CTC output is an unpunctuated, uncapitalised token stream. Create:

```kotlin
package com.itantra.core.audio

/**
 * Turns raw CTC output into a sentence.
 *
 * The problem statement asks the STT module to "form the sentences detected", and a CTC
 * decode produces neither punctuation nor capitalisation. This is deliberately a rule-based
 * pass, not a model: a punctuation-restoration model would cost more RAM and latency than
 * the whole rest of the pipeline, against a criterion that scores both.
 */
object TextPostProcessor {

    /** Scripts that use the danda rather than a full stop. */
    private val DANDA_LANGS = setOf("hi", "mr", "bn", "gu", "or")

    fun finish(raw: String, languageCode: String): String {
        var s = raw.trim().replace(Regex("\\s+"), " ")
        if (s.isEmpty()) return s

        val terminator = if (languageCode in DANDA_LANGS) "।" else "."
        if (s.last() !in charArrayOf('.', '!', '?', '।', '॥')) s += terminator

        // Latin scripts capitalise; Indic scripts have no case, so leave them untouched.
        if (languageCode == "en") s = s.replaceFirstChar { it.uppercase() }
        return s
    }
}
```

Call it in `STTModule.transcribe`. ANCHOR:

```kotlin
            val text = CtcDecoder.greedyDecode(logits[0], vocab, blankId = vocab.size - 1)
```

REPLACEMENT:

```kotlin
            val decoded = CtcDecoder.greedyDecode(logits[0], vocab, blankId = vocab.size - 1)
            val text = TextPostProcessor.finish(decoded, languageCode)
```

### DO NOT

Do not add a punctuation-restoration neural model. It is the wrong trade against this rubric.

---

## T45 · Warm the models at app start and on language change (revised 2026-09-24)

**Files:** `core/audio/TTSModule.kt`, `core/service/ITantraForegroundService.kt`, `ui/MainViewModel.kt`
**Criterion:** LAT
**Depends on:** T70 (Step 1 uses its `ttsLock`). Check: `grep -n "ttsLock" app/src/main/java/com/itantra/core/audio/TTSModule.kt` must match.

### Why (measured)

`docs/latency-evidence/` shows `STT('hi') loaded in 2306ms` **during** the first PTT phrase. Phrase 1 could not be transcribed until the load finished, phrase 2 queued behind it, and the mid-hold head start was lost. Load the models before the user speaks.

> The first version of this task warmed the models inside the service's start-up `launch`, using the service's own language fields. That only ever warms Hindi: nothing changes those fields until T72. This revision adds a reusable `warmUp()` and calls it when the ViewModel binds to the service; T72 then calls it again whenever the language changes.

### Step 1 — `core/audio/TTSModule.kt`: a warm-up that loads without synthesizing

ANCHOR:

```kotlin
    fun getLoadedLanguages(): Set<String> = ttsCache.keys.toSet()
```

REPLACEMENT:

```kotlin
    /**
     * Load [languageCode]'s voice into the cache without synthesizing anything (T45), so the first
     * received message does not pay the load. Returns false if there is no voice for it or its
     * pack is not downloaded. Takes the same lock as synthesize() (T70).
     */
    suspend fun warmUp(languageCode: String): Boolean =
        ttsLock.withLock { withContext(Dispatchers.Default) { getOrLoadTts(languageCode) != null } }

    fun getLoadedLanguages(): Set<String> = ttsCache.keys.toSet()
```

### Step 2 — `core/service/ITantraForegroundService.kt`: a public `warmUp()`

ANCHOR:

```kotlin
    fun unloadTTSLanguage(lang: String) = ttsModule.unloadLanguage(lang)
```

REPLACEMENT:

```kotlin
    fun unloadTTSLanguage(lang: String) = ttsModule.unloadLanguage(lang)

    /**
     * Load the STT model and TTS voice now, off the critical path (T45). Cheap to call again: both
     * loads return at once when the model is already cached. A PTT phrase that arrives during the
     * warm-up simply waits for the load via the STT lock, as it would have anyway.
     */
    fun warmUp(sttLang: String = sttLanguage, ttsLang: String = ttsLanguage) {
        serviceScope.launch {
            val t0 = System.nanoTime()
            val sttOk = runCatching { sttModule.ensureLoaded(sttLang) }.getOrDefault(false)
            val ttsOk = runCatching { ttsModule.warmUp(ttsLang) }.getOrDefault(false)
            Log.d(TAG, "Warm-up stt=$sttLang:$sttOk tts=$ttsLang:$ttsOk in ${(System.nanoTime() - t0) / 1_000_000}ms")
        }
    }
```

### Step 3 — `ui/MainViewModel.kt`: warm up as soon as the service is bound

ANCHOR (inside `onServiceConnected`):

```kotlin
                viewModelScope.launch { it.isBluetoothListening.collect { b -> _isBluetoothListening.value = b } }
```

REPLACEMENT:

```kotlin
                viewModelScope.launch { it.isBluetoothListening.collect { b -> _isBluetoothListening.value = b } }
                it.warmUp()
```

T66 uses the same ANCHOR line and also keeps it. If T66 is already done, the line is followed by T66's `alertFlow` line — put `it.warmUp()` after that line instead.

### VERIFY

1. `./gradlew :app:compileDebugKotlin`
2. Cold-start the app and wait ~5 s. `adb logcat -s iTantraService:* STTModule:*` shows `STT('hi') loaded in …` and then `Warm-up stt=hi:true tts=hi:true` **before** any PTT press.
3. Hold PTT and speak two phrases with a pause: no `loaded in` line appears during the hold, and phrase 1's STT line follows its `Speech segment complete` line by well under a second.

### DO NOT

- Do not warm all ten languages. That is the RAM criterion pointing the other way. Warm only the selected language.
- Do not call `synthesize(" ", …)` to warm TTS: it runs a real synthesis nobody hears and logs a synthesis-complete event for it. Use `warmUp()`.

---

## T46 · Bound the model caches

> **Superseded 2026-09-24:** use the explicit version in **Group H → T46** (both modules written out, anchors verified against PR #17). Kept here for history.

**Files:** `core/audio/STTModule.kt`, `core/audio/TTSModule.kt`
**Criterion:** EFF

### STTModule

ANCHOR:

```kotlin
    private val sessionCache = mutableMapOf<String, OrtSession>()
    private val vocabCache = mutableMapOf<String, Array<String>>()
    private val ioNamesCache = mutableMapOf<String, IoNames>()
```

REPLACEMENT:

```kotlin
    // Bounded LRU. Each session is a ~197MB native allocation; caching every language a user
    // ever touched held them all for the process lifetime.
    private val sessionCache = object : LinkedHashMap<String, OrtSession>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, OrtSession>): Boolean {
            if (size > MAX_CACHED_LANGUAGES) {
                runCatching { eldest.value.close() }
                vocabCache.remove(eldest.key)
                ioNamesCache.remove(eldest.key)
                Log.d(TAG, "Evicted STT session '${eldest.key}' (LRU)")
                return true
            }
            return false
        }
    }
    private val vocabCache = mutableMapOf<String, Array<String>>()
    private val ioNamesCache = mutableMapOf<String, IoNames>()
```

Add to the companion object:

```kotlin
        /** Sessions kept resident. 2 covers a send/receive language pair. */
        private const val MAX_CACHED_LANGUAGES = 2
```

### TTSModule

Apply the same pattern to `ttsCache`, calling `release()` instead of `close()` on eviction.

### DO NOT

Do not set the limit to 1. A device sending in one language and receiving in another would thrash, reloading a 197 MB model per message.

---

# Group D — Bundle and UI (T20, T21, T22)

## T20 · Make the core bundle a language pair

**File:** `app/src/main/java/com/itantra/domain/model/ModelManifest.kt`
**Criterion:** EFF — currently forces 2.18 GB.

### ANCHOR

```kotlin
        fun coreTransceiverPacks(): List<ModelPack> = listOf(
            VAD_MODEL,
            STT_HINDI,
            STT_GUJARATI,
            STT_MARATHI,
            STT_KANNADA,
            STT_MALAYALAM,
            STT_TAMIL,
            STT_TELUGU,
            STT_BENGALI,
            STT_ENGLISH,
            LANG_DETECTION,
            ESPEAK_NG_DATA,
            TTS_HINDI,
            TTS_GUJARATI,
            TTS_MALAYALAM,
            TTS_BENGALI,
            TTS_ENGLISH
        )
```

### REPLACEMENT

```kotlin
        /** Always needed, whatever languages the user picks. ~10MB total. */
        fun baselinePacks(): List<ModelPack> = listOf(VAD_MODEL, LANG_DETECTION, ESPEAK_NG_DATA)

        fun sttPackFor(code: String): ModelPack? = when (code) {
            "hi" -> STT_HINDI;  "gu" -> STT_GUJARATI; "mr" -> STT_MARATHI
            "kn" -> STT_KANNADA; "ml" -> STT_MALAYALAM; "ta" -> STT_TAMIL
            "te" -> STT_TELUGU; "bn" -> STT_BENGALI;   "en" -> STT_ENGLISH
            else -> null
        }

        fun ttsPackFor(code: String): ModelPack? = when (code) {
            "hi" -> TTS_HINDI;  "gu" -> TTS_GUJARATI; "mr" -> TTS_MARATHI
            "kn" -> TTS_KANNADA; "ml" -> TTS_MALAYALAM; "ta" -> TTS_TAMIL
            "te" -> TTS_TELUGU; "bn" -> TTS_BENGALI;   "en" -> TTS_ENGLISH
            "or" -> TTS_ODIA
            else -> null
        }

        /**
         * The compulsory set for one language pair — roughly 220MB rather than the 2.18GB
         * that downloading all nine STT models plus every voice required. Model and flash
         * footprint is 20% of the evaluation.
         */
        fun coreTransceiverPacks(sttLang: String, ttsLang: String): List<ModelPack> =
            (baselinePacks() + listOfNotNull(sttPackFor(sttLang), ttsPackFor(ttsLang))).distinct()

        @Deprecated("Downloads all nine STT models (2.18GB). Use the two-argument form.")
        fun coreTransceiverPacks(): List<ModelPack> = coreTransceiverPacks("hi", "hi")
```

Then update the call site. **File:** `ui/MainViewModel.kt`, ANCHOR:

```kotlin
    fun downloadCorePacks() = downloadManager.downloadAll(ModelPack.coreTransceiverPacks())
```

REPLACEMENT:

```kotlin
    fun downloadCorePacks() = downloadManager.downloadAll(
        ModelPack.coreTransceiverPacks(_selectedLanguage.value, _selectedLanguage.value)
    )
```

### VERIFY

```bash
grep -rn "coreTransceiverPacks()" app/src/main/java
```

Every remaining no-argument call site must be reviewed. `ModelDownloadGate` is likely one — check it.

---

## T21 🎨 · Per-language download selection

**File:** `ui/screen/DownloadsScreen.kt`
**Criterion:** EFF

**This file's current contents are not quoted here.** Open it, find how pack rows are currently rendered, and extend that pattern. Requirements:

- A language picker for STT and TTS, defaulting to the onboarding language.
- "Download the Pack" fetches only `coreTransceiverPacks(stt, tts)`.
- Show the real total for the current selection using `ModelRegistry` `sizeBytes`, not a hardcoded figure.
- Other languages remain individually downloadable.

### DO NOT

Do not hardcode any size string. The README's "~169 MB" was wrong by 13× precisely because it was hardcoded.

---

## T22 · Licence table

**File:** `README.md`
**Criterion:** REQ / DOC

Extend the existing compliance table with a row per downloaded artefact: component, source URL, licence. Must include:

| Component | Licence |
| --- | --- |
| IndicConformer STT (sherpa-onnx export) | Apache 2.0 |
| Piper / Coqui / Mimic3 voices | Apache 2.0 / MIT, voice-dependent |
| **MMS-TTS voices (T17b)** | **CC-BY-NC 4.0 — non-commercial** |
| Silero VAD | MIT |
| fastText LID | MIT |

The MMS row is the one that matters. Declare it; do not let a judge find it.

---

# Group E — The deferred six 🔬

These were held back from Part 1 because **they require real data or real design decisions**. They are specified here as procedures. Where a step says *fetch*, fetch — do not substitute a plausible value.

## T23 + T29 🔬 · Match the NeMo preprocessor, with a golden test

**Criterion:** ACC — the highest-value accuracy work in the project.

### Step 1 — get the real config

Do **not** use the table in `IMPROVEMENT_PLAN.md` §3.1 as your source of truth. It lists NeMo *defaults*, and this checkpoint may override them.

```python
import nemo.collections.asr as nemo_asr
m = nemo_asr.models.ASRModel.from_pretrained("ai4bharat/indicconformer_stt_hi_hybrid_ctc_rnnt_large")
print(m.cfg.preprocessor)
```

Record every field: `sample_rate`, `window_size`, `window_stride`, `window`, `features`, `n_fft`, `preemph`, `dither`, `log`, `log_zero_guard_type`, `log_zero_guard_value`, `mag_power`, `normalize`, `mel_norm`, `lowfreq`, `highfreq`.

### Step 2 — reconcile against the Kotlin

For each field, confirm the Part 1 implementation matches. Where it does not, **the config wins.** Fields most likely to differ from what Part 1 assumed: `window_size` (0.02 vs 0.025), `lowfreq`/`highfreq` (Part 1 assumes 0 and 8000), and `dither` (Part 1 omits it — dither is usually disabled at inference, but confirm).

### Step 3 — emit the golden fixture

```python
import torch, numpy as np, soundfile as sf
wav, sr = sf.read("fixture.wav")          # 16kHz mono, 3-5 seconds of real speech
sig = torch.tensor(wav, dtype=torch.float32).unsqueeze(0)
length = torch.tensor([sig.shape[1]])
feats, feat_len = m.preprocessor(input_signal=sig, length=length)
np.save("golden_features.npy", feats[0].cpu().numpy())   # [n_mels, T]
```

Commit `fixture.wav` and `golden_features.npy` to `app/src/test/resources/`.

### Step 4 — the test

**New file:** `app/src/test/java/com/itantra/MelFeatureGoldenTest.kt`

Load the fixture WAV, run `extractLogMelSpectrogram`, and assert every value is within `1e-3` of the golden array. Report the worst absolute difference and its index on failure — a systematic offset in one mel band points at the filterbank, a drift along time points at framing or centering.

`extractLogMelSpectrogram` is `private`. Make it `internal` and annotate `@VisibleForTesting`. **Do not make it public.**

### DO NOT

Do not relax the tolerance to make the test pass. If it fails at 1e-3, the preprocessing is still wrong and the WER gap is still real.

---

## T30 🔬 · WER harness

**Criterion:** ACC / DOC

### Choosing a test set

Use a public Indic ASR benchmark with per-language test splits. Whichever you pick, **record its name, version and split** — an unnamed WER number is worth nothing to a judge.

### Text normalisation before scoring

This is where naive WER harnesses go wrong, and it matters more for Indic scripts than for English:

- Unicode NFC normalise both reference and hypothesis.
- Strip punctuation including danda (।) and double danda (॥).
- Normalise Indic digits to ASCII, or the reverse — consistently, and say which.
- Do **not** lowercase Indic text; it has no case. Lowercase only for English.
- Do not strip ZWJ/ZWNJ blindly: in Malayalam and Bengali they are orthographically meaningful.

### Procedure

1. Push N utterances per language to the device.
2. Run each through `STTModule.transcribe` via an instrumented test.
3. Pull the hypotheses, compute WER and CER per language against normalised references.
4. Emit `wer_results.csv`: `language, n_utterances, wer, cer, median_rtf`.
5. Compare against the checkpoint's published WER on the same benchmark. **The gap is the number that matters** — it is the part you control.

### DO NOT

Do not report a single averaged WER across all ten languages. Per-language is what the rubric implies and what is defensible.

---

## T48 + T49 🔬 · CTC beam search

**File:** `core/audio/CtcDecoder.kt`
**Criterion:** ACC · Expected: 10–20% relative WER

Add `beamDecode(logits, vocab, blankId, beamWidth = 10)` alongside the existing `greedyDecode`. **Do not modify or delete `greedyDecode`** — it is unit-tested and is the fallback.

Standard CTC prefix beam search:

- Track two probabilities per prefix: ending in blank (`pB`) and ending in a non-blank (`pNB`).
- Work in log space throughout. Use a log-sum-exp helper; naive probability multiplication underflows within ~50 frames at this vocabulary size (5633 for Hindi).
- At each frame consider only the top-K tokens (K ≈ 20) rather than all 5633, or decode time will exceed the greedy path by more than the WER gain is worth.
- Prune to `beamWidth` prefixes after each frame.

Gate it behind a flag so you can A/B measure:

```kotlin
    /** Beam search costs latency. Measure the WER gain before enabling by default. */
    var useBeamSearch: Boolean = false
```

Measure RTF **and** WER both ways with the T30 harness before choosing a default. If beam search pushes RTF above 0.5, keep greedy — Latency is 20% and Accuracy is 40%, but a submission that misses real-time entirely reads as broken.

KenLM (T49) is only worth attempting once beam search is measured and landed.

---

## T50 🔬 · Streaming STT

**Criterion:** LAT · **The largest structural latency win, and the riskiest task in the plan.**

> **Amended 2026-09-23:** do **T65** (phrase-level pipelining) first. It sends each phrase at a natural pause while PTT is held, with no WER cost, and may remove the need for this task. Only start T50 if T65's measured cross-device delay still misses the target.

Do not begin this until T05, T29 and T30 are all complete. You need a green golden test and a WER baseline, because streaming changes the feature context the encoder sees and **will** move WER. Without a baseline you cannot tell a bug from an expected regression.

### Design constraints

- IndicConformer is a **non-streaming** architecture: it attends over the whole utterance. Chunked inference with no context window degrades accuracy at every chunk boundary.
- Therefore use **overlapping windows with left context**: chunk 2 s, left context 1 s, hop 1 s. Discard the outputs corresponding to the context region and keep only the new hop.
- Merge at the CTC level — concatenate token sequences and collapse repeats across the seam — not at the text level, which double-writes characters at boundaries.

### Acceptance

Streaming is only adopted if WER degrades by **less than 1 point absolute** versus the batch path on the T30 harness. Otherwise keep batch inference and take the latency win from T41 and T40 instead. Record the comparison either way; it is a good slide.

### DO NOT

Do not ship streaming without the WER comparison. A faster pipeline that transcribes worse loses more of the 40% than it gains of the 20%.

---

# Group G — Second-audit tasks (T62–T69)

Added 2026-09-23. The findings behind each task are in [`IMPROVEMENT_PLAN.md` §10](IMPROVEMENT_PLAN.md#10-addendum--second-audit-2026-09-23).

### How to use this group (read first, especially if you are a smaller model)

- **Every rule in Part 1 §0 and in "Additional rules" above still applies.** Match on text, never on line numbers. If an ANCHOR does not match character for character, **stop and report**. Do not adapt the change to "similar-looking" code.
- **Some anchors depend on earlier tasks.** Each task has a **Depends on** line. If a dependency is not done, do not start the task. Check whether a dependency is done by searching for the text its REPLACEMENT adds, e.g. `grep -n "playbackQueue" app/src/main/java/com/itantra/core/audio/AudioPlaybackManager.kt` for T38.
- **File paths** are relative to `app/src/main/java/com/itantra/` unless they start with `app/`, `docs/` or `model-export/`.
- **Fully qualified names** (e.g. `java.util.concurrent.atomic.AtomicInteger`) are used deliberately so that you do not need to add imports. If a snippet uses a short name that the file does not import yet, the task says which import to add.
- **Build check after every task:** `./gradlew :app:compileDebugKotlin` (Windows: `.\gradlew.bat :app:compileDebugKotlin`). It must pass before you commit. Commit message starts with the task ID.

### Recommended order

```
T69 (transport + dedup) ─┬─> T66 (SOS UI) ──> T68 (ESP32, stretch)
                         │
T37 (phone mode) + T63 (echo gate)   ← always in the same PR
T62 (Silero VAD) ──> T41 ──> T65 (phrase pipelining)   (T65 also needs T38 on the receiver)
T13 ──> T67 (voice notes)
T64 (Odia + CTC re-export)            — Python/offline, can run in parallel with everything
```

---

## T62 🔬 · Repair the Silero VAD and make it the primary detector

**Files:** `core/audio/VADModule.kt`, `core/download/ModelRegistry.kt`
**Criterion:** ACC (clean segmentation), EFF (idle CPU), LAT (reliable endpoints)
**Depends on:** nothing. Do it **before** T32; T32 then becomes the fallback path.

### Background you need

- `VADModule.initialize()` forces the energy backend because the model returned ~0.001 for silence, a sine wave and white noise. **That is correct behaviour for a speech detector** — none of those is speech. The model was never tested on speech.
- The model is downloaded from the Silero repo's `master` branch. Its inputs (`input`, `state [2,1,128]`, `sr`) identify it as the **v5+** model (the file name `silero_vad_v4.onnx` is only a local name — do not rename it, or existing downloads are orphaned).
- The official v5 Python wrapper (`OnnxWrapper.__call__` in `silero_vad/utils_vad.py`) keeps the **last 64 samples** of the previous 512-sample window and **prepends** them, so each call receives `[1, 576]`. `VADModule` sends `[1, 512]` with no context. This is the most likely reason the output looked flat.

### Step 1 — prove the diagnosis in Python before touching Kotlin

1. Pull the exact model the app uses:

   ```bash
   adb shell run-as com.itantra.debug cat files/models/silero_vad_v4.onnx > silero_vad_v4.onnx
   ```

2. Record ~5 s of someone speaking, and ~5 s of room silence, as **16 kHz mono WAV**. Convert with `ffmpeg -i in.m4a -ac 1 -ar 16000 speech_16k.wav` if needed.

3. Save this as `model-export/check_silero.py` and run it on both files:

   ```python
   """Checks the app's Silero model with and without the v5 64-sample context (T62)."""
   import sys
   import numpy as np
   import onnxruntime as ort
   import soundfile as sf

   MODEL, WAV = sys.argv[1], sys.argv[2]
   wav, sr = sf.read(WAV, dtype="float32")
   assert sr == 16000 and wav.ndim == 1, "need 16 kHz mono"
   sess = ort.InferenceSession(MODEL)

   def run(with_context: bool):
       state = np.zeros((2, 1, 128), dtype=np.float32)
       ctx = np.zeros(64, dtype=np.float32)
       probs = []
       for i in range(0, len(wav) - 512, 512):
           w = wav[i:i + 512]
           x = np.concatenate([ctx, w]) if with_context else w
           out, state = sess.run(None, {
               "input": x[None, :].astype(np.float32),
               "state": state,
               "sr": np.array(16000, dtype=np.int64),
           })
           ctx = w[-64:]
           probs.append(float(out[0][0]))
       p = np.array(probs)
       return p.max(), (p > 0.5).mean()

   for flag in (False, True):
       mx, frac = run(flag)
       print(f"context={flag}: max={mx:.3f} fraction>0.5={frac:.2f}")
   ```

   `pip install onnxruntime soundfile numpy` if needed.

4. **Decision:**

   | Result on the speech file | Result on the silence file | Action |
   | --- | --- | --- |
   | `context=True` max ≥ 0.8 | `context=True` max < 0.3 | Diagnosis confirmed — do Step 2 |
   | Both runs flat (< 0.3) on speech | — | **STOP.** Report both outputs. Keep the energy backend (T32) |
   | `context=False` already works | — | Context is not the issue, but the model works — do Step 2 anyway (context is still correct per the official wrapper) |

   Paste the printed lines into the PR description. They are the evidence for the slide.

### Step 2 — Kotlin changes in `core/audio/VADModule.kt`

**2a. Constants.** ANCHOR (in the companion object):

```kotlin
        private const val SILERO_WINDOW_SIZE = 512
```

REPLACEMENT:

```kotlin
        private const val SILERO_WINDOW_SIZE = 512
        /** Samples of the previous window prepended to every Silero call. The official v5
         *  wrapper (OnnxWrapper in silero_vad/utils_vad.py) does this; without it the model's
         *  output is unreliable. See T62. */
        private const val SILERO_CONTEXT_SIZE = 64
        /** Release threshold for the neural backend. Silero's own recommended hysteresis is
         *  "threshold - 0.15", which stops the detector chattering inside a word. */
        private const val SPEECH_RELEASE_THRESHOLD = 0.35f
```

**2b. Context buffer.** ANCHOR:

```kotlin
    private var lastSpeechProb = 0f
```

REPLACEMENT:

```kotlin
    private var lastSpeechProb = 0f
    /** Last SILERO_CONTEXT_SIZE samples of the previous window (T62). */
    private val sileroContext = FloatArray(SILERO_CONTEXT_SIZE)
```

**2c. Turn the neural backend on.** ANCHOR (inside `initialize()`; the long comment above it stays — **do not delete it**):

```kotlin
            activeBackend = VadBackend.BASIC_ENERGY
            session?.let {
```

REPLACEMENT:

```kotlin
            // UPDATE (T62): the test described above used only non-speech inputs, so ~0 output
            // was the correct answer, not a malfunction. The real defect was the missing 64-sample
            // context the v5 model expects (added in process()). Verified with
            // model-export/check_silero.py on recorded speech before re-enabling.
            activeBackend = if (session != null) VadBackend.NEURAL else VadBackend.BASIC_ENERGY
            session?.let {
```

**2d. Feed the context.** ANCHOR (inside `process()`):

```kotlin
            pendingSamples.addAll(audioChunk.asIterable())
            while (pendingSamples.size >= SILERO_WINDOW_SIZE) {
                val window = FloatArray(SILERO_WINDOW_SIZE) { pendingSamples.removeFirst() }

                val inputTensor = OnnxTensor.createTensor(
                    env,
                    FloatBuffer.wrap(window),
                    longArrayOf(1, SILERO_WINDOW_SIZE.toLong())
                )
```

REPLACEMENT:

```kotlin
            pendingSamples.addAll(audioChunk.asIterable())
            var chunkMaxProb = -1f
            while (pendingSamples.size >= SILERO_WINDOW_SIZE) {
                val window = FloatArray(SILERO_WINDOW_SIZE) { pendingSamples.removeFirst() }

                // v5+ Silero input is [previous 64 samples | current 512 samples] = 576 (T62).
                val framed = FloatArray(SILERO_CONTEXT_SIZE + SILERO_WINDOW_SIZE)
                sileroContext.copyInto(framed, destinationOffset = 0)
                window.copyInto(framed, destinationOffset = SILERO_CONTEXT_SIZE)
                window.copyInto(
                    sileroContext,
                    destinationOffset = 0,
                    startIndex = SILERO_WINDOW_SIZE - SILERO_CONTEXT_SIZE,
                    endIndex = SILERO_WINDOW_SIZE
                )

                val inputTensor = OnnxTensor.createTensor(
                    env,
                    FloatBuffer.wrap(framed),
                    longArrayOf(1, framed.size.toLong())
                )
```

**2e. Track the loudest window in the chunk.** One 100 ms chunk contains three 32 ms windows; keeping only the last one can miss a short word. ANCHOR:

```kotlin
                lastSpeechProb = outputVal[0][0]
```

REPLACEMENT:

```kotlin
                lastSpeechProb = outputVal[0][0]
                if (lastSpeechProb > chunkMaxProb) chunkMaxProb = lastSpeechProb
```

**2f. Hysteresis and return value.** ANCHOR (the neural branch's tail — the energy branch uses `prob`, not `lastSpeechProb`, so this text is unique):

```kotlin
            val isCurrentSpeech = lastSpeechProb >= SPEECH_THRESHOLD
            if (isCurrentSpeech != isSpeechActive) {
                isSpeechActive = isCurrentSpeech
                callbacks.onVADTriggered(isCurrentSpeech, lastSpeechProb)
            }

            lastSpeechProb
```

REPLACEMENT:

```kotlin
            // Loudest window in this chunk; falls back to the previous value if the chunk was too
            // short to complete a window.
            val chunkProb = if (chunkMaxProb >= 0f) chunkMaxProb else lastSpeechProb
            val isCurrentSpeech = if (isSpeechActive) chunkProb >= SPEECH_RELEASE_THRESHOLD
                                  else chunkProb >= SPEECH_THRESHOLD
            if (isCurrentSpeech != isSpeechActive) {
                isSpeechActive = isCurrentSpeech
                callbacks.onVADTriggered(isCurrentSpeech, chunkProb)
            }

            // AudioCaptureModule compares this return value against a fixed 0.5, so return a
            // value on the side of 0.5 that matches the hysteresis decision made above.
            if (isCurrentSpeech) maxOf(chunkProb, SPEECH_THRESHOLD)
            else minOf(chunkProb, SPEECH_THRESHOLD - 0.01f)
```

**2g. Reset the context.** ANCHOR (inside `resetState()`):

```kotlin
        pendingSamples.clear()
        lastSpeechProb = 0f
```

REPLACEMENT:

```kotlin
        pendingSamples.clear()
        lastSpeechProb = 0f
        sileroContext.fill(0f)
```

### Step 3 — pin the model version in `core/download/ModelRegistry.kt`

`master` can change between two users' downloads. Pin it.

1. Run `git ls-remote --tags https://github.com/snakers4/silero-vad`. Pick the newest tag whose name starts with `v5` or `v6`. Call it `<TAG>`. **Use the real output; do not guess a tag.**
2. Check the file exists at that tag: `curl -sIL https://raw.githubusercontent.com/snakers4/silero-vad/<TAG>/src/silero_vad/data/silero_vad.onnx | grep -i -E "^HTTP|content-length"` must show `200` and a length.
3. Get its hash: `curl -sL <that URL> | sha256sum`.
4. ANCHOR:

   ```kotlin
           "https://raw.githubusercontent.com/snakers4/silero-vad/master/src/silero_vad/data/silero_vad.onnx"
   ```

   REPLACEMENT: the same line with `master` replaced by `<TAG>`.
5. In the `ModelPack.VAD_MODEL` entry, set `sizeBytes` to the content-length from step 2 and `sha256` to the hash from step 3 (lowercase hex string).
6. Re-run Step 1 against the pinned file before committing.

### VERIFY

```bash
./gradlew :app:compileDebugKotlin
adb logcat -s VADModule:*
```

- Logcat on app start must show `VAD initialized — backend: NEURAL`.
- Speak in a quiet room: speech is detected, and released within ~1 s of stopping.
- Play traffic or fan noise from a second device at arm's length, stay silent: **no** detection. Then speak over it: detection.
- If any of these fail, revert Step 2c only (the backend switch). The energy path is then used again and nothing else regresses.

### DO NOT

- Do not rename `silero_vad_v4.onnx`.
- Do not change `CHUNK_SIZE` (1600) or `SILERO_WINDOW_SIZE` (512).
- Do not delete the energy branch. It is the fallback when the model is missing or throws.

---

## T63 · Echo gate: never capture while this phone is playing

**Files:** `core/audio/AudioPlaybackManager.kt`, `core/audio/AudioCaptureModule.kt`, `core/service/ITantraForegroundService.kt`
**Criterion:** REQ — without it, phone mode (T37) transmits received messages back to the sender.
**Depends on:** nothing. **Must be merged in the same PR as T37**, never after.

### Why

In phone mode the microphone is always on. When a message arrives, TTS plays through the loudspeaker, the microphone hears it, VAD fires, STT transcribes it, and the transcript is sent back. Two phones can loop one sentence forever. Nothing currently prevents this. The fix is to ignore microphone input while audio is playing, plus a short tail for room echo.

### Step 1 — `AudioPlaybackManager.kt`: expose "is audio playing?"

Both `playNormal()` and `playAlert()` call `requestAudioFocus(...)` before playing and `releaseAudioFocus()` in their `finally` block. Those two functions are therefore the exact start and end of every playback. Count there.

**1a.** ANCHOR:

```kotlin
    private var savedVolume: Int = -1
```

REPLACEMENT:

```kotlin
    private var savedVolume: Int = -1

    /** Tracks currently between requestAudioFocus() and releaseAudioFocus() (T63). */
    private val activePlaybacks = java.util.concurrent.atomic.AtomicInteger(0)
    /** System.nanoTime() of the most recent playback start and end (T63). */
    @Volatile private var lastPlaybackStartNs: Long = 0L
    @Volatile private var lastPlaybackEndNs: Long = 0L

    /**
     * True while this device is playing audio, or within [tailMs] after it stopped. Used by
     * AudioCaptureModule as an echo gate so a received message played on the loudspeaker is not
     * re-captured, transcribed and sent back (T63). The tail covers room reverberation and the
     * AudioTrack drain after the last write.
     *
     * Stale guard: if a playback start was never matched by an end (e.g. AudioTrack.Builder
     * threw between requestAudioFocus and the try block), stop gating after 60 s so the
     * microphone can never be muted permanently. No single message plays that long.
     */
    fun isOutputActive(tailMs: Long = 250L): Boolean {
        val now = System.nanoTime()
        if (activePlaybacks.get() > 0) {
            if (now - lastPlaybackStartNs < 60_000_000_000L) return true
            activePlaybacks.set(0)
        }
        return lastPlaybackEndNs != 0L && now - lastPlaybackEndNs < tailMs * 1_000_000L
    }
```

**1b.** ANCHOR:

```kotlin
    private fun requestAudioFocus(isAlert: Boolean) {
```

REPLACEMENT:

```kotlin
    private fun requestAudioFocus(isAlert: Boolean) {
        lastPlaybackStartNs = System.nanoTime()
        activePlaybacks.incrementAndGet()
```

**1c.** ANCHOR:

```kotlin
    private fun releaseAudioFocus() {
```

REPLACEMENT:

```kotlin
    private fun releaseAudioFocus() {
        lastPlaybackEndNs = System.nanoTime()
        activePlaybacks.updateAndGet { if (it > 0) it - 1 else 0 }
```

**Check before continuing:** `grep -n "requestAudioFocus(\|releaseAudioFocus()" app/src/main/java/com/itantra/core/audio/AudioPlaybackManager.kt`. You must see exactly two calls of each (one pair in `playNormal`, one in `playAlert`), plus the two definitions. If another task has changed that pairing, **stop and report**.

### Step 2 — `AudioCaptureModule.kt`: drop input while gated

**2a.** ANCHOR:

```kotlin
    var currentLanguage: String = "hi"
```

REPLACEMENT:

```kotlin
    var currentLanguage: String = "hi"

    /**
     * Echo gate (T63). When this returns true, captured audio is discarded instead of being fed
     * to VAD/STT. The service wires it to AudioPlaybackManager.isOutputActive(). A lambda rather
     * than a constructor parameter so the service's construction order does not change.
     */
    @Volatile var isSuppressed: () -> Boolean = { false }
```

**2b.** ANCHOR (inside the capture loop in `startCapture()`; only the comment line is matched because T51 renames the variable on the next line):

```kotlin
                // Run VAD
```

REPLACEMENT:

```kotlin
                // Echo gate (T63): while this phone is playing a received message, whatever the
                // microphone hears is our own loudspeaker. Discard it and any half-built segment.
                if (isSuppressed()) {
                    synchronized(bufferLock) {
                        speechBuffer.clear()
                        silenceChunkCount = 0
                    }
                    continue
                }

                // Run VAD
```

If other tasks have added counters that must be reset whenever `speechBuffer.clear()` is called, reset them inside the same `synchronized` block too:

| If this task is done | Also add inside the block |
| --- | --- |
| T31 | `preRoll.clear()` |
| T41 | `speechChunkCount = 0` |
| T52 | set the running-total counter T52 added to `0` |

### Step 3 — `ITantraForegroundService.kt`: wire it

ANCHOR (inside `initializeModules()`):

```kotlin
        // Initialize VAD on startup (always resident)
```

REPLACEMENT:

```kotlin
        // Echo gate (T63): never capture while this device is playing a received message.
        audioCaptureModule.isSuppressed = { audioPlayback.isOutputActive() }

        // Initialize VAD on startup (always resident)
```

### VERIFY

1. `./gradlew :app:compileDebugKotlin`
2. Two phones, both in phone mode (T37). Say one sentence on phone A. Phone B plays it. **Phone A must not receive anything back**, and B's logcat (`adb logcat -s AudioCapture:*`) must show no `Speech segment complete` while B is playing.
3. After B finishes playing, speak on B: it must transmit normally (the gate releases).
4. PTT mode: hold PTT while a message is playing. Speech during playback is dropped (half-duplex, like a radio). Speech after playback ends is sent. This is expected; mention it in the demo script.

### DO NOT

- Do not stop and restart `AudioRecord` to gate. Restarting costs 50–200 ms and can glitch on some devices.
- Do not put the gate inside `VADModule`. VAD state should not be touched by playback.
- Do not attach `AcousticEchoCanceler` as part of this task. The gate is deterministic; AEC quality varies by device. It can be evaluated later for barge-in.

---

## T64 🔬 · Odia STT, and a clean CTC-only export of all ten languages (merges T55)

**Files:** new `model-export/export_ctc_int8.py`, `domain/model/ModelManifest.kt`, `core/download/ModelRegistry.kt`
**Criterion:** REQ (10/10 languages), ACC, EFF (size)
**Depends on:** a hosting location decided by a human (same one as T17b). **If no hosting URL has been given to you, do Steps 1–5, then stop and report.**

### Background you need

- The registry downloads STT from a third-party mirror, `parismitaglobalsolutions/indicconformer-sherpa-onnx`, which has no Odia.
- AI4Bharat publishes Odia directly: `ai4bharat/indicconformer_stt_or_hybrid_ctc_rnnt_large`. Same family as the other nine: Conformer-Large, ~120 M parameters, hybrid CTC + RNNT decoders.
- `STTModule` feeds **80-bin log-mel features** `[1, 80, T]` plus a length, and decodes CTC with the blank as the **last** vocabulary entry. The export must produce exactly that interface. NeMo's `export()` does not include the preprocessor by default, which is what we want.
- **Do not use `model-export/export_indicconformer.py`.** It restores the checkpoint as `EncDecMultiTaskModel` (the wrong class) and exports a raw-audio graph.

### Step 1 — environment

Use Linux or Google Colab, Python 3.10. Open the model card at `https://huggingface.co/ai4bharat/indicconformer_stt_or_hybrid_ctc_rnnt_large` and **follow its installation instructions exactly** — AI4Bharat checkpoints may require their NeMo fork rather than stock `nemo_toolkit`. Record the exact install commands in the PR. Then `pip install onnx onnxruntime soundfile`.

Download (you may need `huggingface-cli login` and to accept the model's terms on the website first):

```bash
huggingface-cli download ai4bharat/indicconformer_stt_or_hybrid_ctc_rnnt_large --local-dir ckpt_or
ls ckpt_or   # note the .nemo file name
```

### Step 2 — inspect before exporting

```python
import nemo.collections.asr as nemo_asr
m = nemo_asr.models.ASRModel.restore_from("ckpt_or/<FILE>.nemo", map_location="cpu")
m.eval()
print(type(m).__name__)          # expect a hybrid RNNT+CTC class
print(m.cfg.preprocessor)        # SAVE this output: it is the input for T23
print(m.cfg.decoder.keys() if "decoder" in m.cfg else "no decoder cfg")
```

Also run the model card's own transcription example on one Odia WAV and save the text. **If the card's example passes a `language_id` argument**, the checkpoint uses AI4Bharat's multi-softmax decoder and its CTC output covers a **shared multilingual vocabulary** — note this; Step 4 depends on it.

### Step 3 — export CTC-only, then quantize

Save as `model-export/export_ctc_int8.py`:

```python
"""Exports an AI4Bharat IndicConformer hybrid checkpoint as a CTC-only INT8 ONNX graph (T64).

Output matches what STTModule expects: inputs = mel features [1, 80, T] + length,
output = CTC log-probs [1, T', V], blank = last vocabulary id.
"""
import sys
import nemo.collections.asr as nemo_asr
from onnxruntime.quantization import quantize_dynamic, QuantType

nemo_path, out_prefix = sys.argv[1], sys.argv[2]
m = nemo_asr.models.ASRModel.restore_from(nemo_path, map_location="cpu")
m.eval()

# Select the CTC head for export. If this call fails, read the error and the installed NeMo's
# docs for the hybrid model's export options -- do NOT guess another API.
m.set_export_config({"decoder_type": "ctc"})
m.export(f"{out_prefix}.fp32.onnx")

quantize_dynamic(f"{out_prefix}.fp32.onnx", f"{out_prefix}.int8.onnx",
                 weight_type=QuantType.QUInt8)
print("done")
```

Run: `python model-export/export_ctc_int8.py ckpt_or/<FILE>.nemo or_model`

Check the graph's interface:

```python
import onnxruntime as ort
s = ort.InferenceSession("or_model.int8.onnx")
print([(i.name, i.shape) for i in s.get_inputs()])   # expect a feature input with 80 channels + a length
print([(o.name, o.shape) for o in s.get_outputs()])  # note the last dim = vocabulary size V
```

`STTModule.resolveIoNames()` accepts feature inputs named `audio_signal`, `x`, `features`, `input` or `waveform`, and lengths named `length`, `x_lens`, `input_length` or `x_length`. If the names differ, **stop and report** — do not rename graph nodes.

### Step 4 — tokens.txt

The file format `CtcDecoder.parseTokens` reads is one `<token> <id>` per line, ids `0..V-1`, with the blank as the last line. Generate it from the model, not by hand:

```python
vocab = m.ctc_decoder.vocabulary if hasattr(m, "ctc_decoder") else m.decoder.vocabulary
with open("or_tokens.txt", "w", encoding="utf-8") as f:
    for i, t in enumerate(vocab):
        f.write(f"{t} {i}\n")
    f.write(f"<blk> {len(vocab)}\n")
```

Then check: number of lines == `V` from Step 3. **If they differ, stop and report.** If Step 2 showed a `language_id` (multi-softmax) and `V` equals the line count of the mirror's shared root `tokens.txt` (5633 lines for the existing languages), the model uses the shared vocabulary — record this in the PR; the existing `STTModule` decodes it the same way it already decodes the other eight.

### Step 5 — validate before shipping

Compute features with **NeMo's own preprocessor** (`m.preprocessor`) on your Odia test WAV, run `or_model.int8.onnx` on them, greedy-decode (argmax per frame, merge repeats, drop blank = last id) with `or_tokens.txt`, and compare with the model card transcription from Step 2. They must match or differ only trivially. Record both strings and the file size of `or_model.int8.onnx` in the PR. **Expected size: ~120–135 MB.** If it is > 180 MB, say so.

### Step 6 — the other nine (the old T55)

Only if Step 5 passed and the size is clearly below the mirror's ~197 MB: repeat Steps 1–5 for `hi gu mr kn ml ta te bn` using `ai4bharat/indicconformer_stt_<code>_hybrid_ctc_rnnt_large`. **Keep English on the existing mirror** (there is no AI4Bharat IndicConformer for English). For each language record: size, the T30 WER before (mirror) and after (your export). Adopt the new export only for languages whose WER did not get worse by more than 0.5 points.

### Step 7 — host (human decision)

Upload `stt_<code>_int8.onnx` and `stt_<code>_tokens.txt` to the team's model repository (the same host as T17b). **You need the exact base URL from a human.** Record each file's real size (`ls -l`) and SHA-256 (`sha256sum`).

### Step 8 — app changes

**8a. `domain/model/ModelManifest.kt`** — add an Odia STT pack. ANCHOR:

```kotlin
    STT_ENGLISH(
        "English STT Engine",
        "IndicConformer (sherpa-onnx) — English speech recognition",
        sizeMb = 188,
        isRequired = false,
        requiredFor = "Transceiver"
    ),
```

REPLACEMENT:

```kotlin
    STT_ENGLISH(
        "English STT Engine",
        "IndicConformer (sherpa-onnx) — English speech recognition",
        sizeMb = 188,
        isRequired = false,
        requiredFor = "Transceiver"
    ),
    STT_ODIA(
        "Odia STT Engine",
        "IndicConformer (AI4Bharat, self-exported CTC INT8) — Odia speech recognition",
        sizeMb = 0, // SET to the real size in MB from Step 7
        isRequired = false,
        requiredFor = "Transceiver"
    ),
```

Replace the `0` with the real size before committing.

**8b.** If T20 is done, add Odia to `sttPackFor`. ANCHOR:

```kotlin
            "te" -> STT_TELUGU; "bn" -> STT_BENGALI;   "en" -> STT_ENGLISH
            else -> null
```

(the first of the two `when` blocks — the one inside `sttPackFor`). REPLACEMENT:

```kotlin
            "te" -> STT_TELUGU; "bn" -> STT_BENGALI;   "en" -> STT_ENGLISH
            "or" -> STT_ODIA
            else -> null
```

If T20 is not done, instead add `STT_ODIA,` after `STT_ENGLISH,` in the `coreTransceiverPacks()` list.

**8c. `core/download/ModelRegistry.kt`.** Add a helper beside `sttInfo` for self-hosted STT. ANCHOR:

```kotlin
    /** A sherpa-onnx `tts-models` release TTS voice bundle: real espeak-ng phonemization included. */
```

REPLACEMENT:

```kotlin
    /**
     * Self-exported STT (T64): AI4Bharat IndicConformer checkpoint -> CTC-only INT8 ONNX, hosted
     * by the team because the mirror behind SHERPA_BASE has no Odia model. Each language has its
     * own tokens file on the host.
     */
    private fun hostedSttInfo(pack: ModelPack, lang: String, sizeBytes: Long, sha256: String, tokensSha256: String): ModelInfo =
        ModelInfo(
            pack = pack,
            fileName = "stt_${lang}_int8.onnx",
            downloadUrl = "$ITANTRA_MODELS_BASE/stt_${lang}_int8.onnx",
            sha256 = sha256,
            sizeBytes = sizeBytes,
            auxFileName = "stt_${lang}_tokens.txt",
            auxUrl = "$ITANTRA_MODELS_BASE/stt_${lang}_tokens.txt"
        )

    /** A sherpa-onnx `tts-models` release TTS voice bundle: real espeak-ng phonemization included. */
```

`tokensSha256` is recorded for the PR but `ModelInfo` has no field for an aux hash — leave the parameter out if the compiler warns it is unused, and write the hash in a comment on the registry entry instead. If T17b already added a base-URL constant for the team host, use that name instead of `ITANTRA_MODELS_BASE`. Otherwise add, beside `SHERPA_TTS_MODELS_BASE`:

```kotlin
    /** Team-hosted exports (T17b voices, T64 STT). Set by a human — never guess this URL. */
    private const val ITANTRA_MODELS_BASE = "<BASE URL FROM STEP 7>"
```

Then add the registry entry beside the other STT lines. ANCHOR:

```kotlin
        ModelPack.STT_ENGLISH to sttInfo(ModelPack.STT_ENGLISH, "en", 197_595_500L),
```

REPLACEMENT:

```kotlin
        ModelPack.STT_ENGLISH to sttInfo(ModelPack.STT_ENGLISH, "en", 197_595_500L),
        ModelPack.STT_ODIA to hostedSttInfo(ModelPack.STT_ODIA, "or", <SIZE_BYTES>L, "<SHA256>", "<TOKENS_SHA256>"),
```

with the real values from Step 7.

**8d.** Update the class doc of `ModelRegistry.kt`: the sentence saying the source has no Odia STT should now say Odia STT is self-exported from `ai4bharat/indicconformer_stt_or_hybrid_ctc_rnnt_large` and hosted by the team. Keep the "Assamese is not substituted" sentence.

### VERIFY

- `./gradlew :app:compileDebugKotlin`
- On device: select Odia, download, hold PTT and speak Odia. Logcat `STTModule` shows `STT('or') loaded` and a non-empty Odia transcription.
- Add the Odia row to the T30 WER table.

### DO NOT

- Do not substitute the Assamese (`as/`) model for Odia.
- Do not hand-write `tokens.txt`.
- Do not invent the hosting URL, sizes or hashes.

---

## T65 · Phrase-level pipelining while PTT is held

**Files:** `core/audio/STTModule.kt`, `core/audio/AudioCaptureModule.kt`, `core/service/ITantraForegroundService.kt`
**Criterion:** LAT (cross-device delay), REQ ("after detecting pauses … form the sentences … instantly stream")
**Depends on:** T41 (its getter is the anchor in Step 3), and T38 on the receiving side (so phrases queue instead of overlapping).

### Background you need — what already exists, and what is broken

- The capture loop already cuts a segment at 800 ms of silence **even while PTT is held**, and calls `onSpeechReady` → STT → transmit. So mid-hold phrases are already sent. Three things are wrong with it:
  1. **Capture stalls during inference.** `onSpeechReady(...)` is called *inside* the capture loop and runs the whole STT inference. The loop stops reading the microphone for that time, while `AudioRecord`'s buffer holds only ~200 ms. Audio spoken during inference is lost.
  2. **Concurrent inference corrupts features.** `stopPTT()` calls `sttModule.transcribe()` directly, possibly while a mid-hold segment is still being transcribed on another coroutine. After T05, `STTModule` reuses member scratch arrays (`fftRe`, `fftIm`, `powerSpectrum`) and a plain `HashMap` session cache, so two concurrent calls corrupt each other.
  3. **Order is not guaranteed.** The release flush can overtake an earlier phrase.
- The fix: one lock around inference, one queue that all segments go through (including the release flush), and a shorter cut while PTT is held.

### Step 1 — `STTModule.kt`: serialise inference

**1a.** Add the import. ANCHOR:

```kotlin
import kotlinx.coroutines.withContext
```

REPLACEMENT:

```kotlin
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
```

**1b.** ANCHOR:

```kotlin
    private val ioNamesCache = mutableMapOf<String, IoNames>()
```

REPLACEMENT:

```kotlin
    private val ioNamesCache = mutableMapOf<String, IoNames>()

    /** One inference or model load at a time (T65). The feature extractor reuses member scratch
     *  buffers and the caches are plain HashMaps, so concurrent calls corrupt each other. */
    private val inferenceLock = kotlinx.coroutines.sync.Mutex()
```

**1c.** ANCHOR:

```kotlin
    suspend fun ensureLoaded(languageCode: String): Boolean = withContext(Dispatchers.Default) {
```

REPLACEMENT:

```kotlin
    suspend fun ensureLoaded(languageCode: String): Boolean =
        inferenceLock.withLock { ensureLoadedUnlocked(languageCode) }

    private suspend fun ensureLoadedUnlocked(languageCode: String): Boolean = withContext(Dispatchers.Default) {
```

**1d.** ANCHOR:

```kotlin
    suspend fun transcribe(
        audioBuffer: FloatArray,
        languageCode: String = "hi"
    ): AppResult<String> = withContext(Dispatchers.Default) {
```

REPLACEMENT:

```kotlin
    suspend fun transcribe(
        audioBuffer: FloatArray,
        languageCode: String = "hi"
    ): AppResult<String> = inferenceLock.withLock { transcribeUnlocked(audioBuffer, languageCode) }

    private suspend fun transcribeUnlocked(
        audioBuffer: FloatArray,
        languageCode: String
    ): AppResult<String> = withContext(Dispatchers.Default) {
```

The function bodies are unchanged; only their headers move. The existing `return@withContext` labels keep working. **Never call `ensureLoaded` or `transcribe` from inside the other while holding the lock** — `Mutex` is not re-entrant. Neither currently does.

### Step 2 — `AudioCaptureModule.kt`: a single segment queue

**2a.** ANCHOR:

```kotlin
    private var captureJob: Job? = null
```

REPLACEMENT:

```kotlin
    private var captureJob: Job? = null

    /** One pending STT job (T65). [done] completes after onSpeechReady returns. */
    private class Segment(
        val audio: FloatArray,
        val language: String,
        val done: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    )

    /**
     * All finished speech goes through this queue and ONE consumer (T65). Previously
     * onSpeechReady -- which runs the whole STT inference -- was called inline in the capture
     * loop, so the loop stopped reading the microphone during inference and AudioRecord's small
     * buffer overflowed. The queue keeps capture reading and keeps phrases in spoken order.
     */
    private val segmentQueue =
        kotlinx.coroutines.channels.Channel<Segment>(kotlinx.coroutines.channels.Channel.UNLIMITED)

    init {
        scope.launch {
            for (segment in segmentQueue) {
                try {
                    onSpeechReady(segment.audio, segment.language)
                } catch (e: Exception) {
                    Log.e(TAG, "segment processing failed: ${e.message}", e)
                } finally {
                    segment.done?.complete(Unit)
                }
            }
        }
    }

    /** Queue [audio] behind any phrases already pending and suspend until it is processed. */
    suspend fun submitAndAwait(audio: FloatArray, language: String) {
        val done = kotlinx.coroutines.CompletableDeferred<Unit>()
        segmentQueue.send(Segment(audio, language, done))
        done.await()
    }
```

`init` must come **after** the `segmentQueue` declaration (it does, if you paste the block as one piece). `scope` is declared above `captureJob`, so it is already initialised.

**2b.** ANCHOR:

```kotlin
                readySegment?.let { combined ->
                    Log.d(TAG, "Speech segment complete: ${combined.size} samples")
                    onSpeechReady(combined, currentLanguage)
                }
```

REPLACEMENT:

```kotlin
                readySegment?.let { combined ->
                    Log.d(TAG, "Speech segment complete: ${combined.size} samples")
                    // Hand off; never run inference on the capture loop (T65).
                    segmentQueue.trySend(Segment(combined, currentLanguage))
                }
```

### Step 3 — `AudioCaptureModule.kt`: shorter cut while PTT is held

Holding the button already says "this is speech", so a shorter pause can safely end a phrase. ANCHOR (this is T41's REPLACEMENT; if it is not present, T41 is not done — **stop**):

```kotlin
    private val silenceChunksForEndOfSpeech: Int
        get() = if (speechChunkCount >= confidentSpeechChunks) silenceChunksShort
                else silenceChunksLong
```

REPLACEMENT:

```kotlin
    /** Set by the service while the PTT button is held (T65). */
    @Volatile var pttHeld: Boolean = false
    /** 400 ms: the phrase cut used while PTT is held and enough speech has been captured. */
    private val silenceChunksPtt = 4

    private val silenceChunksForEndOfSpeech: Int
        get() = when {
            pttHeld && speechChunkCount >= confidentSpeechChunks -> silenceChunksPtt
            speechChunkCount >= confidentSpeechChunks -> silenceChunksShort
            else -> silenceChunksLong
        }
```

### Step 4 — `ITantraForegroundService.kt`: route the release flush through the queue

**4a.** ANCHOR (the whole `startPTT` function):

```kotlin
    fun startPTT() {
        if (!audioCaptureModule.isRunning) {
            audioCaptureModule.startCapture()
            _pipelineStage.value = PipelineStage.LISTENING
        }
    }
```

REPLACEMENT:

```kotlin
    fun startPTT() {
        audioCaptureModule.pttHeld = true
        if (!audioCaptureModule.isRunning) {
            audioCaptureModule.startCapture()
            _pipelineStage.value = PipelineStage.LISTENING
        }
    }
```

**4b.** ANCHOR (the whole `stopPTT` function):

```kotlin
    fun stopPTT() {
        serviceScope.launch {
            val buffer = audioCaptureModule.flushAndTranscribe()
            if (buffer != null && buffer.isNotEmpty()) {
                _pipelineStage.value = PipelineStage.TRANSCRIBING
                sttModule.ensureLoaded(sttLanguage)
                sttModule.transcribe(buffer, sttLanguage)
                // onSTTResult (audioCallbacks) takes it from TRANSCRIBING through TRANSMITTING
                // and back to IDLE; only reset here if transcription produced no result at all.
                if (_pipelineStage.value == PipelineStage.TRANSCRIBING) _pipelineStage.value = PipelineStage.IDLE
            } else {
                _pipelineStage.value = PipelineStage.IDLE
            }
            audioCaptureModule.stopCapture()
        }
    }
```

REPLACEMENT:

```kotlin
    fun stopPTT() {
        audioCaptureModule.pttHeld = false
        serviceScope.launch {
            val buffer = audioCaptureModule.flushAndTranscribe()
            // Stop the microphone before waiting on STT, so nothing said after release is queued.
            audioCaptureModule.stopCapture()
            if (buffer != null && buffer.isNotEmpty()) {
                _pipelineStage.value = PipelineStage.TRANSCRIBING
                // Same queue as mid-hold phrases (T65): keeps spoken order, avoids concurrent
                // inference, and goes through onSpeechReady so the flush gets telemetry too.
                audioCaptureModule.submitAndAwait(buffer, sttLanguage)
                // onSTTResult (audioCallbacks) takes it from TRANSCRIBING through TRANSMITTING
                // and back to IDLE; only reset here if transcription produced no result at all.
                if (_pipelineStage.value == PipelineStage.TRANSCRIBING) _pipelineStage.value = PipelineStage.IDLE
            } else {
                _pipelineStage.value = PipelineStage.IDLE
            }
        }
    }
```

If your copy of `stopPTT` differs from the ANCHOR (for example because telemetry lines were added), **stop and report** rather than merging by hand.

### VERIFY

1. `./gradlew :app:compileDebugKotlin`
2. Hold PTT on phone A and say two sentences with a clear half-second pause between them, **still holding**. Phone B must start speaking sentence 1 **before** A releases.
3. Say one long sentence without pausing: exactly one message is sent, on release.
4. The telemetry CSV shows one row per phrase. `adb logcat -s STTModule:*` shows inferences one after another, never overlapping.

### DO NOT

- Do not go below 400 ms (`silenceChunksPtt = 4`). Shorter cuts split words and cost accuracy.
- Do not start T50 (streaming inference) until this is measured. It may make T50 unnecessary.

---

## T66 🎨 · SOS: send alerts from the UI, and show received alerts

**Files:** `core/service/ITantraForegroundService.kt`, `ui/MainViewModel.kt`, `ui/screen/TransceiverScreen.kt`, the top-level Compose host (`MainActivity.kt`)
**Criterion:** REQ — *"alert type messages will be announced at highest volume non-interruptible"* cannot be demonstrated today, because no UI sends an ALERT (`broadcastAlert()` has no caller) and no UI shows one (`alertFlow` has no collector).
**Depends on:** T69 (Step 1 below calls its `transmit()` helper).

### Step 1 — service: correct language on alerts, and "speak next message as alert"

**1a.** Replace `broadcastAlert`. ANCHOR (the whole function):

```kotlin
    fun broadcastAlert(text: String) {
        val alert = TransceiverMessage(
            type = MessageType.ALERT,
            text = text,
            srcLang = sttLanguage,
            dstLang = ttsLanguage,
            senderId = deviceId,
            timestamp = System.currentTimeMillis(),
            sequence = ++sequenceCounter,
            direction = Direction.SENT
        )
        appendMessage(alert)
        if (isBluetoothFallbackActive) {
            bluetoothManager.send(alert)
        } else {
            socketTransport.broadcast(alert)
        }
    }
```

(If T69 is done, the last five lines already read `transmit(alert)` — then the ANCHOR ends with `        transmit(alert)\n    }` instead. Either form is fine.)

REPLACEMENT:

```kotlin
    /**
     * Broadcast an ALERT. [lang] is the language [text] is written in: the receiver chooses its
     * voice from srcLang (see T43), so a preset that fell back to English must say "en", not the
     * sender's STT language.
     */
    fun broadcastAlert(text: String, lang: String = sttLanguage) {
        val alert = TransceiverMessage(
            type = MessageType.ALERT,
            text = text,
            srcLang = lang,
            dstLang = lang,
            senderId = deviceId,
            timestamp = System.currentTimeMillis(),
            sequence = ++sequenceCounter,
            direction = Direction.SENT
        )
        appendMessage(alert)
        transmit(alert)
    }
```

**1b.** Add the flag. ANCHOR:

```kotlin
    fun setTTSLanguage(lang: String) { ttsLanguage = lang }
```

REPLACEMENT:

```kotlin
    fun setTTSLanguage(lang: String) { ttsLanguage = lang }

    /** When true, the next STT result is sent as an ALERT instead of SPEECH, then resets (T66). */
    @Volatile var sendNextAsAlert: Boolean = false
```

**1c.** Use it. ANCHOR (inside `onSTTResult`; appears exactly once in the file):

```kotlin
                    type = MessageType.SPEECH,
```

REPLACEMENT:

```kotlin
                    type = if (sendNextAsAlert) MessageType.ALERT else MessageType.SPEECH,
```

**1d.** Reset it after one use. ANCHOR:

```kotlin
                appendMessage(message)
                // Transmit over network
```

REPLACEMENT:

```kotlin
                sendNextAsAlert = false
                appendMessage(message)
                // Transmit over network
```

### Step 2 — `MainViewModel.kt`

**2a.** State. ANCHOR:

```kotlin
    val pipelineStage: StateFlow<ITantraForegroundService.PipelineStage> = _pipelineStage.asStateFlow()
```

REPLACEMENT:

```kotlin
    val pipelineStage: StateFlow<ITantraForegroundService.PipelineStage> = _pipelineStage.asStateFlow()

    // ── SOS / alerts (T66) ──
    /** The most recent received ALERT not yet acknowledged; null when none is showing. */
    private val _incomingAlert = MutableStateFlow<AlertEvent?>(null)
    val incomingAlert: StateFlow<AlertEvent?> = _incomingAlert.asStateFlow()

    private val _alertArmed = MutableStateFlow(false)
    /** True while the next spoken PTT message will be sent as an ALERT. */
    val alertArmed: StateFlow<Boolean> = _alertArmed.asStateFlow()

    fun dismissAlert() { _incomingAlert.value = null }

    /** Send a preset alert. Uses [languageCode]'s text if the template has it, else English. */
    fun sendPresetAlert(template: AlertTemplate, languageCode: String) {
        val lang = if (template.templateText.containsKey(languageCode)) languageCode else "en"
        val text = template.templateText.getValue(lang)
        foregroundService?.broadcastAlert(text, lang)
    }

    /** Arm or disarm "send my next spoken message as an ALERT". */
    fun setAlertArmed(armed: Boolean) {
        _alertArmed.value = armed
        foregroundService?.sendNextAsAlert = armed
    }
```

Add imports if missing: `com.itantra.domain.model.AlertEvent`, `com.itantra.domain.model.AlertTemplate`.

**2b.** Collect alerts from the service. ANCHOR:

```kotlin
                viewModelScope.launch { it.isBluetoothListening.collect { b -> _isBluetoothListening.value = b } }
```

REPLACEMENT:

```kotlin
                viewModelScope.launch { it.isBluetoothListening.collect { b -> _isBluetoothListening.value = b } }
                viewModelScope.launch { it.alertFlow.collect { e -> _incomingAlert.value = e } }
```

**2c.** The armed flag resets itself in the service after one message; mirror that in the UI. In `stopTransceiverPtt()`, after `foregroundService?.stopPTT()`, add `_alertArmed.value = false`.

### Step 3 — UI 🎨 (open the files and match their existing patterns; do not invent a new style)

**Sender, `TransceiverScreen.kt`:**
- An **SOS** button near the PTT button, using the same button component and shape the screen already uses for primary actions. It opens a dialog listing `AlertTemplate.entries` by `displayName`.
- Tapping a template shows a confirmation ("Send '<displayName>' alert to all connected peers?"). On confirm: `viewModel.sendPresetAlert(template, <the current STT language code this screen already holds>)`.
- A toggle labelled **"Next message is an ALERT"**, styled like the existing auto-detect toggle, bound to `alertArmed` / `setAlertArmed`.

**Receiver, `MainActivity.kt` (top level, so it appears on every screen):**
- Collect `viewModel.incomingAlert`. When non-null, show a full-screen `Dialog` (`DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false)`) with: "ALERT", the message text in large type, the sender id, the time from `receivedAt`, and one **Acknowledge** button that calls `viewModel.dismissAlert()`.
- Use the theme's existing colours. Do not add new colours.

### Follow-up for a human (not for an agent)

`AlertTemplate` has text only for `hi`, `en`, `ta`, `te`, `bn`. The other five languages fall back to English. Adding `gu`, `mr`, `kn`, `ml`, `or` text needs a native speaker to write and check it. **An agent must not generate these translations.**

### VERIFY

1. `./gradlew :app:compileDebugKotlin`
2. Phone A: SOS → Medical Emergency → confirm. Phone B: alarm-volume speech **and** the full-screen alert, which stays until Acknowledge.
3. Phone A: turn on "Next message is an ALERT", hold PTT, speak. Phone B treats it as an alert. The next PTT message is normal speech again.

### DO NOT

- Do not make the alert dialog dismissible by back or outside tap.
- Do not split ALERT text for streaming TTS (see T40's DO NOT).

---

## T67 🎨 · Received speech is kept as a replayable voice note

**Files:** new `core/audio/VoiceNoteStore.kt`, `core/service/ITantraForegroundService.kt`, `ui/MainViewModel.kt`, `ui/screen/TransceiverScreen.kt`
**Criterion:** REQ — *"converted into intelligible speech which will be played as a voice note"*
**Depends on:** T13 (the `play(samples, sampleRate, …)` signature). T13 is already in the working tree; commit it first.

### Step 1 — create `core/audio/VoiceNoteStore.kt` exactly

```kotlin
package com.itantra.core.audio

import android.content.Context
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Keeps each received message's synthesized speech as a 16-bit mono WAV, so it can be replayed
 * like a voice note (T67). Files live in filesDir/voicenotes/, named by sender and sequence so the
 * UI can find a message's note without any new field on TransceiverMessage.
 *
 * Only reads files written by [save]; this is not a general WAV parser.
 */
object VoiceNoteStore {

    private const val DIR = "voicenotes"
    /** Oldest notes are deleted beyond this count. ~200 KB each for a few seconds of speech. */
    private const val MAX_NOTES = 200

    fun fileFor(context: Context, senderId: String, sequence: Int): File {
        val safeSender = senderId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return File(File(context.filesDir, DIR), "${safeSender}_$sequence.wav")
    }

    fun save(context: Context, senderId: String, sequence: Int, samples: FloatArray, sampleRate: Int): File? =
        runCatching {
            val file = fileFor(context, senderId, sequence)
            file.parentFile?.mkdirs()
            val dataBytes = samples.size * 2
            val buf = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
            buf.put("RIFF".toByteArray(Charsets.US_ASCII)); buf.putInt(36 + dataBytes)
            buf.put("WAVE".toByteArray(Charsets.US_ASCII))
            buf.put("fmt ".toByteArray(Charsets.US_ASCII)); buf.putInt(16)
            buf.putShort(1)                 // PCM
            buf.putShort(1)                 // mono
            buf.putInt(sampleRate)
            buf.putInt(sampleRate * 2)      // byte rate
            buf.putShort(2)                 // block align
            buf.putShort(16)                // bits per sample
            buf.put("data".toByteArray(Charsets.US_ASCII)); buf.putInt(dataBytes)
            for (s in samples) buf.putShort((s.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort())
            file.writeBytes(buf.array())
            prune(context)
            file
        }.getOrNull()

    /** Returns (samples, sampleRate) for a file written by [save], or null. */
    fun load(file: File): Pair<FloatArray, Int>? = runCatching {
        val bb = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        val sampleRate = bb.getInt(24)
        val n = bb.getInt(40) / 2
        FloatArray(n) { i -> bb.getShort(44 + i * 2) / Short.MAX_VALUE.toFloat() } to sampleRate
    }.getOrNull()

    private fun prune(context: Context) {
        val files = File(context.filesDir, DIR).listFiles()?.sortedBy { it.lastModified() } ?: return
        files.dropLast(MAX_NOTES).forEach { it.delete() }
    }
}
```

### Step 2 — save on receive (`ITantraForegroundService.kt`, inside `onTextReceived`)

The exact code around the `play` call depends on which tasks are done. Find the `audioPlayback.play(` call inside `onTextReceived` and use the matching row:

| State of `onTextReceived` | What to add |
| --- | --- |
| One `synth` and one `audioPlayback.play(synth.samples, synth.sampleRate, …)` (T13 done, T40 not) | On the line **before** that `audioPlayback.play(`, add the snippet below with `SAMPLES = synth.samples` and `RATE = synth.sampleRate` |
| A `for (segment in segments)` loop (T40 done) | Before the loop: `val noteParts = mutableListOf<FloatArray>(); var noteRate = 0`. Inside the loop, right after `val part = …`: `noteParts.add(part.samples); noteRate = part.sampleRate`. After the loop: the snippet below with `SAMPLES = concatenate(noteParts)` and `RATE = noteRate`, guarded by `if (noteParts.isNotEmpty())` |
| `val waveform = …` and `audioPlayback.play(waveform, isAlert)` (T13 not done) | **Stop.** Do T13 first |

Snippet (replace `SAMPLES` and `RATE`):

```kotlin
                    // Keep it as a replayable voice note (T67). Off the playback path.
                    val noteSamples = SAMPLES
                    val noteRate = RATE
                    serviceScope.launch(Dispatchers.IO) {
                        com.itantra.core.audio.VoiceNoteStore.save(
                            this@ITantraForegroundService, message.senderId, message.sequence, noteSamples, noteRate
                        )
                    }
```

For the T40 row, `concatenate(noteParts)` is:

```kotlin
FloatArray(noteParts.sumOf { it.size }).also { out ->
    var o = 0
    for (p in noteParts) { p.copyInto(out, o); o += p.size }
}
```

(In the T40 row, `noteRate` is already declared by you; name the snippet's local `noteRateFinal` instead to avoid the clash.)

### Step 3 — replay (`ITantraForegroundService.kt`)

ANCHOR:

```kotlin
    fun setTTSLanguage(lang: String) { ttsLanguage = lang }
```

REPLACEMENT (if T66 already added lines after it, insert this directly after the `setTTSLanguage` line and leave T66's lines in place):

```kotlin
    fun setTTSLanguage(lang: String) { ttsLanguage = lang }

    /** Replays a stored voice note through the normal playback path (T67). False if none exists. */
    fun replayVoiceNote(senderId: String, sequence: Int): Boolean {
        val file = com.itantra.core.audio.VoiceNoteStore.fileFor(this, senderId, sequence)
        if (!file.exists()) return false
        serviceScope.launch(Dispatchers.IO) {
            val (samples, rate) = com.itantra.core.audio.VoiceNoteStore.load(file) ?: return@launch
            audioPlayback.play(samples, rate)
        }
        return true
    }
```

### Step 4 — `MainViewModel.kt`

Add beside `stopTransceiverPtt()`:

```kotlin
    /** Replay a received message's voice note (T67). Returns false if it is not stored yet. */
    fun replayVoiceNote(message: TransceiverMessage): Boolean =
        foregroundService?.replayVoiceNote(message.senderId, message.sequence) ?: false
```

Add the import `com.itantra.domain.model.TransceiverMessage` if missing.

### Step 5 — UI 🎨 (`TransceiverScreen.kt`)

On each message bubble whose `direction == Direction.RECEIVED`, add a small play icon button using an icon style already used in that file. On tap: `if (!viewModel.replayVoiceNote(msg))` show the screen's existing snackbar/toast pattern with "Voice note not ready yet". Show the note's duration only if it is trivial to get; it is optional.

### VERIFY

1. `./gradlew :app:compileDebugKotlin`
2. Receive three messages. `adb shell run-as com.itantra.debug ls files/voicenotes` lists three `.wav` files.
3. Tap play on the second message: it plays again, and it waits its turn if something else is playing (T38).
4. `adb shell run-as com.itantra.debug cat files/voicenotes/<one>.wav > note.wav` plays correctly on a laptop.

### DO NOT

- Do not add fields to `TransceiverMessage` or the proto for this.
- Do not save on the sender side. The PS describes the receiver's TTS output as the voice note.

---

## T68 · (Stretch) ESP32 receiver — the "embedded device" in the PS

**Files:** new `firmware/esp32_receiver/esp32_receiver.ino`, `core/network/BluetoothRFCOMMManager.kt`
**Criterion:** REQ — *"stream the data through wifi/Bluetooth connected embedded device or another phone"*
**Depends on:** T69 (otherwise the phone never sends to a Bluetooth peer it connected to as a client).

### Hardware (human)

- An **original ESP32** board (ESP32-WROOM-32 / DevKitC). **Not** ESP32-S2, S3, C3 or C6 — those have no Bluetooth Classic, and the app's transport is Bluetooth Classic RFCOMM.
- Optional: an active buzzer on GPIO 25 → GND. The on-board LED on GPIO 2 is used as the alert light.
- Arduino IDE with the "esp32 by Espressif Systems" board package.

### Step 1 — app: fall back to the standard serial UUID

The app connects with its own UUID `6E400001-B5A3-F393-E0A9-E50E24DCCA9E`. An ESP32 running `BluetoothSerial` advertises the standard Serial Port Profile UUID instead, so the connection fails.

**1a.** ANCHOR (companion object of `BluetoothRFCOMMManager`):

```kotlin
        private val SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
```

REPLACEMENT:

```kotlin
        private val SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        /** Standard Bluetooth Serial Port Profile UUID. Embedded receivers (T68) listen on this. */
        private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
```

**1b.** ANCHOR (inside `connectToDevice`):

```kotlin
                val socket = device.createRfcommSocketToServiceRecord(SERVICE_UUID)
                socket.connect()
```

REPLACEMENT:

```kotlin
                val socket = run {
                    val primary = device.createRfcommSocketToServiceRecord(SERVICE_UUID)
                    try {
                        primary.connect()
                        primary
                    } catch (e: IOException) {
                        // Not another iTantra phone? An embedded receiver (T68) exposes the
                        // standard SPP UUID instead. Retry once on it before giving up.
                        runCatching { primary.close() }
                        Log.w(TAG, "iTantra UUID connect failed (${e.message}); retrying on SPP")
                        device.createRfcommSocketToServiceRecord(SPP_UUID).also { it.connect() }
                    }
                }
```

**1c.** Fix a partial-read bug that embedded senders and fragmented RFCOMM packets trigger. ANCHOR (inside `handleSocket`):

```kotlin
                val read = input.read(lengthBuffer)
                if (read < 4) break
```

REPLACEMENT:

```kotlin
                // RFCOMM may deliver the 4-byte header in pieces; read until complete.
                var got = 0
                while (got < 4) {
                    val n = input.read(lengthBuffer, got, 4 - got)
                    if (n < 0) break
                    got += n
                }
                if (got < 4) break
```

### Step 2 — the firmware

Create `firmware/esp32_receiver/esp32_receiver.ino` exactly:

```cpp
// iTantra embedded receiver (T68).
// Accepts the app's frames over Bluetooth Classic SPP: 4-byte big-endian length + a
// TransceiverMessage protobuf (see app/src/main/proto/itantra.proto). Prints the text on the
// serial monitor and flashes/buzzes on ALERT. Protobuf is decoded by hand (only the fields we
// need) so no extra library is required.
#include "BluetoothSerial.h"

BluetoothSerial SerialBT;
const int LED_PIN = 2;       // on-board LED on most ESP32 DevKitC boards
const int BUZZER_PIN = 25;   // active buzzer to GND (optional)
const size_t MAX_PAYLOAD = 4096;
uint8_t payload[MAX_PAYLOAD];

// Blocks until n bytes arrive or the link drops. Returns false on disconnect.
bool readExact(uint8_t* buf, size_t n) {
  size_t got = 0;
  while (got < n) {
    if (!SerialBT.hasClient()) return false;
    int c = SerialBT.read();
    if (c < 0) { delay(1); continue; }
    buf[got++] = (uint8_t)c;
  }
  return true;
}

bool readVarint(const uint8_t* p, size_t len, size_t& i, uint64_t& out) {
  out = 0;
  for (int shift = 0; shift < 64 && i < len; shift += 7) {
    uint8_t b = p[i++];
    out |= (uint64_t)(b & 0x7F) << shift;
    if (!(b & 0x80)) return true;
  }
  return false;
}

void alertSignal() {
  for (int k = 0; k < 6; k++) {
    digitalWrite(LED_PIN, HIGH); digitalWrite(BUZZER_PIN, HIGH); delay(150);
    digitalWrite(LED_PIN, LOW);  digitalWrite(BUZZER_PIN, LOW);  delay(100);
  }
}

void setup() {
  Serial.begin(115200);
  pinMode(LED_PIN, OUTPUT);
  pinMode(BUZZER_PIN, OUTPUT);
  SerialBT.begin("iTantra-ESP32");
  Serial.println("Ready. Pair the phone with 'iTantra-ESP32'.");
}

void loop() {
  uint8_t hdr[4];
  if (!readExact(hdr, 4)) { delay(50); return; }
  uint32_t len = ((uint32_t)hdr[0] << 24) | ((uint32_t)hdr[1] << 16) | ((uint32_t)hdr[2] << 8) | hdr[3];
  if (len == 0) return;
  if (len > MAX_PAYLOAD) {                 // too big for this board: skip it
    for (uint32_t k = 0; k < len; k++) { uint8_t d; if (!readExact(&d, 1)) return; }
    return;
  }
  if (!readExact(payload, len)) return;

  uint64_t type = 0;                        // 0 SPEECH, 1 ALERT, 2 ACK, 3 PING
  String text, srcLang, sender;
  size_t i = 0;
  while (i < len) {
    uint64_t key;
    if (!readVarint(payload, len, i, key)) break;
    uint32_t field = key >> 3, wire = key & 7;
    if (wire == 0) {                        // varint: type(1), timestamp(6), sequence(8)
      uint64_t v; if (!readVarint(payload, len, i, v)) break;
      if (field == 1) type = v;
    } else if (wire == 2) {                 // length-delimited: text(2), src_lang(3), dst_lang(4), sender_id(5)
      uint64_t l; if (!readVarint(payload, len, i, l) || i + l > len) break;
      String s; s.reserve(l);
      for (uint64_t k = 0; k < l; k++) s += (char)payload[i + k];
      if (field == 2) text = s; else if (field == 3) srcLang = s; else if (field == 5) sender = s;
      i += l;
    } else if (wire == 5) {                 // fixed32: confidence(7)
      i += 4;
    } else if (wire == 1) {                 // fixed64: not used by this schema, skip safely
      i += 8;
    } else {
      break;                                // unknown wire type: stop parsing this frame
    }
  }

  if (type == 2 || type == 3) return;       // ACK / PING: nothing to show
  Serial.printf("[%s] %s (%s): %s\n", type == 1 ? "ALERT" : "SPEECH", sender.c_str(), srcLang.c_str(), text.c_str());
  if (type == 1) alertSignal();
  else { digitalWrite(LED_PIN, HIGH); delay(80); digitalWrite(LED_PIN, LOW); }
}
```

**Honest limitation for the slide:** the serial monitor shows UTF-8 correctly, but small OLED displays cannot render Indic scripts. If a display is added, show "ALERT from <sender>" rather than the Indic text.

### VERIFY

1. Flash the sketch; the serial monitor prints `Ready`.
2. Phone: pair with `iTantra-ESP32` in Android Bluetooth settings, then connect from the app's Bluetooth device list. Logcat shows `retrying on SPP`, then a connection.
3. Speak on the phone (PTT): the text appears on the serial monitor. Send an SOS (T66): LED/buzzer pattern plays.

### DO NOT

- Do not change the app's own `SERVICE_UUID`; phone-to-phone must keep working.
- Do not add nanopb or other protobuf libraries to the firmware; the hand decoder is intentional.

---

## T69 · Send on every live transport, and drop duplicates on receive

**Files:** `core/network/BluetoothRFCOMMManager.kt`, `core/service/ITantraForegroundService.kt`
**Criterion:** REQ — reliable Bluetooth in both directions
**Depends on:** nothing.

### Why

`onSTTResult` sends over Bluetooth only when `isBluetoothFallbackActive` is true. That flag is set only when **this** phone started the Bluetooth server (Host Beacon, or three Wi-Fi Direct failures). A phone that connected **outbound** as the Bluetooth client has the flag false, so it sends over TCP to nobody. Over Bluetooth the link is one-directional unless both phones have Host Beacon on. The fix is to send on every transport that has a peer, and to ignore the second copy if a peer is reachable on both.

### Step 1 — `BluetoothRFCOMMManager.kt`

ANCHOR:

```kotlin
    fun send(message: TransceiverMessage, targetDeviceId: String? = null) {
```

REPLACEMENT:

```kotlin
    /** True if at least one Bluetooth peer is connected (T69). */
    fun hasConnections(): Boolean = connectedSockets.isNotEmpty()

    fun send(message: TransceiverMessage, targetDeviceId: String? = null) {
```

### Step 2 — `ITantraForegroundService.kt`: a single send path

**2a.** Add the helper and the dedup set. ANCHOR:

```kotlin
    private var isBluetoothFallbackActive = false
```

REPLACEMENT:

```kotlin
    private var isBluetoothFallbackActive = false

    /**
     * Send on every transport that currently has a peer (T69). Previously Bluetooth was used only
     * when this phone had started the Bluetooth server, so a phone connected as the Bluetooth
     * client sent over TCP to nobody.
     */
    private fun transmit(message: TransceiverMessage) {
        if (bluetoothManager.hasConnections()) bluetoothManager.send(message)
        socketTransport.broadcast(message)
    }

    /**
     * Recently received message keys, so a peer reachable over both Wi-Fi Direct and Bluetooth is
     * heard once (T69). The key includes the timestamp because sequence numbers restart at 1 when
     * the sender's app restarts. Bounded to 256 entries; guard every access with synchronized.
     */
    private val seenMessages: MutableSet<String> = java.util.Collections.newSetFromMap(
        object : java.util.LinkedHashMap<String, Boolean>(64, 0.75f, false) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean =
                size > 256
        }
    )
```

**2b.** Use it in `onSTTResult`. ANCHOR:

```kotlin
                if (isBluetoothFallbackActive) {
                    bluetoothManager.send(message)
                } else {
                    socketTransport.broadcast(message)
                }
```

REPLACEMENT:

```kotlin
                transmit(message)
```

**2c.** Use it in `broadcastAlert`. ANCHOR:

```kotlin
        if (isBluetoothFallbackActive) {
            bluetoothManager.send(alert)
        } else {
            socketTransport.broadcast(alert)
        }
```

REPLACEMENT:

```kotlin
        transmit(alert)
```

(If T66 is already done, this block is already gone — skip 2c.)

**2d.** Drop duplicates. ANCHOR:

```kotlin
        override fun onTextReceived(message: TransceiverMessage) {
```

REPLACEMENT:

```kotlin
        override fun onTextReceived(message: TransceiverMessage) {
            val dedupKey = "${message.senderId}:${message.sequence}:${message.timestamp}"
            val isNew = synchronized(seenMessages) { seenMessages.add(dedupKey) }
            if (!isNew) {
                Log.d(TAG, "Duplicate $dedupKey dropped (reached us on two transports)")
                return
            }
```

Do not remove `isBluetoothFallbackActive`: it still controls whether the Bluetooth **server** is listening.

### VERIFY

1. `./gradlew :app:compileDebugKotlin`
2. Phone A: Host Beacon on. Phone B: Host Beacon **off**, connect to A over Bluetooth. Speak on B: A must hear it (this failed before). Speak on A: B hears it.
3. Two phones connected over both Wi-Fi Direct and Bluetooth: each message is heard once; logcat shows `Duplicate … dropped` for the second copy.

### DO NOT

- Do not remove either transport's `send`/`broadcast`. Both may be live.
- Do not key the dedup on `sequence` alone.

---

## T70 · Serialise `TTSModule` (the same fix T65 made to `STTModule`)

**File:** `core/audio/TTSModule.kt`
**Criterion:** EFF (RAM), LAT
**Depends on:** nothing. Do it before T45.

### Why

`ITantraForegroundService.onTextReceived` starts a new coroutine for every received message, and each calls `ttsModule.synthesize()`. `TTSModule.ttsCache` is a plain `HashMap` and `getOrLoadTts()` has no lock. When two messages arrive while the voice is still loading, both miss the cache and both load the voice: double RAM, and the first instance is overwritten in the map and never released. The two calls can also run sherpa-onnx `generate()` on one instance at once. `docs/latency-evidence/receiver_logcat.txt` fits this (two syntheses finishing 92 ms apart after arriving 374 ms apart) but does not prove it — that capture has no `TTSModule` tag.

Serialising synthesis costs nothing in practice: the playback queue (T38) already plays messages one at a time.

### Step 1 — imports

ANCHOR:

```kotlin
import kotlinx.coroutines.withContext
```

REPLACEMENT:

```kotlin
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
```

### Step 2 — the lock

ANCHOR:

```kotlin
    private val ttsCache = mutableMapOf<String, OfflineTts>()
```

REPLACEMENT:

```kotlin
    private val ttsCache = mutableMapOf<String, OfflineTts>()

    /** One synthesis or voice load at a time (T70). The cache is a plain HashMap, and two
     *  messages arriving during a cold load would otherwise both load the same voice. */
    private val ttsLock = kotlinx.coroutines.sync.Mutex()
```

### Step 3 — wrap `synthesize`

ANCHOR:

```kotlin
    suspend fun synthesize(text: String, languageCode: String): SynthesisResult? =
        withContext(Dispatchers.Default) {
```

REPLACEMENT:

```kotlin
    suspend fun synthesize(text: String, languageCode: String): SynthesisResult? =
        ttsLock.withLock { synthesizeUnlocked(text, languageCode) }

    private suspend fun synthesizeUnlocked(text: String, languageCode: String): SynthesisResult? =
        withContext(Dispatchers.Default) {
```

The body is unchanged; `return@withContext` labels keep working. The KDoc stays above the public `synthesize`. `Mutex` is not re-entrant: nothing inside the body may call `synthesize` or `warmUp` (nothing does).

### VERIFY

1. `./gradlew :app:compileDebugKotlin`
2. Cold-start the receiver. From the sender, send two short PTT messages within about half a second. `adb logcat -s TTSModule:*` on the receiver shows **one** `sherpa-onnx TTS loaded for 'hi'` line, not two. Both messages still play, in order.

### DO NOT

- Do not lock `unloadLanguage()` or `release()` in this task (they are not `suspend`). Note it in the PR if you think they need it.

---

## T71 · Make the telemetry stamps mean what the rubric asks

**Files:** `core/telemetry/Telemetry.kt`, `core/audio/AudioCaptureModule.kt`, `core/service/ITantraForegroundService.kt`
**Criterion:** LAT, DOC
**Depends on:** T65 (the `Segment` class is its code).

### Why

Two stamps are in the wrong place today (found in the `docs/latency-evidence/` run):

1. `captureEndNs` ("speech ended") is stamped in the service's `onSpeechReady` — which, since T65, runs when the queue **picks the phrase up**, not when the VAD cut it. So `stt_ms` leaves out queue wait: phrase 2 waited ~1.2 s behind phrase 1, and its `stt_ms` of 360 ms does not show that. The rubric's first metric is *words said → STT complete*, which must include it.
2. `ensureLoaded()` runs after that stamp and before feature extraction, so a model load lands in `feature_ms`, `stt_ms` **and** `rtf`. Phrase 1's `feature_ms` of 2439 ms was 2306 ms of model load.

Fix: stamp the cut time in `AudioCaptureModule`, add a stamp after the model is ready, and report the waiting (queue + load) as its own column. Also add a synthesis-only TTS column, because `tts_ms` includes waiting in the playback queue.

### Step 1 — `core/telemetry/Telemetry.kt`

**1a.** ANCHOR:

```kotlin
        var featureDoneNs: Long = 0,
```

REPLACEMENT:

```kotlin
        /** When STT processing actually started: after any queue wait and model load (T71). */
        var sttStartNs: Long = 0,
        var featureDoneNs: Long = 0,
```

**1b.** ANCHOR:

```kotlin
        /** Feature extraction only, milliseconds. */
        val featureMs: Double get() = ns(captureEndNs, featureDoneNs)
```

REPLACEMENT:

```kotlin
        /** Feature extraction only, milliseconds. */
        val featureMs: Double get() = ns(procStartNs, featureDoneNs)
        /** Queue wait + model load before STT processing began, milliseconds (T71). */
        val waitMs: Double get() = ns(captureEndNs, sttStartNs)
        /** Receive -> synthesis finished, milliseconds. Excludes playback-queue wait, which
         *  ttsLatencyMs includes (T71). */
        val ttsSynthMs: Double get() = ns(rxNs, ttsDoneNs)
        /** Start of processing: sttStartNs when recorded, else the older captureEndNs stamp. */
        private val procStartNs: Long get() = if (sttStartNs != 0L) sttStartNs else captureEndNs
```

**1c.** ANCHOR:

```kotlin
            if (audioDurationMs <= 0) 0.0 else (sttLatencyMs / audioDurationMs)
```

REPLACEMENT:

```kotlin
            // Processing time only: queue wait and model load are not the model's speed (T71).
            if (audioDurationMs <= 0) 0.0 else (ns(procStartNs, inferDoneNs) / audioDurationMs)
```

**1d.** CSV header. ANCHOR:

```kotlin
                f.appendText("id,lang,audio_ms,chars,stt_ms,feature_ms,infer_ms,rtf,tts_ms,tts_audio_ms\n")
```

REPLACEMENT:

```kotlin
                f.appendText("id,lang,audio_ms,chars,stt_ms,wait_ms,feature_ms,infer_ms,rtf,tts_ms,tts_synth_ms,tts_audio_ms\n")
```

**1e.** CSV row. ANCHOR:

```kotlin
                "%d,%s,%d,%d,%.1f,%.1f,%.1f,%.4f,%.1f,%d\n".format(
                    u.id, u.lang, u.audioDurationMs, u.charCount,
                    u.sttLatencyMs, u.featureMs, u.inferMs, u.rtf,
                    u.ttsLatencyMs, u.ttsAudioDurationMs
                )
```

REPLACEMENT:

```kotlin
                "%d,%s,%d,%d,%.1f,%.1f,%.1f,%.1f,%.4f,%.1f,%.1f,%d\n".format(
                    u.id, u.lang, u.audioDurationMs, u.charCount,
                    u.sttLatencyMs, u.waitMs, u.featureMs, u.inferMs, u.rtf,
                    u.ttsLatencyMs, u.ttsSynthMs, u.ttsAudioDurationMs
                )
```

**1f.** Log line. ANCHOR:

```kotlin
        Log.d(TAG, "utt=${u.id} lang=${u.lang} rtf=%.3f stt=%.0fms feat=%.0fms infer=%.0fms tts=%.0fms"
            .format(u.rtf, u.sttLatencyMs, u.featureMs, u.inferMs, u.ttsLatencyMs))
```

REPLACEMENT:

```kotlin
        Log.d(TAG, "utt=${u.id} lang=${u.lang} rtf=%.3f stt=%.0fms wait=%.0fms feat=%.0fms infer=%.0fms tts=%.0fms synth=%.0fms"
            .format(u.rtf, u.sttLatencyMs, u.waitMs, u.featureMs, u.inferMs, u.ttsLatencyMs, u.ttsSynthMs))
```

### Step 2 — `core/audio/AudioCaptureModule.kt`: carry the cut time

**2a.** ANCHOR:

```kotlin
    private val onSpeechReady: suspend (FloatArray, String) -> Unit
```

REPLACEMENT:

```kotlin
    /** (audio, language, cutNs): cutNs is System.nanoTime() when the phrase was cut (T71). */
    private val onSpeechReady: suspend (FloatArray, String, Long) -> Unit
```

**2b.** ANCHOR:

```kotlin
    private class Segment(
        val audio: FloatArray,
        val language: String,
        val done: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    )
```

REPLACEMENT:

```kotlin
    private class Segment(
        val audio: FloatArray,
        val language: String,
        val done: kotlinx.coroutines.CompletableDeferred<Unit>? = null,
        /** When this phrase was cut (VAD pause or PTT release), not when it left the queue (T71). */
        val cutNs: Long = System.nanoTime()
    )
```

The default value is correct for both producers: the capture loop builds the `Segment` right at the cut, and `submitAndAwait` builds it right at PTT release.

**2c.** ANCHOR:

```kotlin
                    onSpeechReady(segment.audio, segment.language)
```

REPLACEMENT:

```kotlin
                    onSpeechReady(segment.audio, segment.language, segment.cutNs)
```

### Step 3 — `core/service/ITantraForegroundService.kt`

ANCHOR:

```kotlin
            onSpeechReady = { audioBuffer, lang ->
                val utt = Telemetry.begin(lang)
                utt.captureEndNs = System.nanoTime()
                utt.audioDurationMs = audioBuffer.size * 1000L / STTModule.SAMPLE_RATE
                sttModule.ensureLoaded(lang)
                sttModule.currentUtterance = utt
```

REPLACEMENT:

```kotlin
            onSpeechReady = { audioBuffer, lang, cutNs ->
                val utt = Telemetry.begin(lang)
                // Speech ended when the phrase was cut, not when it left the queue (T71).
                utt.captureEndNs = cutNs
                utt.audioDurationMs = audioBuffer.size * 1000L / STTModule.SAMPLE_RATE
                sttModule.ensureLoaded(lang)
                utt.sttStartNs = System.nanoTime()
                sttModule.currentUtterance = utt
```

### VERIFY

1. `./gradlew :app:compileDebugKotlin` and `./gradlew :app:testDebugUnitTest`
2. **Delete the old CSV first** — its header has fewer columns: `adb shell run-as com.itantra.debug rm files/telemetry.csv`
3. Two-phone run as in `docs/latency-evidence/`. The sender CSV has the new header; for a phrase queued behind another, `wait_ms` is large and `feature_ms` small; `rtf` stays around 0.2–0.3 even on the first phrase. The receiver's `tts_synth_ms` is less than or equal to `tts_ms`.

### DO NOT

- Do not change `sttLatencyMs` itself — it is correctly `captureEndNs → inferDoneNs`; only the stamps feeding it move.
- Do not add parameters to `AudioCallbacks` (frozen). `onSpeechReady` is a constructor lambda, not a contract, so changing it is allowed.

---

## T72 🎨 · Let the user choose the walkie-talkie's language

**Files:** `ui/MainViewModel.kt`, `ui/screen/TransceiverScreen.kt`
**Criterion:** REQ — ten languages must be demonstrable on the walkie-talkie itself.
**Depends on:** T45 (uses `warmUp`).

### Why

`ITantraForegroundService.sttLanguage` and `ttsLanguage` start as `"hi"`, and **nothing calls `setSTTLanguage()` or `setTTSLanguage()`** (`grep -rn "setSTTLanguage\|setTTSLanguage" app/src/main/java/com/itantra/ui` returns nothing). The only language picker is on the AI Assistant screen: it calls `MainViewModel.setManualLanguage()`, which updates `_selectedLanguage` for the assistant only. So every PTT message is recognised by the Hindi model and spoken with the Hindi voice, whatever language is spoken. The Transceiver screen's "Auto" pill toggles a flag that the transceiver never reads (`IMPROVEMENT_PLAN.md` §7.3).

Decision recorded here: **one app-wide language.** The same selection drives the assistant and the walkie-talkie.

### Step 1 — `ui/MainViewModel.kt`: push the selection to the service

ANCHOR:

```kotlin
    fun setManualLanguage(bcp47Code: String) {
        _selectedLanguage.value = bcp47Code
        if (!_isAutoDetectEnabled.value) {
            _detectedLanguage.value = bcp47Code
        }
    }
```

REPLACEMENT:

```kotlin
    fun setManualLanguage(bcp47Code: String) {
        _selectedLanguage.value = bcp47Code
        if (!_isAutoDetectEnabled.value) {
            _detectedLanguage.value = bcp47Code
        }
        // The walkie-talkie previously ignored this and always used Hindi (T72).
        foregroundService?.let {
            it.setSTTLanguage(bcp47Code)
            it.setTTSLanguage(bcp47Code)
            it.warmUp(bcp47Code, bcp47Code)
        }
    }
```

### Step 2 — `ui/MainViewModel.kt`: sync when the service binds

ANCHOR (added by T45 Step 3; 16 spaces of indentation):

```kotlin
                it.warmUp()
```

REPLACEMENT:

```kotlin
                // Push the current selection before warming, so the right model is loaded (T72).
                it.setSTTLanguage(_selectedLanguage.value)
                it.setTTSLanguage(_selectedLanguage.value)
                it.warmUp()
```

### Step 3 — UI 🎨 (`ui/screen/TransceiverScreen.kt`)

- Collect `viewModel.selectedLanguage`.
- Add a row of language chips **copied from the AI Assistant picker** (`AIAssistantScreen.kt`: the `LazyRow` over `STT_LANGUAGES`, same `Box` styling, `.clickable { viewModel.setManualLanguage(code) }`). `STT_LANGUAGES` is `private` in that file: move it to a shared place (for example a top-level `val` in a new `ui/component/Languages.kt`) and import it in both screens. Do not duplicate the list. When T64 adds Odia STT, add `"or" to "ଓଡ଼ିଆ"` there once.
- Replace the existing "Auto" pill's behaviour: its `clickable` should no longer call `setAutoDetect`, and its label should show the selected language (the `nativeName` of `IndicLanguage.fromCode(selectedLanguage)`). The transceiver has no working auto-detect; do not show one.
- Disable the chips while PTT is held (switching model mid-hold would transcribe half a phrase with the wrong model).

### VERIFY

1. `./gradlew :app:compileDebugKotlin`
2. Pick Tamil on the Transceiver screen. Logcat shows `STT('ta') loaded` and `Warm-up stt=ta:true …`. Hold PTT and speak Tamil: `STT inference: '<Tamil text>' … [ta]`.
3. Kill and restart the app: the transceiver uses the selection again. (It resets to Hindi if the ViewModel's default is used; persisting the choice is optional polish, not part of this task.)
4. Languages without a TTS voice yet (Marathi, Kannada, Tamil, Telugu until T17b) will transcribe and send, but the receiver reports a real "TTS not available" error instead of speaking. That is expected and honest.

### DO NOT

- Do not implement audio language detection here; that is a separate, larger task.
- Do not let two different languages be selected for the assistant and the transceiver in this task.

---

## Group H — after the second device run (2026-09-24)

Written after reviewing PR #17 (T70, T45, T72, T71) and its run-2 evidence in `docs/latency-evidence/README.md` → "Run 2". **Every ANCHOR below was checked to match exactly once in `feature/latency-pipeline-2` @ `71b2c17`** (the PR #17 branch). Part 1 §0 rules apply. File paths are relative to `app/src/main/java/com/itantra/`.

Order: **T43 → T73 → T46 → T74**. T43 is one hour and fixes a bug visible in every multi-language demo.

---

## T43 (re-anchored) · The receiver speaks in the text's own language

**File:** `core/service/ITantraForegroundService.kt`
**Criterion:** ACC, REQ
**Depends on:** nothing.

> Replaces the ANCHOR in `IMPLEMENTATION_SPEC.md` T43, which predates the telemetry code. The decision is unchanged: use `message.srcLang`, never `dstLang` and never the receiver's own `ttsLanguage`.

### Why (measured)

Run 2's receiver spoke every Kannada and Tamil message with the Hindi voice: `receiver_logcat.txt` shows `Received …: 'என்ன பாடா'` followed by `sherpa-onnx TTS synthesized … [hi]`. `onTextReceived` passes the receiver's own `ttsLanguage` to TTS. There is no translation step, so the text is always in the sender's spoken language, `srcLang`.

### ANCHOR (inside `onTextReceived`)

```kotlin
                val utt = Telemetry.begin(ttsLanguage)
                utt.rxNs = rxStampNs
                val synth = ttsModule.synthesize(message.text, ttsLanguage)
```

### REPLACEMENT

```kotlin
                // Voice the text in the language it is WRITTEN in (T43). There is no translation,
                // so using this phone's own ttsLanguage fed e.g. Tamil text to the Hindi voice
                // (docs/latency-evidence/run2/receiver_logcat.txt).
                val targetLang = message.srcLang.ifBlank { ttsLanguage }
                val utt = Telemetry.begin(targetLang)
                utt.rxNs = rxStampNs
                val synth = ttsModule.synthesize(message.text, targetLang)
```

### VERIFY

1. `./gradlew :app:compileDebugKotlin`
2. Phone A picks Tamil, phone B stays on Hindi. Speak Tamil on A. B's logcat shows the Tamil text received, then a real `TTS not available for 'ta'` error — **not** `synthesized … [hi]`. The text still appears in B's message list.
3. Phone A picks Hindi again: B speaks it with the Hindi voice as before.

### DO NOT

- Do not fall back to another language's voice when `srcLang` has no voice. Silence plus a real error is the honest behaviour until T17b adds the voice.

---

## T73 · Re-initialise the VAD once its model finishes downloading

**Files:** `core/audio/VADModule.kt`, `core/service/ITantraForegroundService.kt`, `ui/MainViewModel.kt`
**Criterion:** ACC (segmentation quality), EFF
**Depends on:** nothing.

### Why (measured)

`VADModule.initialize()` runs once, when the service starts. On a first install the service starts **before** the VAD model has downloaded, so it finds no file and falls back to the energy detector for the rest of the process. Run 2 hit this: `run2/receiver_logcat_prelim_connectivity_check.txt` shows `backend: BASIC_ENERGY (physical path: null …)`, and the model file's timestamp was ~90 s later. Only a force-stop fixed it. A judge installing the app fresh would get the worse detector.

### Step 1 — `core/audio/VADModule.kt`

ANCHOR:

```kotlin
    fun resetState() {
```

REPLACEMENT:

```kotlin
    /**
     * Run [initialize] again if the neural backend is not active (T73). On a first install the
     * service starts before the VAD model has downloaded, so the first initialize() found no file
     * and fell back to BASIC_ENERGY for the whole process
     * (docs/latency-evidence/run2/receiver_logcat_prelim_connectivity_check.txt).
     * @return true if the neural backend is active afterwards.
     */
    suspend fun reinitializeIfNeeded(): Boolean {
        if (activeBackend == VadBackend.NEURAL) return true
        session?.close()
        session = null
        initialize()
        return activeBackend == VadBackend.NEURAL
    }

    fun resetState() {
```

### Step 2 — `core/service/ITantraForegroundService.kt`

ANCHOR:

```kotlin
    // ==================== INTERNALS ====================
```

REPLACEMENT:

```kotlin
    /** Called when the VAD model pack finishes downloading (T73). No-op if already neural. */
    fun reinitVadIfNeeded() {
        serviceScope.launch {
            val neural = vadModule.reinitializeIfNeeded()
            Log.d(TAG, "VAD re-init after download — neural: $neural")
        }
    }

    // ==================== INTERNALS ====================
```

(T74 inserts other code before the same line. Both keep the line, so they can be done in either order.)

### Step 3 — `ui/MainViewModel.kt`: listen for the download, once the service is bound

The listener must go inside `onServiceConnected`. **Do not put it in `init {}`**: `init` runs before `downloadStates` is declared further down the class, so it would read an uninitialised property and crash.

ANCHOR (the end of `onServiceConnected`; this text is unique because of the `override fun onServiceDisconnected` line):

```kotlin
                it.warmUp()
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
```

REPLACEMENT:

```kotlin
                it.warmUp()
                // Re-initialise the VAD when its model finishes downloading (T73). The first
                // emission may already be Downloaded; reinitVadIfNeeded() is then a no-op.
                val svc = it
                viewModelScope.launch {
                    var vadReady = false
                    downloadStates.collect { states ->
                        val nowReady = states[ModelPack.VAD_MODEL] is DownloadState.Downloaded
                        if (nowReady && !vadReady) svc.reinitVadIfNeeded()
                        vadReady = nowReady
                    }
                }
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
```

`ModelPack` and `DownloadState` are already imported in this file.

### VERIFY

1. `./gradlew :app:compileDebugKotlin`
2. Clear the app's data (`adb shell pm clear com.itantra.debug`), launch, and download the core pack. Logcat `VADModule:*` first shows `backend: BASIC_ENERGY`, then — when the VAD pack finishes — `VAD initialized — backend: NEURAL` and `VAD re-init after download — neural: true`, **without** restarting the app.
3. Normal launch with models already present: one `neural: true` line, no errors.

### DO NOT

- Do not poll the file system on a timer. React to the download state.

---

## T46 (made explicit) · Bound the model caches

**Files:** `core/audio/STTModule.kt`, `core/audio/TTSModule.kt`
**Criterion:** EFF (RAM)
**Depends on:** T65 and T70 (their locks make eviction safe: a model cannot be evicted while another call is using it).

> Replaces T46 in the Group C section above, whose TTS half said only "apply the same pattern". Priority raised on 2026-09-24: since T72, every language the user taps loads another ~197 MB STT model, and nothing ever unloads one.

### Step 1 — `core/audio/STTModule.kt`

ANCHOR:

```kotlin
    private val sessionCache = mutableMapOf<String, OrtSession>()
    private val vocabCache = mutableMapOf<String, Array<String>>()
    private val ioNamesCache = mutableMapOf<String, IoNames>()
```

REPLACEMENT:

```kotlin
    // Bounded LRU (T46). Each session is a ~197MB native allocation; caching every language a
    // user ever tapped held them all for the process lifetime. Eviction only happens inside
    // ensureLoaded(), which holds inferenceLock, so a session is never closed while in use.
    private val sessionCache = object : LinkedHashMap<String, OrtSession>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, OrtSession>): Boolean {
            if (size > MAX_CACHED_LANGUAGES) {
                runCatching { eldest.value.close() }
                vocabCache.remove(eldest.key)
                ioNamesCache.remove(eldest.key)
                Log.d(TAG, "Evicted STT session '${eldest.key}' (LRU)")
                return true
            }
            return false
        }
    }
    private val vocabCache = mutableMapOf<String, Array<String>>()
    private val ioNamesCache = mutableMapOf<String, IoNames>()
```

Then in the companion object. ANCHOR:

```kotlin
        private const val TAG = "STTModule"
```

REPLACEMENT:

```kotlin
        private const val TAG = "STTModule"
        /** STT sessions kept resident (T46). 2 covers switching back and forth between two languages. */
        private const val MAX_CACHED_LANGUAGES = 2
```

### Step 2 — `core/audio/TTSModule.kt`

ANCHOR:

```kotlin
    private val ttsCache = mutableMapOf<String, OfflineTts>()
```

REPLACEMENT:

```kotlin
    // Bounded LRU (T46). Eviction happens inside getOrLoadTts(), which only runs under ttsLock
    // (T70), so a voice is never released while synthesizing.
    private val ttsCache = object : LinkedHashMap<String, OfflineTts>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, OfflineTts>): Boolean {
            if (size > MAX_CACHED_VOICES) {
                runCatching { eldest.value.release() }
                Log.d(TAG, "Evicted TTS voice '${eldest.key}' (LRU)")
                return true
            }
            return false
        }
    }
```

Then in the companion object. ANCHOR:

```kotlin
        private const val TAG = "TTSModule"
```

REPLACEMENT:

```kotlin
        private const val TAG = "TTSModule"
        /** TTS voices kept resident (T46). 2 covers speaking two senders' languages in turn. */
        private const val MAX_CACHED_VOICES = 2
```

### VERIFY

1. `./gradlew :app:compileDebugKotlin`
2. On the Transceiver screen tap Hindi → Tamil → Kannada → Hindi. Logcat `STTModule:*` shows `Evicted STT session 'hi' (LRU)` when Kannada loads, and Hindi loads again on the last tap.
3. `adb shell dumpsys meminfo com.itantra.debug | findstr TOTAL` after the four taps stays near the value after two taps, instead of growing by ~200 MB per language.

### DO NOT

- Do not set either limit to 1: a phone that sends in one language and receives in another would reload a model on every message.

---

## T74 · The AI Assistant uses the service's models and playback queue

**Files:** `core/service/ITantraForegroundService.kt`, `ui/MainViewModel.kt`
**Criterion:** EFF (RAM), REQ (no overlapping audio)
**Depends on:** nothing, but do it after T46 so both memory fixes are measured together.

### Why

`MainViewModel` creates its **own** `STTModule`, `TTSModule` and `AudioPlaybackManager` for the AI Assistant, separate from the service's. Since T72 both features use the same selected language, so using the Assistant's voice input or speech loads a second copy of the same ~197 MB STT model and voice. The two instances also have separate locks and separate playback queues, so an Assistant reply and a received walkie-talkie message can play on top of each other.

### Step 1 — `core/service/ITantraForegroundService.kt`: expose the shared instances

ANCHOR:

```kotlin
    // ==================== INTERNALS ====================
```

REPLACEMENT:

```kotlin
    /** Shared with the AI Assistant so one model instance, one lock and one playback queue serve
     *  both features (T74). Valid only after onCreate(). */
    val sharedStt: STTModule get() = sttModule
    val sharedTts: TTSModule get() = ttsModule
    val sharedPlayback: AudioPlaybackManager get() = audioPlayback

    // ==================== INTERNALS ====================
```

### Step 2 — `ui/MainViewModel.kt`: prefer the service's instances

**2a.** ANCHOR:

```kotlin
    private val audioPlayback = AudioPlaybackManager(application, audioCallbacks)
```

REPLACEMENT:

```kotlin
    private val audioPlayback = AudioPlaybackManager(application, audioCallbacks)

    // Use the service's instances whenever it is bound (T74), so the Assistant and the
    // walkie-talkie share one model copy, one lock and one playback queue. The ViewModel's own
    // instances above are only a fallback before binding; they hold no model until used.
    private val activeStt: STTModule get() = foregroundService?.sharedStt ?: sttModule
    private val activeTts: TTSModule get() = foregroundService?.sharedTts ?: ttsModule
    private val activePlayback: AudioPlaybackManager get() = foregroundService?.sharedPlayback ?: audioPlayback
```

**2b.** ANCHOR (in the Assistant's `AudioCaptureModule` lambda):

```kotlin
            sttModule.ensureLoaded(lang)
            val result = sttModule.transcribe(pcm, lang)
```

REPLACEMENT:

```kotlin
            activeStt.ensureLoaded(lang)
            val result = activeStt.transcribe(pcm, lang)
```

**2c.** ANCHOR:

```kotlin
            val synth = ttsModule.synthesize(text, lang)
```

REPLACEMENT:

```kotlin
            val synth = activeTts.synthesize(text, lang)
```

**2d.** ANCHOR:

```kotlin
                audioPlayback.play(synth.samples, synth.sampleRate)
```

REPLACEMENT:

```kotlin
                activePlayback.play(synth.samples, synth.sampleRate)
```

**2e.** ANCHOR:

```kotlin
                    sttModule.ensureLoaded(_selectedLanguage.value)
                    val result = sttModule.transcribe(pcm, _selectedLanguage.value)
```

REPLACEMENT:

```kotlin
                    activeStt.ensureLoaded(_selectedLanguage.value)
                    val result = activeStt.transcribe(pcm, _selectedLanguage.value)
```

Leave `sttModule = sttModule` in the `AudioCaptureModule(...)` constructor call and `ttsModule.release()` in `onCleared()` unchanged: they refer to the ViewModel's fallback instances, which is correct.

### VERIFY

1. `./gradlew :app:compileDebugKotlin`
2. `grep -n "sttModule.transcribe\|ttsModule.synthesize\|audioPlayback.play" app/src/main/java/com/itantra/ui/MainViewModel.kt` returns nothing.
3. On a phone: use the walkie-talkie in Hindi, then the Assistant's voice input in Hindi. Logcat `STTModule:*` shows **one** `STT('hi') loaded` for the whole session, not two.

### DO NOT

- Do not delete the ViewModel's own instances; they are the fallback before the service binds.
- Do not share the ViewModel's `VADModule` / `AudioCaptureModule`: the Assistant captures on demand with its own capture loop, and the VAD model is only ~2 MB.

---

# Group F — Dossier (T56–T61)

Not code. Commands and procedure.

## T56 · Scorecard run

```bash
# per language, 3 repeats, on the target phone
adb shell am start -n com.itantra/.MainActivity
# ... run the utterance set ...
adb shell run-as com.itantra cat files/telemetry.csv > scorecard_<lang>_<run>.csv

# size
ls -la app/build/outputs/apk/release/

# memory, while active
adb shell dumpsys meminfo com.itantra | grep TOTAL

# idle CPU, phone mode, 10 minutes
adb shell top -d 60 -n 10 | grep itantra
```

Report **medians**, not best runs. Include a 10-minute sustained session to expose thermal throttling; a figure that degrades under load is still worth reporting honestly.

## T57–T61

Before/after table from the week-0 and week-6 CSVs; rehearsed two-device demo; deck built around the scorecard; docs reconciled; backup recording. See `TASKS.md`.

---

## Coverage

| Group | Tasks | Status |
| --- | --- | --- |
| Part 1 | T02, T05–T07, T13–T15, T17a, T17b, T24, T31, T37–T39, T43, T44, T47 | Specced |
| A — instrumentation | T08–T12 | Specced |
| B — capture/VAD | T32–T35, T41, T51–T53 | Specced |
| C — latency/compliance | T40, T42, T45, T46 | Specced |
| D — bundle/UI | T20, T21 🎨, T22 | Specced |
| E — deferred | T23, T29, T30, T48, T49, T50 | Specced as procedures 🔬 |
| F — dossier | T56–T61 | Commands given |
| G — second audit | T62 🔬, T63, T64 🔬, T65, T66 🎨, T67 🎨, T68, T69 | Specced, anchors verified against committed code and working tree on 2026-09-23. T62, T65 done |
| G — after first device run | T70, T71, T72 🎨; T45 revised | Anchors verified against `feature/latency-pipeline` @ `e57fb7d` on 2026-09-24. All four done in PR #17 |
| H — after second device run | T43 (re-anchored), T73, T46 (explicit), T74 | Anchors verified against `feature/latency-pipeline-2` @ `71b2c17` (PR #17) on 2026-09-24 |
| Judgement only | T01, T03, T04, T16, T25–T28, T36, T54 | Trivial, or covered inline in Part 1 |
| Superseded | T19 → T64; T55 → merged into T64 Step 6 | — |

---

_iTantra · Smart India Hackathon 2026 · Problem Statement #26173_
