# iTantra — Implementation Spec, Part 2

> Continues [`IMPLEMENTATION_SPEC.md`](IMPLEMENTATION_SPEC.md). **Read §0 of Part 1 first — the rules contract applies here unchanged.**
> Covers the tasks Part 1 left unspecified. As of 2026-09-22 · PS-26173

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

Do not attach `AcousticEchoCanceler`. This is a push-to-talk radio, not a speakerphone; AEC can attenuate the near-end speaker.

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

## T45 · Warm the models at service start

**File:** `core/service/ITantraForegroundService.kt`
**Criterion:** LAT

### ANCHOR

```kotlin
        serviceScope.launch {
            val vadOk = vadModule.initialize()
            if (vadOk && connectionMode == ConnectionMode.PHONE_MODE) {
                audioCaptureModule.startCapture()
            }
        }
```

### REPLACEMENT

```kotlin
        serviceScope.launch {
            val vadOk = vadModule.initialize()
            if (vadOk && connectionMode == ConnectionMode.PHONE_MODE) {
                audioCaptureModule.startCapture()
            }
            // Load the configured pair off the critical path. Otherwise the first message of
            // every session pays a ~197MB ONNX load plus a VITS voice load before it can start.
            launch {
                runCatching { sttModule.ensureLoaded(sttLanguage) }
                    .onFailure { Log.w(TAG, "STT warmup failed: ${it.message}") }
            }
            launch {
                runCatching { ttsModule.synthesize(" ", ttsLanguage) }
                    .onFailure { Log.w(TAG, "TTS warmup failed: ${it.message}") }
            }
        }
```

### DO NOT

Do not warm all ten languages. That is the RAM criterion pointing the other way. Warm only the configured pair.

---

## T46 · Bound the model caches

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
| Judgement only | T01, T03, T04, T16, T19, T25–T28, T36, T54, T55 | Trivial, or covered inline in Part 1 |

---

_iTantra · Smart India Hackathon 2026 · Problem Statement #26173_
