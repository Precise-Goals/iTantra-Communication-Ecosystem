# iTantra — Implementation Spec

> Execution detail for [`TASKS.md`](TASKS.md). Written to be followed literally by a coding agent.
> As of 2026-09-21 · PS-26173

---

## 0. Rules for the implementing agent

**Read this section before touching any file. It is not advice; it is a contract.**

1. **Never invent an API, a URL, a file path, a model name, or a constant.** If this document does not give it to you, you do not have it. Stop and report instead of guessing.
2. **Match on text, never on line numbers.** Line numbers in this document are hints only and will drift. Every task gives you an **ANCHOR** — the exact current text. Find that text. If it does not match character-for-character, **STOP and report the mismatch.** Do not "fix it up" or apply the change to something that looks similar.
3. **Change only the files listed in the task.** If a change seems to require editing a file not listed, stop and report.
4. **Run the VERIFY command after every task.** If it fails, revert your change and report. Do not stack a second change on top of a failing one.
5. **Do not reformat, re-indent, or "clean up" surrounding code.** Touch only what the task says.
6. **Do not delete comments.** The comments in this codebase record real on-device debugging findings and are load-bearing knowledge.
7. **One task, one commit.** Commit message starts with the task ID, e.g. `T05: replace naive DFT with radix-2 FFT`.
8. **If a task says the code does not exist yet, create it exactly as given.** Do not improvise a different design.

### Facts you may rely on (verified 2026-09-21)

These were checked against the real GitHub release asset list, not recalled. Treat them as ground truth.

| Fact | Value |
| --- | --- |
| sherpa-onnx `tts-models` release total assets | 645 |
| Indic voices that **exist** in that release | Hindi (`hi_IN` pratham/priyamvada/rohan), Malayalam (`ml_IN` arjun/meera), Gujarati (`gu_IN` mimic3), Bengali (`vits-coqui-bn-custom_female`), English (many) |
| MMS voices in that release | **Only `vits-mms-eng.tar.bz2`** — no `mar`, `kan`, `tam`, `tel`, or `ory` |
| Marathi / Kannada / Tamil / Telugu / Odia TTS | **No prebuilt asset exists.** Must be converted from `facebook/mms-tts-*` yourself (T17b) |
| Odia STT | No Odia model in `parismitaglobalsolutions/indicconformer-sherpa-onnx` (it has `as/` Assamese only). **But AI4Bharat publishes one:** `ai4bharat/indicconformer_stt_or_hybrid_ctc_rnnt_large` — export it yourself (T64, `IMPLEMENTATION_SPEC_2.md` Group G). *Corrected 2026-09-23.* |
| Silero VAD | The downloaded file is the **v5+** model (inputs `input`, `state [2,1,128]`, `sr`), despite the local name `silero_vad_v4.onnx`. It needs a 64-sample context prefix per window (T62). *Added 2026-09-23.* |
| Translation | **Not required by the PS** and not present in the code. The receiver's voice must match the language the text is written in (`srcLang`) — see the T43 correction. *Added 2026-09-23.* |
| Piper voices have int8 variants | **Yes** — `-int8.tar.bz2` suffix, ~21 MB vs ~67 MB |

**Correction to earlier planning documents:** `ACTION_PLAN.md` §3 originally claimed the five missing TTS voices could be added by pasting URLs from the sherpa-onnx release. **That is false** — the assets do not exist. The registry comments in `ModelRegistry.kt` lines 19–25 were correct all along. T17 below is split into the real two-part task.

---

## T05 + T06 + T07 · Replace the naive DFT and precompute the filterbank

**File:** `app/src/main/java/com/itantra/core/audio/STTModule.kt`
**Criterion:** LAT / EFF · **Measured impact:** 952 ms → 22 ms per 3 s utterance

### ANCHOR 1 — the function to delete

Find this **entire function** and delete it:

```kotlin
    /** Compute power spectrum via naive DFT (production should use FFTW via JNI). */
    private fun computePowerSpectrum(frame: FloatArray): FloatArray {
        val N = frame.size
        val halfN = N / 2 + 1
        val spectrum = FloatArray(halfN)
        for (k in 0 until halfN) {
            var re = 0.0
            var im = 0.0
            for (n in frame.indices) {
                val angle = 2.0 * PI * k * n / N
                re += frame[n] * cos(angle)
                im -= frame[n] * sin(angle)
            }
            spectrum[k] = (re * re + im * im).toFloat()
        }
        return spectrum
    }
```

### ANCHOR 2 — the function to delete

Find this **entire function** and delete it:

```kotlin
    /** Apply mel filterbank to linear frequency spectrum. */
    private fun applyMelFilterbank(spectrum: FloatArray, nMels: Int, sampleRate: Int): FloatArray {
```

…through to its closing brace (it ends with `return result` then `}`). Delete the whole thing.

### REPLACEMENT — add these members to the `STTModule` class

Add a new constant beside the existing ones in the `companion object`. The existing block reads:

```kotlin
        private const val N_MELS = 80
        private const val FRAME_LENGTH = 400   // 25ms window at 16kHz
        private const val HOP_LENGTH = 160     // 10ms hop at 16kHz
```

Add one line directly after `HOP_LENGTH`:

```kotlin
        /** FFT size: next power of two at or above FRAME_LENGTH. NeMo pads the 400-sample
         *  window to 512 rather than transforming 400 points directly. */
        private const val N_FFT = 512
```

Then add these as **private members of the class** (not the companion object), placed immediately above `extractLogMelSpectrogram`:

```kotlin
    // ── Precomputed FFT and mel filterbank ────────────────────────────────
    // Built once on first use. The previous implementation recomputed a naive O(N^2) DFT and
    // rebuilt the entire mel filterbank on every single frame, which cost ~950ms per 3s of
    // audio and put RTF above 1.0 before the ONNX session even ran.

    /** Bit-reversal permutation table for the radix-2 FFT. */
    private val fftReverse: IntArray by lazy {
        val rev = IntArray(N_FFT)
        var j = 0
        for (i in 1 until N_FFT) {
            var bit = N_FFT shr 1
            while (j >= bit) { j -= bit; bit = bit shr 1 }
            j += bit
            rev[i] = j
        }
        rev
    }

    /** Periodic Hann window (torch.hann_window default), length FRAME_LENGTH. */
    private val hannWindow: FloatArray by lazy {
        FloatArray(FRAME_LENGTH) { i ->
            (0.5 * (1.0 - kotlin.math.cos(2.0 * PI * i / FRAME_LENGTH))).toFloat()
        }
    }

    /** Sparse mel filterbank: for each mel band, the first FFT bin and its triangular weights. */
    private class MelBank(val startBin: IntArray, val weights: Array<FloatArray>)

    private val melBank: MelBank by lazy { buildMelBank() }

    /** Scratch buffers, reused across frames to keep allocation constant per utterance. */
    private val fftRe = DoubleArray(N_FFT)
    private val fftIm = DoubleArray(N_FFT)
    private val powerSpectrum = FloatArray(N_FFT / 2 + 1)

    /**
     * Build the mel filterbank once, with Slaney area normalization — librosa's `norm="slaney"`,
     * which is what NeMo's AudioToMelSpectrogramPreprocessor uses. Without it the wide
     * high-frequency bands carry systematically more energy than the encoder saw in training.
     */
    private fun buildMelBank(): MelBank {
        val nBins = N_FFT / 2 + 1
        val melMin = hzToMel(0.0)
        val melMax = hzToMel(SAMPLE_RATE / 2.0)
        val melPoints = DoubleArray(N_MELS + 2) { i ->
            melToHz(melMin + i * (melMax - melMin) / (N_MELS + 1))
        }
        val binFreq = DoubleArray(nBins) { k -> k.toDouble() * SAMPLE_RATE / N_FFT }

        val starts = IntArray(N_MELS)
        val weightRows = Array(N_MELS) { FloatArray(0) }

        for (m in 0 until N_MELS) {
            val lower = melPoints[m]
            val center = melPoints[m + 1]
            val upper = melPoints[m + 2]
            // Slaney normalization: scale each triangle by 2/(upper-lower) so filters have
            // equal area rather than equal peak height.
            val enorm = 2.0 / (upper - lower)

            var first = -1
            var last = -1
            for (k in 0 until nBins) {
                val f = binFreq[k]
                if (f > lower && f < upper) {
                    if (first < 0) first = k
                    last = k
                }
            }
            if (first < 0) { starts[m] = 0; weightRows[m] = FloatArray(0); continue }

            val w = FloatArray(last - first + 1)
            for (k in first..last) {
                val f = binFreq[k]
                val v = if (f <= center) (f - lower) / (center - lower)
                        else (upper - f) / (upper - center)
                w[k - first] = (v * enorm).toFloat()
            }
            starts[m] = first
            weightRows[m] = w
        }
        return MelBank(starts, weightRows)
    }

    /**
     * In-place radix-2 Cooley-Tukey FFT over [fftRe]/[fftIm], then power spectrum into
     * [powerSpectrum]. Caller must have filled fftRe with the windowed, zero-padded frame
     * and zeroed fftIm.
     */
    private fun fftPowerInPlace() {
        for (i in 1 until N_FFT) {
            val j = fftReverse[i]
            if (i < j) {
                var t = fftRe[i]; fftRe[i] = fftRe[j]; fftRe[j] = t
                t = fftIm[i]; fftIm[i] = fftIm[j]; fftIm[j] = t
            }
        }
        var len = 2
        while (len <= N_FFT) {
            val ang = -2.0 * PI / len
            val wr = kotlin.math.cos(ang)
            val wi = kotlin.math.sin(ang)
            var i = 0
            while (i < N_FFT) {
                var cr = 1.0
                var ci = 0.0
                for (k in 0 until len / 2) {
                    val u = i + k
                    val v = i + k + len / 2
                    val tr = fftRe[v] * cr - fftIm[v] * ci
                    val ti = fftRe[v] * ci + fftIm[v] * cr
                    fftRe[v] = fftRe[u] - tr; fftIm[v] = fftIm[u] - ti
                    fftRe[u] += tr;           fftIm[u] += ti
                    val nr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = nr
                }
                i += len
            }
            len = len shl 1
        }
        for (k in powerSpectrum.indices) {
            powerSpectrum[k] = (fftRe[k] * fftRe[k] + fftIm[k] * fftIm[k]).toFloat()
        }
    }
```

### ANCHOR 3 — rewrite the frame loop

Find this block inside `extractLogMelSpectrogram`:

```kotlin
        while (start + FRAME_LENGTH <= audio.size) {
            val frame = audio.copyOfRange(start, start + FRAME_LENGTH)

            // Apply Hann window
            for (i in frame.indices) {
                frame[i] *= (0.5f * (1f - cos(2.0 * PI * i / (FRAME_LENGTH - 1)))).toFloat()
            }

            // FFT magnitude spectrum (simplified — real impl uses FFTW or KissFFT via JNI)
            val spectrum = computePowerSpectrum(frame)

            // Apply mel filterbank (80 filters, 0Hz–8000Hz)
            val melFeatures = applyMelFilterbank(spectrum, N_MELS, SAMPLE_RATE)

            // Log compression
            for (i in melFeatures.indices) {
                melFeatures[i] = (ln(melFeatures[i].toDouble() + 1e-10)).toFloat()
            }

            frames.add(melFeatures)
            start += HOP_LENGTH
        }
```

Replace it with exactly:

```kotlin
        val bank = melBank
        // NeMo's log_zero_guard_value default is 2**-24, not the 1e-10 used previously.
        val logGuard = 5.9604645e-8

        while (start + FRAME_LENGTH <= audio.size) {
            // Window straight into the FFT scratch buffer; zero-pad FRAME_LENGTH..N_FFT.
            // Preemphasis (x[i] - 0.97*x[i-1]) is applied to `audio` before this loop.
            for (i in 0 until FRAME_LENGTH) {
                fftRe[i] = (audio[start + i] * hannWindow[i]).toDouble()
                fftIm[i] = 0.0
            }
            for (i in FRAME_LENGTH until N_FFT) { fftRe[i] = 0.0; fftIm[i] = 0.0 }

            fftPowerInPlace()

            val melFeatures = FloatArray(N_MELS)
            for (m in 0 until N_MELS) {
                val w = bank.weights[m]
                val s = bank.startBin[m]
                var energy = 0f
                for (k in w.indices) energy += powerSpectrum[s + k] * w[k]
                melFeatures[m] = ln(energy.toDouble() + logGuard).toFloat()
            }

            frames.add(melFeatures)
            start += HOP_LENGTH
        }
```

### VERIFY

```bash
./gradlew :app:compileDebugKotlin
```

Then on device, speak one utterance and confirm the log line from `STTModule.transcribe` shows a materially smaller `inferenceMs`.

### DO NOT

- Do not remove the per-feature normalization block that follows this loop. It is correct and load-bearing.
- Do not change `N_MELS`, `FRAME_LENGTH`, `HOP_LENGTH`, or `SAMPLE_RATE`.
- Do not delete the transpose block at the end of the function.

---

## T24 · Add preemphasis

**File:** `app/src/main/java/com/itantra/core/audio/STTModule.kt`
**Criterion:** ACC · Depends on: T05

### ANCHOR

Find the opening of `extractLogMelSpectrogram`:

```kotlin
    private fun extractLogMelSpectrogram(audio: FloatArray): FloatArray {
        val frames = mutableListOf<FloatArray>()
        var start = 0
```

### REPLACEMENT

```kotlin
    private fun extractLogMelSpectrogram(input: FloatArray): FloatArray {
        // NeMo AudioToMelSpectrogramPreprocessor applies preemph=0.97 before framing.
        // Omitting it tilts the whole spectrum relative to what the encoder was trained on.
        val audio = FloatArray(input.size)
        if (input.isNotEmpty()) {
            audio[0] = input[0]
            for (i in 1 until input.size) audio[i] = input[i] - 0.97f * input[i - 1]
        }

        val frames = mutableListOf<FloatArray>()
        var start = 0
```

### VERIFY

```bash
./gradlew :app:compileDebugKotlin
```

### DO NOT

Do not apply preemphasis inside the frame loop. It is applied **once to the whole signal**, before framing.

---

## T13 · Delete the TTS resampler, play at native rate

**Files:** `core/audio/TTSModule.kt`, `core/audio/AudioPlaybackManager.kt`, `core/service/ITantraForegroundService.kt`
**Criterion:** ACC · Piper VITS output is 22050 Hz; downsampling to 16 kHz with no anti-alias filter aliases every voice.

### Step 1 — `TTSModule.kt`, return the native rate

ANCHOR:

```kotlin
                val resampled = resampleTo16k(audio.samples, audio.sampleRate)
                callbacks.onTTSSynthesisComplete(durationMs)
                resampled
```

REPLACEMENT:

```kotlin
                callbacks.onTTSSynthesisComplete(durationMs)
                // Return the voice's native sample rate. The previous linear-interpolation
                // downsample to 16kHz had no anti-aliasing filter, so everything above 8kHz
                // folded back into the audible band as metallic artifacts — and AudioTrack
                // plays 22050Hz natively, so the resample bought nothing.
                SynthesisResult(audio.samples, audio.sampleRate)
```

Change the function signature. ANCHOR:

```kotlin
    suspend fun synthesize(text: String, languageCode: String): FloatArray? =
```

REPLACEMENT:

```kotlin
    /** Synthesized PCM plus the sample rate it must be played at. */
    data class SynthesisResult(val samples: FloatArray, val sampleRate: Int)

    suspend fun synthesize(text: String, languageCode: String): SynthesisResult? =
```

Delete the whole `resampleTo16k` function. ANCHOR (delete through its closing brace):

```kotlin
    /** Resample a waveform from [sourceSampleRate] down to [PLAYBACK_SAMPLE_RATE] via linear interpolation. */
    fun resampleTo16k(waveform: FloatArray, sourceSampleRate: Int): FloatArray {
```

### Step 2 — `AudioPlaybackManager.kt`, take the rate as a parameter

ANCHOR:

```kotlin
    fun play(waveform: FloatArray, isAlert: Boolean = false) {
        scope.launch {
            if (isAlert) playAlert(waveform) else playNormal(waveform)
        }
    }
```

REPLACEMENT:

```kotlin
    fun play(waveform: FloatArray, sampleRate: Int, isAlert: Boolean = false) {
        scope.launch {
            if (isAlert) playAlert(waveform, sampleRate) else playNormal(waveform, sampleRate)
        }
    }
```

Then in **both** `playNormal` and `playAlert`: change the signature to accept `sampleRate: Int`, and replace every use of the `SAMPLE_RATE` constant with the `sampleRate` parameter. There are two uses in each function — one in `AudioTrack.getMinBufferSize(...)` and one in `.setSampleRate(...)`.

### Step 3 — `ITantraForegroundService.kt`, pass it through

ANCHOR:

```kotlin
                val waveform = ttsModule.synthesize(message.text, ttsLanguage)
                if (waveform != null) {
                    // synthesize() already returns audio resampled to PLAYBACK_SAMPLE_RATE
                    audioPlayback.play(waveform, isAlert)
                }
```

REPLACEMENT:

```kotlin
                val synth = ttsModule.synthesize(message.text, ttsLanguage)
                if (synth != null) {
                    audioPlayback.play(synth.samples, synth.sampleRate, isAlert)
                }
```

### VERIFY

```bash
./gradlew :app:compileDebugKotlin
grep -rn "resampleTo16k" app/src/main/java
```

The grep must return **nothing**.

### DO NOT

Do not delete the `PLAYBACK_SAMPLE_RATE` constant yet — search for other references first and remove them in the same commit only if they exist.

---

## T14 + T15 + T02 · Shrink the APK

**File:** `app/build.gradle.kts`
**Criterion:** EFF

### Step 1 — arm64 only

ANCHOR:

```kotlin
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
```

REPLACEMENT:

```kotlin
        ndk {
            // Judged build targets real phones only. x86_64 is emulator-only and armeabi-v7a
            // cannot run llamacpp at all (LlmModule.isDeviceSupported already gates it out).
            abiFilters += listOf("arm64-v8a")
        }
```

### Step 2 — drop the LLM from the judged build

ANCHOR:

```kotlin
    implementation(libs.llamacpp.kotlin)
```

REPLACEMENT:

```kotlin
    // Excluded from the judged build: a 2.39GB model and its native libs sit directly against
    // the Efficiency criterion, and the problem statement does not ask for an LLM.
    // Re-enable for the full build by uncommenting.
    // implementation(libs.llamacpp.kotlin)
```

You must then make `LlmModule.kt` compile without the dependency. **Do not delete the file.** Report back if it fails to compile and wait for instruction — do not stub out methods on your own initiative.

### VERIFY

```bash
./gradlew :app:assembleDebug
ls -la app/build/outputs/apk/debug/
```

Record the APK size before and after in the commit message.

---

## T17a · Swap the three Piper voices to int8 (verified win)

**File:** `app/src/main/java/com/itantra/core/download/ModelRegistry.kt`
**Criterion:** EFF · **Verified saving: 138.6 MB across three voices**

The sherpa-onnx release publishes int8 variants of every Piper voice. Confirmed sizes:

| Voice | Current | int8 | Saving |
| --- | --- | --- | --- |
| `vits-piper-hi_IN-pratham-medium` | 67.2 MB | **21.0 MB** | 46.2 MB |
| `vits-piper-ml_IN-arjun-medium` | 67.2 MB | **20.8 MB** | 46.4 MB |
| `vits-piper-en_US-lessac-low` | 67.1 MB | **21.1 MB** | 46.0 MB |

### ANCHOR (Hindi — repeat the same shape for Malayalam and English)

```kotlin
        ModelPack.TTS_HINDI to sherpaTtsInfo(
            ModelPack.TTS_HINDI, "vits-piper-hi_IN-pratham-medium.tar.bz2", "hi",
            sizeBytes = 67_238_438L,
            sha256 = "2084d321e1d2752f2b64ed3012ba27751df01a80da46f52920098cdcb7e35648"
        ),
```

### REPLACEMENT

```kotlin
        ModelPack.TTS_HINDI to sherpaTtsInfo(
            ModelPack.TTS_HINDI, "vits-piper-hi_IN-pratham-medium-int8.tar.bz2", "hi",
            sizeBytes = 21_000_000L,
            sha256 = null // fetch the real digest from the release asset list before shipping
        ),
```

**You must replace both `sizeBytes` and `sha256`.** The old SHA-256 belongs to the non-int8 file and will fail integrity verification if left in place. Get the real values with:

```bash
curl -s "https://api.github.com/repos/k2-fsa/sherpa-onnx/releases/tags/tts-models" \
  | grep -A3 '"vits-piper-hi_IN-pratham-medium-int8.tar.bz2"'
```

Do the same for Malayalam (`vits-piper-ml_IN-arjun-medium-int8.tar.bz2`) and English (`vits-piper-en_US-lessac-low-int8.tar.bz2`).

### DO NOT

- Gujarati (`vits-mimic3-gu_IN-cmu-indic_low`) and Bengali (`vits-coqui-bn-custom_female`) have **no int8 variant**. Leave them alone.
- Do not invent a sha256. If you cannot fetch it, set `sha256 = null` — the pipeline falls back to trust-on-first-download, which is safe.

### VERIFY

On device: delete the Hindi voice, re-download, confirm it extracts and synthesizes.

---

## T17b · Produce the five missing TTS voices

**Criterion:** ACC / REQ · **Effort: ~2 days.** This is a conversion task, not a URL change.

### Read this before starting

**The voices do not exist as prebuilt downloads.** The sherpa-onnx `tts-models` release was queried directly on 2026-09-21: of 645 assets, the only MMS voice is `vits-mms-eng.tar.bz2`. There is no `mar`, `kan`, `tam`, `tel`, or `ory`.

The comments already in `ModelRegistry.kt` lines 19–25 state this correctly. **They are right. Do not "fix" them.**

### Procedure

1. Source checkpoints are `facebook/mms-tts-mar`, `-kan`, `-tam`, `-tel`, `-ory` on Hugging Face.
2. Convert each to sherpa-onnx VITS ONNX using the official script documented at <https://k2-fsa.github.io/sherpa/onnx/tts/mms.html>. Each conversion produces `model.onnx` + `tokens.txt`.
3. Package each as `.tar.bz2` matching the layout of an existing voice — inspect an extracted Hindi bundle first and mirror it exactly.
4. Host the five archives (a Hugging Face repo under your own account is fine) and record each real size and SHA-256.
5. Add five `sherpaTtsInfo`-shaped entries to `ModelRegistry`, but pointing at your host, not `SHERPA_TTS_MODELS_BASE`. Add a new base-URL constant beside the existing ones.
6. Replace the five stub entries. ANCHOR for each:

```kotlin
        ModelPack.TTS_KANNADA to ModelInfo(
            ModelPack.TTS_KANNADA, fileName = "", downloadUrl = "", sha256 = null, sizeBytes = 0L
        ),
```

7. Add the language codes to `TTSModule.LANGUAGE_TO_PACK`. ANCHOR:

```kotlin
        private val LANGUAGE_TO_PACK: Map<String, ModelPack> = mapOf(
            "hi" to ModelPack.TTS_HINDI,
            "gu" to ModelPack.TTS_GUJARATI,
            "ml" to ModelPack.TTS_MALAYALAM,
            "bn" to ModelPack.TTS_BENGALI,
            "en" to ModelPack.TTS_ENGLISH
        )
```

Add `"mr"`, `"kn"`, `"ta"`, `"te"`, `"or"` mapped to their packs.

8. Update the class doc at the top of `ModelRegistry.kt` to say these are now self-converted MMS voices, and record **CC-BY-NC 4.0** as their licence.

### DO NOT

- Do not point any of these at a URL in `k2-fsa/sherpa-onnx`. It will 404.
- Do not substitute a different language's model as a placeholder. The existing code refuses to do this deliberately (see the Odia comment) and that discipline is worth keeping.

---

## T37 · Wire phone mode to the UI

**Files:** `ui/MainViewModel.kt`, `ui/screen/TransceiverScreen.kt`
**Criterion:** REQ — the PS requires *"if turned off it should work like a phone"*

> ⚠️ **Ship with T63 in the same PR (added 2026-09-23).** Phone mode keeps the microphone open while received messages play on the loudspeaker. Without T63's echo gate the phone transcribes its own playback and sends it back to the sender. T63 is in `IMPLEMENTATION_SPEC_2.md` Group G.

`ConnectionMode.PHONE_MODE` and `ITantraForegroundService.setConnectionMode()` already exist and are correct. The only thing missing is that **nothing calls them.** Verified: `grep -rn "setConnectionMode" app/src/main/java/com/itantra/ui` returns nothing.

### Step 1 — `MainViewModel.kt`

Add beside the other PTT functions. ANCHOR:

```kotlin
    fun stopTransceiverPtt() {
```

INSERT **before** that function:

```kotlin
    private val _isPhoneMode = MutableStateFlow(false)
    val isPhoneMode: StateFlow<Boolean> = _isPhoneMode.asStateFlow()

    /**
     * PTT off = phone mode: continuous VAD-gated capture instead of hold-to-talk.
     * Required by the problem statement ("if turned off it should work like a phone").
     */
    fun setPhoneMode(enabled: Boolean) {
        _isPhoneMode.value = enabled
        foregroundService?.setConnectionMode(
            if (enabled) ConnectionMode.PHONE_MODE else ConnectionMode.PUSH_TO_TALK
        )
    }
```

Add the import `com.itantra.domain.model.ConnectionMode` if it is not already present.

### Step 2 — `TransceiverScreen.kt`

Add a `Switch` labelled "Phone mode" near the existing language auto-detect toggle, bound to `isPhoneMode` / `setPhoneMode`. When phone mode is on, the PTT button must be disabled and visually de-emphasised.

Match the styling of the existing auto-detect toggle in that file — do not introduce a new visual pattern.

### VERIFY

```bash
grep -rn "setConnectionMode" app/src/main/java/com/itantra/ui
```

Must now return a match. On device: toggle phone mode, speak without touching PTT, confirm the message transmits.

---

## T38 + T39 · Playback queue with alert pre-emption

**File:** `core/audio/AudioPlaybackManager.kt`
**Criterion:** REQ — concurrent messages currently overlap and garble each other.

### ANCHOR

```kotlin
    fun play(waveform: FloatArray, sampleRate: Int, isAlert: Boolean = false) {
        scope.launch {
            if (isAlert) playAlert(waveform, sampleRate) else playNormal(waveform, sampleRate)
        }
    }
```

(If T13 has not been done yet, the anchor is the three-argument-less version; do T13 first.)

### REPLACEMENT

```kotlin
    private data class PlaybackItem(
        val waveform: FloatArray,
        val sampleRate: Int,
        val isAlert: Boolean
    )

    /** Serialises playback. Without this, two messages arriving close together each built their
     *  own AudioTrack and played simultaneously, which on a walkie-talkie is unusable. */
    private val playbackQueue = kotlinx.coroutines.channels.Channel<PlaybackItem>(
        capacity = 16,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )

    init {
        scope.launch {
            for (item in playbackQueue) {
                if (item.isAlert) playAlert(item.waveform, item.sampleRate)
                else playNormal(item.waveform, item.sampleRate)
            }
        }
    }

    fun play(waveform: FloatArray, sampleRate: Int, isAlert: Boolean = false) {
        playbackQueue.trySend(PlaybackItem(waveform, sampleRate, isAlert))
    }
```

### Also harden the alert focus request

ANCHOR in `requestAudioFocus`:

```kotlin
            val focusRequest = AudioFocusRequest.Builder(focusGain)
                .setAudioAttributes(
```

Add `.setWillPauseWhenDucked(false)` to that builder chain, immediately before `.build()`.

### VERIFY

Send two messages from the peer device within one second of each other. They must play one after the other, not on top of each other.

### DO NOT

Do not add a second `scope.launch` inside `play()`. The whole point is that exactly one coroutine consumes the queue.

---

## T43 · Honour `dstLang`

> **Use the re-anchored version in `IMPLEMENTATION_SPEC_2.md` Group H → T43 (2026-09-24).** The ANCHOR below predates the telemetry code and no longer matches. The decision in the correction below (use `srcLang`) is unchanged.

**File:** `core/service/ITantraForegroundService.kt`
**Criterion:** ACC / REQ

### ANCHOR

```kotlin
                val waveform = ttsModule.synthesize(message.text, ttsLanguage)
```

(or, after T13, the `val synth = ...` form)

### REPLACEMENT

> **Corrected 2026-09-23.** The first version of this task used `message.dstLang`. That is wrong: the sender fills `dstLang` from **its own** TTS setting, and there is no translation step anywhere (the PS does not ask for one). The text is always in the language that was spoken — `srcLang`. Voicing it with the `dstLang` voice reproduces exactly the Devanagari-into-a-Malayalam-voice bug this task exists to fix, whenever the two differ. Use `srcLang`.

```kotlin
                // Speak with the voice for the language the text is WRITTEN in (srcLang). There is
                // no translation step, so any other voice reads the wrong script. Using the local
                // ttsLanguage (or the sender's dstLang) fed e.g. Devanagari text to a Malayalam voice.
                val targetLang = message.srcLang.ifBlank { ttsLanguage }
                val synth = ttsModule.synthesize(message.text, targetLang)
```

If no voice exists for `srcLang` yet (before T17b lands), `synthesize` returns null and reports a real error — that is the correct, honest behaviour. Do not fall back to a different language's voice.

### VERIFY

Phone A: STT language Hindi, TTS language Malayalam. Phone B: TTS language Malayalam. Speak Hindi on A. Phone B must speak **Hindi** (the text's language), not attempt it with the Malayalam voice.

---

## T44 · Fix the confidence score

**File:** `core/audio/STTModule.kt`
**Criterion:** ACC

### ANCHOR

```kotlin
    private fun estimateConfidence(logits: Array<FloatArray>): Float {
        if (logits.isEmpty()) return 0f
        var sumMax = 0f
        for (frame in logits) {
            val maxProb = frame.max()
            sumMax += maxProb
        }
        return (sumMax / logits.size).coerceIn(0f, 1f)
    }
```

### REPLACEMENT

```kotlin
    /**
     * Mean per-frame max softmax probability.
     *
     * The previous version averaged the raw max *logit* and clamped to [0,1]. Logits are
     * unbounded, so that number carried no information — and it was transmitted on the wire
     * and shown in the UI as a confidence percentage.
     */
    private fun estimateConfidence(logits: Array<FloatArray>): Float {
        if (logits.isEmpty()) return 0f
        var sum = 0f
        for (frame in logits) {
            var maxLogit = Float.NEGATIVE_INFINITY
            for (v in frame) if (v > maxLogit) maxLogit = v
            var expSum = 0.0
            for (v in frame) expSum += kotlin.math.exp((v - maxLogit).toDouble())
            sum += (1.0 / expSum).toFloat()   // exp(max - max) / sum = 1 / sum
        }
        return (sum / logits.size).coerceIn(0f, 1f)
    }
```

---

## T47 · Fix the RAM metric

**File:** `core/service/ITantraForegroundService.kt`
**Criterion:** EFF / DOC

### ANCHOR

```kotlin
                val runtime = Runtime.getRuntime()
                val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024f * 1024f)
                _ramUsageMbFlow.value = usedMb
```

### REPLACEMENT

```kotlin
                // Java heap only misses every ONNX model, which are native allocations — the
                // reported figure was a small fraction of real usage. totalPss counts native.
                val memInfo = android.os.Debug.MemoryInfo()
                android.os.Debug.getMemoryInfo(memInfo)
                _ramUsageMbFlow.value = memInfo.totalPss / 1024f
```

### VERIFY

```bash
adb shell dumpsys meminfo com.itantra.debug | grep TOTAL
```

The value shown in the app must now be close to the `TOTAL PSS` reported here.

---

## T31 · VAD pre-roll buffer

**File:** `core/audio/AudioCaptureModule.kt`
**Criterion:** ACC — currently the first phoneme of every utterance is clipped.

### ANCHOR

```kotlin
                var readySegment: FloatArray? = null
                if (isSpeech) {
                    silenceChunkCount = 0
                    synchronized(bufferLock) {
                        // Guard against infinite accumulation
                        if (speechBuffer.sumOf { it.size } < MAX_SPEECH_BUFFER_SAMPLES) {
                            speechBuffer.add(floatChunk)
                        }
                    }
                } else {
```

### REPLACEMENT

```kotlin
                var readySegment: FloatArray? = null
                if (isSpeech) {
                    silenceChunkCount = 0
                    synchronized(bufferLock) {
                        // On the rising edge, prepend the pre-roll. Energy VAD only fires once
                        // the signal is already loud, so unvoiced onsets (/k/ /t/ /p/, initial
                        // fricatives) land in the chunks BEFORE the trigger and were being lost.
                        if (speechBuffer.isEmpty()) {
                            preRoll.forEach { speechBuffer.add(it) }
                        }
                        if (speechBuffer.sumOf { it.size } < MAX_SPEECH_BUFFER_SAMPLES) {
                            speechBuffer.add(floatChunk)
                        }
                    }
                    preRoll.clear()
                } else {
                    // Keep the most recent PRE_ROLL_CHUNKS of silence as lookback.
                    preRoll.addLast(floatChunk)
                    while (preRoll.size > PRE_ROLL_CHUNKS) preRoll.removeFirst()
```

Add to the companion object, beside `MAX_SPEECH_BUFFER_SAMPLES`:

```kotlin
        /** 300ms of lookback at 100ms per chunk. */
        private const val PRE_ROLL_CHUNKS = 3
```

Add as a class member beside `speechBuffer`:

```kotlin
    /** Ring of recent silent chunks, prepended when speech starts. Guarded by bufferLock. */
    private val preRoll = ArrayDeque<FloatArray>()
```

### DO NOT

Do not remove the existing `speechBuffer.add(floatChunk)` inside the silence branch — trailing silence is deliberate and gives the model a clean sentence boundary.

---

## Tasks deliberately not specified here

These need judgement or external data and must **not** be attempted by an agent working from this document alone:

| Task | Why |
| --- | --- |
| T23, T29 | Requires reading the real NeMo checkpoint config and running Python. A guessed config is worse than none. |
| T30, T54 | Requires choosing a test set and running native-speaker evaluation. |
| T48, T49, T50 | Beam search, KenLM and streaming inference are design work, not edits. Spec them separately before implementing. |
| T55 | Merged into T64 Step 6 (`IMPLEMENTATION_SPEC_2.md`). |
| T19 | **Superseded by T64 (2026-09-23).** Odia STT does exist upstream at AI4Bharat; export it per `IMPLEMENTATION_SPEC_2.md` T64. Still never substitute another language's model. |

---

_iTantra · Smart India Hackathon 2026 · Problem Statement #26173_
