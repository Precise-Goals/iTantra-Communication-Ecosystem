# iTantra — Engineering Improvement Plan

> Smart India Hackathon 2026 · Problem Statement PS-26173
> As of 2026-09-20 · Audited against `docs/comprehensive-documentation` @ `e7b07c4`
> **Second audit 2026-09-23** — further findings and corrections in [§10](#10-addendum--second-audit-2026-09-23). Read §10 before acting on §3.3, §3.4, §7.1, §7.2 or the Odia position.

---

## 1. Executive summary

One unoptimised function in `STTModule` costs **952 ms of CPU per 3 seconds of audio** where an FFT costs **22 ms** — a measured **43.6× penalty** that lands on both the Latency and Efficiency scores before ONNX Runtime is even invoked. Fixing it is roughly one day of work and is the highest-value change in the repository.

Every claim in this document was checked against the source tree. The headline finding was reproduced in a standalone benchmark rather than estimated; the harness is in §9.

| Criterion | Weight | Current standing |
| --- | --- | --- |
| **Accuracy** — STT word error rate, TTS legibility and flow | **40%** | Silent preprocessing mismatches against the NeMo training config; greedy-only CTC decoding; VAD clips word onsets; TTS quality degraded by an unnecessary resample |
| **Efficiency** — model size, app size, RAM/flash, idle CPU | **20%** | Roughly 3× more native binary than needed; 2.18 GB compulsory model bundle; unbounded model caches; RAM self-measurement reads the wrong number |
| **Latency** — speech→STT, text→audio, RTF, cross-device delta | **20%** | Dominated by the function above; none of the four required numbers are instrumented today |

A fourth 20% criterion exists in the rubric but was cut off in the source screenshot, so it is not covered here.

### How to read this

§2–5 group findings by scored bucket. §6 covers instrumentation — the rubric asks for four specific measurements that cannot currently be produced. §7 lists correctness bugs and places where the documentation claims capability the code does not have. §8 is the prioritised work plan; start there if time is short. §9 records how each claim was verified.

---

## 2. Critical finding: mel feature extraction is 43.6× slower than it needs to be

`STTModule.computePowerSpectrum()` implements the Fourier transform as a **naive O(N²) DFT**, calling `Math.cos` and `Math.sin` inside the inner loop. `applyMelFilterbank()` then rebuilds the entire mel filterbank — 82 `pow()`/`log10()` calls plus 80×201 triangular weights — on **every single frame**.

Both functions were ported verbatim to Java and timed against a correct radix-2 FFT implementation:

| 3 s utterance (298 frames) | Wall time (desktop JVM) |
| --- | --- |
| Current: naive DFT + per-frame filterbank | **952.3 ms** |
| Radix-2 FFT-512 + same filterbank | **21.8 ms** |
| **Speedup available** | **43.6×** |

The current path makes **47,918,400 transcendental calls per utterance**. A mid-range ARM phone runs roughly 3–5× slower than the benchmark machine, which puts feature extraction alone at an estimated **3–5 seconds for 3 seconds of audio**.

That is the whole explanation for a real-time factor above 1.0. **The model is not the bottleneck — the code that feeds it is.**

### The fix

1. **Replace the DFT with a radix-2 FFT at n_fft=512.** Roughly 80 lines of Kotlin, or add JTransforms. Zero-pad the 400-sample window to 512, which is also what the training config expects (see §3.1).
2. **Precompute the mel filterbank once**, at module init, as a sparse `(startBin, endBin, weights[])` table. The per-frame cost drops from 16,080 multiply-accumulates plus 82 transcendental calls to a few hundred multiply-accumulates.
3. **Pool the frame buffer.** `copyOfRange` allocates a fresh `FloatArray(400)` per frame — 298 allocations per utterance. Reuse one buffer.

**Estimated effort: one day**, including a regression test. This is the correct first change regardless of what else is done, because every latency measurement taken before it will be dominated by this one function and will have to be retaken afterwards.

---

## 3. Accuracy (40%)

This is the heaviest-weighted criterion and the one with the most silent defects. None of the issues below throw an error — they quietly raise word error rate or degrade synthesized audio.

### 3.1 The mel pipeline does not match the NeMo training config

IndicConformer is NeMo-trained. Any mismatch between the runtime preprocessor and `AudioToMelSpectrogramPreprocessor` puts the encoder off-distribution. The codebase already hit the catastrophic version of this once — the per-feature normalization fix recorded in the `STTModule` comments, where every recording decoded to the same repeated character.

These mismatches remain:

| NeMo default | Current code | Expected impact |
| --- | --- | --- |
| `preemph=0.97`, i.e. `x[i] − 0.97·x[i−1]` | **absent** | Spectral tilt wrong across the whole band. Likely the largest remaining WER term. |
| `mel_norm="slaney"` (area-normalized filters) | unnormalized triangles | Wide high-frequency mel bands carry systematically more energy than in training |
| `n_fft=512`, `center=True` | 400-point, no centering | Different bin resolution plus a half-window frame offset |
| `torch.hann_window` (periodic) | symmetric, divides by `N−1` | Small, but free to correct |
| `log_zero_guard_value=2**-24` | `1e-10` | Minor |
| Std computed unbiased (N−1) | biased (N) | Minor |

**How to fix it properly.** Do not patch these from a list — extract `cfg.preprocessor` from the AI4Bharat NeMo checkpoint and match it field for field. Then build a **golden test**: run NeMo's preprocessor in Python over a fixed WAV, save the feature matrix, and assert the Kotlin output matches to ~1e-3 in a JVM unit test. There are currently **zero tests on the feature path**, which is precisely where accuracy lives.

### 3.2 Decoding is greedy-only

`CtcDecoder` exposes `greedyDecode()` and nothing else. Adding prefix beam search at beam width 8–16, plus a small per-language KenLM n-gram, typically buys **10–20% relative WER** on Indic ASR. Beam search on its own is around 150 lines and needs no additional model download.

### 3.3 VAD clips the first phoneme of every utterance

> **Superseded in part by §10.1:** the neural Silero VAD was disabled on a misdiagnosis and should be repaired (T62). The adaptive energy detector below becomes the fallback, not the primary. The pre-roll buffer applies to either.

The active energy path in `VADModule` uses a **fixed** threshold (`rms > 0.025f → 0.85`), despite the README describing it as adaptive. Two distinct accuracy costs:

1. **No pre-roll buffer.** Accumulation begins only *after* RMS crosses the threshold, so low-energy onsets — unvoiced stops `/k/ /t/ /p/`, initial fricatives — are cut off. Every utterance loses its opening phoneme. *Fix:* keep a 300 ms ring buffer and prepend it when speech triggers. Roughly 20 lines for an immediate WER win.
2. **Fixed absolute threshold.** In a noisy environment RMS never drops below 0.025, so the detector never releases and the 30 s cap is hit. In a quiet room a soft speaker never triggers at all. *Fix:* track a rolling noise floor (EMA or percentile over quiet frames) and apply hysteresis — trigger at floor+9 dB, release at floor+4 dB.

### 3.4 The capture chain is not tuned for ASR

> **Amended by §10.2:** `VOICE_RECOGNITION` is right for PTT, but phone mode also needs an echo gate (T63), or received TTS is re-transcribed and sent back.

- `AudioCaptureModule` opens `MediaRecorder.AudioSource.MIC`. Switching to **`VOICE_RECOGNITION`** selects the path Android tunes for speech recognition and disables the aggressive voice-call processing that smears spectra. A standard, free WER improvement.
- No DC-offset removal, and no `NoiseSuppressor` or `AutomaticGainControl` `AudioEffect` attached to the session.

### 3.5 TTS quality is degraded by an unnecessary resample

`TTSModule.resampleTo16k()` downsamples Piper's 22050 Hz VITS output to 16 kHz by **linear interpolation with no anti-aliasing low-pass filter**. Everything above 8 kHz folds back into the audible band as aliasing — the metallic, buzzy artifact that judges will hear directly when scoring "human legibility and flow".

It is also **unnecessary**. `AudioTrack` plays 22050 Hz natively. **Delete the resampler and construct the track at `audio.sampleRate`.** This is a strict quality gain at negative effort. If resampling is ever genuinely needed, low-pass first or use a polyphase/sinc kernel rather than linear interpolation.

### 3.6 No text normalization before synthesis

Text goes straight from the wire to espeak-ng. Numbers (`108`, `3.5 km`), Latin-script tokens embedded in Devanagari, and punctuation are mispronounced or dropped. A per-language number-to-words expander plus abbreviation handling is a few hundred lines and visibly improves the flow half of this criterion.

---

## 4. Latency (20%)

§2 covers the dominant term. Beyond it, the pipeline has four structural delays, each of which maps onto a number the rubric asks for.

```mermaid
flowchart LR
  A[Speech ends] -->|800ms fixed wait| B[Endpoint fires]
  B -->|naive DFT<br/>3-5s| C[Features]
  C -->|ONNX| D[Transcript]
  D -->|protobuf<br/>TCP| E[Peer receives]
  E -->|full-sentence<br/>VITS| F[Waveform]
  F --> G[First audio out]
```

### 4.1 Fixed 800 ms endpointing delay

`VADModule.SILENCE_DURATION_MS = 800` sets a hard floor under "time delay between the words said and STT completion" for every utterance in continuous mode. Make it adaptive — 400–600 ms once the endpoint is confident — or trigger on the VAD's release edge rather than a fixed chunk count.

### 4.2 STT is not streaming

`AudioCaptureModule` buffers the entire utterance, then calls `transcribe()` on the whole thing. Chunked inference over an overlapping window lets the model work while the speaker is still talking, so on PTT release only the final chunk remains. This is the difference between roughly 2 s and roughly 200 ms of perceived latency, and it is the largest structural win after the FFT.

### 4.3 TTS is not streaming

In `ITantraForegroundService.onTextReceived`, `ttsModule.synthesize()` returns the complete waveform before `audioPlayback.play()` is called. Split the text on sentence or clause boundaries and begin playing chunk 1 while chunk 2 synthesizes. This attacks "time delay between the text received and audio processed and played" directly.

### 4.4 Cold start on first message

Both `STTModule.ensureLoaded()` and `TTSModule.getOrLoadTts()` lazy-load on first use — a ~197 MB ONNX graph and a ~70 MB VITS voice respectively. The first message of every session pays this. Warm the configured language pair at service start, off the critical path.

### 4.5 Minor: the TRANSMITTING stage is never observable

`_pipelineStage.value = PipelineStage.TRANSMITTING` is set and reset to `IDLE` two lines later, synchronously. The UI can never render that state. Cosmetic, but it undercuts the claim that the pipeline is not a black box.

---

## 5. Efficiency (20%)

This criterion covers model size, app size, RAM/flash footprint and idle-listening CPU. Two of the four are currently far worse than they need to be, and one is being measured incorrectly.

### 5.1 App size: roughly 3× more binary than necessary

`app/build.gradle.kts` sets `abiFilters = ["arm64-v8a", "armeabi-v7a", "x86_64"]` and builds a single universal APK. The sherpa-onnx AAR alone is 37 MB across those ABIs, plus ONNX Runtime, plus the llama.cpp natives.

- **Switch to ABI splits or an App Bundle**, and ship arm64-v8a only for the demo. `x86_64` is emulator-only; `armeabi-v7a` cannot run the LLM at all — `LlmModule.isDeviceSupported()` already gates it out. Expect a **50–65% APK reduction for a configuration change**.
- **Two copies of ONNX Runtime ship in the same APK**: the `onnxruntime-android` dependency, and `sherpa-onnx-static-link-onnxruntime` which statically links its own. sherpa-onnx can run the STT graphs too. Consolidating onto one runtime removes an entire native runtime from flash.

### 5.2 Model size: the compulsory bundle is 2.18 GB, not 169 MB

`ModelPack.coreTransceiverPacks()` returns 17 packs. Totalling the `sizeBytes` in `ModelRegistry`:

| Component | Count | Size |
| --- | --- | --- |
| STT (IndicConformer INT8) | 9 | 1778.4 MB |
| TTS (VITS voices) | 5 | 389.6 MB |
| espeak-ng data | 1 | 7.25 MB |
| Silero VAD | 1 | 2.22 MB |
| Language ID | 1 | 0.89 MB |
| **Total** | **17** | **~2178 MB** |

The README states the core bundle is "~169 MB". That is **off by a factor of 13** and should be corrected before judging.

Two fixes, both worth arguing on stage:

1. **Stop making all 9 STT languages compulsory.** Make the required bundle VAD + espeak-ng + the user's chosen language pair: roughly **275 MB instead of 2.18 GB**. Download the rest on demand.
2. **Re-examine the 197 MB "INT8" export.** 197.6 MB is large for an INT8 Conformer and is nearly identical across all nine languages, which suggests a hybrid quantization or an unused RNNT decoder branch shipping alongside the CTC head. Re-export CTC-only with full dynamic INT8 quantization and measure — a 2× reduction with no WER change is plausible.

### 5.3 RAM: unbounded caches, and the wrong number is being reported

- **`STTModule.sessionCache` and `TTSModule.ttsCache` are never evicted.** Every language ever used stays resident; three languages is roughly 600 MB of native memory held for the process lifetime. Add an LRU capped at 1–2 sessions that calls `release()` on eviction.
- **`startRamMonitoring()` measures the wrong thing.** It computes `Runtime.totalMemory() − freeMemory()`, which is **Java heap only**. Every ONNX model is a native allocation and is completely invisible to it, so the figure shown in the UI drastically understates the real footprint. Switch to `Debug.getMemoryInfo().totalPss` or `getMemoryStat("summary.total-pss")`. This must be corrected before any footprint number is self-reported.

### 5.4 Idle listening CPU

`initializeModules()` starts capture only when `connectionMode == PHONE_MODE`; the default is `PUSH_TO_TALK`, so nothing runs at boot. But "CPU usage during idle listening" is exactly the PHONE_MODE case, and there capture never stops: a permanent `AudioRecord` loop, the energy VAD, and a permanently held wake lock. (See §7.7 — PHONE_MODE is currently unreachable from the UI, which is a separate problem.)

The arithmetic is cheap; the overheads are not:

- A fresh `FloatArray(1600)` is allocated per 100 ms chunk — about 64 KB/s of garbage, forever. Reuse a preallocated buffer.
- `speechBuffer.sumOf { it.size }` is recomputed on every chunk. Track a running total.
- Consider processing 200–300 ms per wakeup rather than 100 ms. At idle, wakeup count dominates power draw, not arithmetic.

Measure the result honestly with `dumpsys cpuinfo` or Perfetto over a 10-minute idle window, and put that figure on a slide.

---

## 6. The measurement gap

**None of the four numbers the Latency criterion asks for are instrumented.** A grep across `app/src/main/java` for RTF or end-to-end timing returns only `latencyMs`, which is the peer ping round-trip from `SocketTransport.startPingLoop()` — a network metric, not a pipeline one.

| Rubric asks for | Instrumented today |
| --- | --- |
| Delay between words said and STT completion | No |
| Delay between text received and audio played for TTS | No |
| Real Time Factor (RTF) | No |
| Sentence said → same sentence starts as audio on the other phone | No |

This should be built **before** any optimisation work, for two reasons: there is currently no evidence to present, and there is no way to demonstrate that a fix helped.

### What to add

1. **Stamp the send path**: `captureEndNs`, `featureDoneNs`, `inferDoneNs`, `txNs`. The gap `captureEndNs → inferDoneNs` is the first rubric number.
2. **Stamp the receive path**: `rxNs`, `ttsDoneNs`, `firstAudioFrameNs`. Take `firstAudioFrameNs` from `AudioTrack.getPlaybackHeadPosition()` or immediately before the first `write()`, not after synthesis — the rubric asks when audio was *played*, not when it was ready. The gap `rxNs → firstAudioFrameNs` is the second number.
3. **Compute RTF per utterance** as `inference_wall_time / audio_duration`, log it, and surface a rolling median in the UI. Report it separately for feature extraction and for the ONNX session so the §2 fix is visible in the numbers.
4. **Cross-device delta.** `TransceiverMessage` already carries `timestamp`. Derive a clock offset from the existing ping/ACK loop (offset ≈ RTT/2) so the two devices' clocks are comparable, then report `firstAudioFrame(B) − speechEnd(A)`. This is the headline demo number.
5. **Write to CSV** in `filesDir`, one row per utterance. That turns a claim into a table you can put on a slide.

Roughly one day of work, and it makes every subsequent change measurable.

---

## 7. Bugs and claim-vs-code mismatches

These are separate from the performance and accuracy work above. Each is something a judge could surface by testing the app or reading the docs, and each is cheap to fix.

### 7.1 The receiver ignores `dstLang`

> **Corrected by §10.13:** the fix is to use `srcLang`, not `dstLang`.

`ITantraForegroundService.onTextReceived()` calls `ttsModule.synthesize(message.text, ttsLanguage)` — the **receiver's local setting**, not the `dstLang` the sender put on the wire. If the sender speaks Hindi and the receiver is configured for Malayalam, Devanagari text is fed to a Malayalam VITS voice. The output is meaningless.

*Fix:* honour `message.dstLang`, falling back to `srcLang` when no voice for `dstLang` is installed.

### 7.2 There is no translation anywhere in the codebase

> **Resolved by §10.12:** the problem statement does not require translation. Remove the claim; do not build it for the judged submission.

A grep for `translat` across `app/src/main/java` returns only UI icon imports and two hard-coded strings in `TacticalAiEngine`. No machine translation step exists in the transceiver path.

`docs/RANGE_STRATEGY.md` §10 claims: *"it translates: speak Hindi, the receiver hears Malayalam."* Nothing implements this. Either wire an offline MT step — a distilled IndicTrans2, or a phrase table seeded from the nine-language corpus already in `TacticalAiEngine` — or remove the claim. A judge who tests it will find it.

### 7.3 The auto-detect language toggle does nothing

`LanguageDetector` is only called from the AI Assistant path in `MainViewModel`, never from the transceiver STT path. The toggle on the Transceiver screen has no effect on which STT model runs.

It also cannot work as currently designed: `LanguageDetector` operates on text, but the language must be known to choose the STT model that produces that text. A real fix needs audio-side language ID — the `LANG_DETECTION` pack is already downloaded — or running two or three candidate models and selecting by confidence.

### 7.4 The transmitted confidence score is meaningless

`STTModule.estimateConfidence()` averages the maximum **logit** per frame and clamps to [0, 1]. Logits are unbounded, so the result carries no information. It is transmitted on the wire and displayed in the UI. *Fix:* apply softmax per frame, take the max, then average — or use mean per-frame entropy.

### 7.5 Documentation describes a VAD that is not running

The README describes an "adaptive, field-tested energy-based detector". The code is a fixed threshold at `rms > 0.025f`. Fix the detector (§3.3) and the description will become true; until then the two disagree.

### 7.6 The README's core-bundle size is wrong

Stated as "~169 MB"; actually ~2.18 GB (§5.2). Correct this before judging.

### 7.7 Phone mode is unreachable

`ConnectionMode.PHONE_MODE` exists in `AppState.kt` and `ITantraForegroundService.setConnectionMode()` handles it, but `setConnectionMode` is **never called from anywhere in `ui/`**. The app is permanently in `PUSH_TO_TALK`.

The problem statement requires the opposite behaviour explicitly: *"it should work like a walkie talkie using push to talk feature, if turned off it should work like a phone."* This is a hard requirement, not a scored point. Wiring the toggle is small — the service side already exists.

### 7.8 Concurrent messages produce overlapping audio

`AudioPlaybackManager.play()` launches a fresh coroutine and builds a new `AudioTrack` on every call, with no queue and no mutex. Two messages arriving close together play simultaneously and garble each other. For a walkie-talkie under realistic traffic this is a visible defect. Add a single-consumer playback queue, with ALERT messages able to pre-empt the queue head.

---

## 8. Prioritised work plan

Ordered by score movement per day of work. Items 1, 4 and 6 together are about a day and a half and touch all three scored criteria.

| # | Change | Criterion | Effort | Why here |
| --- | --- | --- | --- | --- |
| 1 | FFT-512 + precomputed filterbank + pooled buffers | Latency, Efficiency | 1 day | 43.6× measured; nothing else can be measured meaningfully until this lands |
| 2 | Latency/RTF instrumentation + CSV export | Latency | 1 day | Four required numbers, none currently produced |
| 3 | Match NeMo preprocessor; add golden-reference test | Accuracy | 2 days | Heaviest weight; the defect is silent |
| 4 | Delete the TTS downsampler, play at 22050 Hz | Accuracy | 1 hour | Strict quality gain, negative effort |
| 5 | VAD pre-roll buffer + adaptive noise floor; `VOICE_RECOGNITION` source | Accuracy | 1 day | Stops clipping the first phoneme of every utterance |
| 6 | ABI split to arm64-v8a; drop the duplicate ONNX Runtime | Efficiency | 2 hours | 50–65% APK reduction for a config change |
| 7 | Core bundle = chosen language pair, not all nine | Efficiency | 0.5 day | 2.18 GB → ~275 MB |
| 8 | LRU session cache; fix RAM metric to total PSS | Efficiency | 0.5 day | Bounded footprint, and correct self-reported numbers |
| 9 | Honour `dstLang`; softmax the confidence score | Accuracy | 2 hours | Visible in a live demo |
| 10 | Streaming TTS on sentence boundaries | Latency | 1 day | Roughly halves perceived receive latency |
| 11 | CTC prefix beam search (optional KenLM) | Accuracy | 2 days | 10–20% relative WER |
| 12 | Streaming/chunked STT inference | Latency | 3 days | Largest structural latency win, but depends on items 1 and 3 |

### Suggested sequencing

```mermaid
flowchart TD
  P1["Week 1<br/>FFT + instrumentation<br/>+ TTS resample + ABI split"] --> P2
  P2["Week 2<br/>NeMo preprocessor + golden test<br/>+ VAD pre-roll"] --> P3
  P3["Week 3<br/>Bundle slimming + LRU cache<br/>+ dstLang + confidence"] --> P4
  P4["Week 4<br/>Streaming TTS + beam search"]
```

Take a full baseline measurement (RTF, the three latency deltas, APK size, idle CPU, total PSS) at the end of week 1, immediately after item 2 lands, and repeat it at the end of each week. That gives a before/after table for the submission rather than an assertion.

### Open question on scope

Item 12 is the only entry over two days and the only one with hard dependencies. If the timeline is tight, drop it — items 1–11 together should bring RTF comfortably below 1.0 and make the perceived latency acceptable without restructuring inference.

---

## 9. Appendix: how this was verified

Audited against `docs/comprehensive-documentation` at commit `e7b07c4`. Nothing here is recalled — every claim traces to a file or a run.

### Source of each claim

| Claim | Where it was checked |
| --- | --- |
| Naive DFT, per-frame filterbank | `core/audio/STTModule.kt` — `computePowerSpectrum()`, `applyMelFilterbank()` |
| 43.6× speedup | Standalone Java benchmark, below |
| Missing preemphasis / slaney norm / centering | Same file, `extractLogMelSpectrogram()`, compared against NeMo `AudioToMelSpectrogramPreprocessor` defaults |
| Greedy-only decoding | `core/audio/CtcDecoder.kt` — only `greedyDecode()` is exposed |
| Fixed VAD threshold, no pre-roll | `core/audio/VADModule.kt` energy branch; `core/audio/AudioCaptureModule.kt` buffering |
| Unfiltered 22.05k→16k resample | `core/audio/TTSModule.kt` — `resampleTo16k()` |
| Three ABIs, duplicate ONNX Runtime | `app/build.gradle.kts` — `abiFilters`, dependency block |
| 2.18 GB core bundle | `ModelRegistry.kt` `sizeBytes` values × `ModelPack.coreTransceiverPacks()` |
| Unbounded caches | `STTModule.sessionCache`, `TTSModule.ttsCache` — no eviction path |
| RAM metric is Java-heap only | `ITantraForegroundService.startRamMonitoring()` |
| Capture always running | `ITantraForegroundService.initializeModules()` |
| `dstLang` ignored | `ITantraForegroundService.onTextReceived()` |
| No translation | `grep -rn "translat" app/src/main/java -i` — only icon imports and two `TacticalAiEngine` strings |
| Auto-detect unwired | `LanguageDetector` referenced only in `MainViewModel`'s AI Assistant path |
| Confidence uses raw logits | `STTModule.estimateConfidence()` |
| No RTF/E2E instrumentation | `grep -rn "RTF\|realTimeFactor\|endToEnd" app/src/main/java` — only peer ping `latencyMs` |

### Re-running the audit

```bash
grep -rn "translat" app/src/main/java --include=*.kt -i
grep -rn "RTF\|realTimeFactor\|endToEnd" app/src/main/java --include=*.kt
grep -n "sizeBytes" app/src/main/java/com/itantra/core/download/ModelRegistry.kt
grep -n "abiFilters" app/build.gradle.kts
```

### The benchmark

Both functions were ported verbatim from `STTModule.kt` into a single-file Java program and timed against a radix-2 FFT-512, JIT warmed, on 3 seconds of synthetic audio (298 frames of 400 samples, 160-sample hop). Result:

```
Utterance: 3.0 s audio -> 298 frames
Current  (naive DFT + per-frame filterbank):    952.3 ms
FFT-512  (+ same per-frame filterbank)     :     21.8 ms
Speedup on this desktop JVM: 43.6x
Naive DFT trig calls per utterance: 47,918,400
```

This is a desktop JVM figure and is therefore **optimistic**. A mid-range ARM phone runs roughly 3–5× slower, so treat 952 ms as a floor rather than an estimate of on-device cost. The absolute numbers matter less than the ratio, which is a property of the algorithm and holds on any hardware.

### Not covered

- The fourth 20% rubric criterion, which was cut off in the source screenshot.
- On-device measurement of any kind. Every latency and CPU figure here is derived from code inspection or the desktop benchmark. The instrumentation in §6 is what turns these into measured numbers.
- The `worktree-afsk-radio-link` branch (`MeshLink`, `AfskModem`, `HdlcFramer`). It compiles, has no tests, and is off the critical path for these three criteria.

---

## 10. Addendum — second audit (2026-09-23)

A second pass over the same tree, plus the uncommitted working-tree changes on `docs/comprehensive-documentation`. Four findings **correct** earlier planning (§10.1, §10.2, §10.3, §10.13); the rest are gaps no earlier document listed. New tasks are T62–T69 in [`TASKS.md`](TASKS.md), specced in [`IMPLEMENTATION_SPEC_2.md`](IMPLEMENTATION_SPEC_2.md) Group G, with every code anchor checked against both the committed code and the working tree.

### 10.1 Correction — the Silero VAD was disabled on a misdiagnosis

`VADModule.initialize()` forces `VadBackend.BASIC_ENERGY` with the comment that the bundled model is *"empirically non-functional"*: it was tested against **silence, a 200 Hz sine and white noise**, and returned ~0.001 for all three.

That is the correct output. None of those inputs is speech; a working speech detector *should* reject all three. The test proves nothing about the model — it was never run on a recording of a person talking.

There is also a probable real defect. `ModelRegistry` downloads `silero_vad.onnx` from the repo's **`master`** branch, and the code confirms its signature is a single combined `state [2,1,128]` — that is the **v5+ model**, not v4 (the on-disk name `silero_vad_v4.onnx` is misleading). The official v5 wrapper (`OnnxWrapper` in `silero_vad/utils_vad.py`) **prepends the last 64 samples of the previous window** to every 512-sample window, feeding `[1, 576]`. `VADModule.process()` feeds `[1, 512]` with no context. That alone can flatten the output.

**Consequence for the plan:** §3.3's adaptive energy detector (T32) is still worth doing, but as the *fallback*. The primary fix is to repair Silero (T62). A neural VAD is also the right answer for the Efficiency criterion's "CPU during idle listening" — Silero costs well under 1 ms per 32 ms window — and for noisy field conditions, where any energy threshold degrades.

### 10.2 Correction — `VOICE_RECOGNITION` and "no AEC" break phone mode

§3.4 recommends `VOICE_RECOGNITION`, and `IMPLEMENTATION_SPEC_2.md` T33 says *"Do not attach `AcousticEchoCanceler` — this is a push-to-talk radio, not a speakerphone."* That was true until T37. **Phone mode makes it a speakerphone.**

In `PHONE_MODE` capture runs continuously. When a message arrives, `onTextReceived` plays TTS through the loudspeaker; nothing mutes or gates the mic (`grep -rn "AcousticEchoCanceler\|VOICE_COMMUNICATION" app/src/main/java` → nothing in capture). The phone hears its own playback, the VAD fires, STT transcribes it, and **the received sentence is transmitted back to the sender**. Two phones in phone mode can ping-pong a sentence indefinitely. This will surface in the first minute of a judge testing phone mode.

**Fix (T63):** gate capture while `PipelineStage == SPEAKING` plus a ~250 ms tail, in both modes — deterministic, zero-cost, and correct walkie-talkie semantics. Optionally, in phone mode only, use `VOICE_COMMUNICATION` with `AcousticEchoCanceler` to allow barge-in. Keep `VOICE_RECOGNITION` for PTT.

### 10.3 Correction — Odia STT does exist upstream

Every planning document states that no Odia STT model exists. That is true only of the **third-party mirror** the registry downloads from (`parismitaglobalsolutions/indicconformer-sherpa-onnx`). AI4Bharat publishes Odia directly: [`ai4bharat/indicconformer_stt_or_hybrid_ctc_rnnt_large`](https://huggingface.co/ai4bharat/indicconformer_stt_or_hybrid_ctc_rnnt_large) — same family as the other nine, Conformer-Large, ~120 M parameters, hybrid CTC-RNNT.

10/10 STT is therefore an **export job**, not an impossibility (T64). Two consequences:

- The "state 9/10 with the reason" position in T19 is withdrawn. A judge who searches Hugging Face for thirty seconds will find the model.
- The same card supports §5.2's suspicion about size: **~120 M parameters at INT8 should be ~120–130 MB**, not the mirror's ~197 MB. Exporting all ten languages CTC-only from the AI4Bharat originals (T55 + T64 as one job) likely fixes Odia and cuts ~600 MB from a full install in the same pass.

⚠️ `model-export/export_indicconformer.py` cannot be reused as-is: it restores the checkpoint as `EncDecMultiTaskModel` (NeMo's Canary class, not the hybrid CTC-RNNT class) and exports a raw-audio graph, whereas `STTModule` feeds 80-bin mel features. Treat it as historical.

### 10.4 Gap — the app cannot send an ALERT

`ITantraForegroundService.broadcastAlert()` exists and the receive side (alarm stream, max volume, DND bypass) is implemented. **Nothing in `ui/` calls `broadcastAlert()`** (`grep -rn "broadcastAlert" app/src/main/java/com/itantra/ui` → nothing). The alert path is unreachable from the app, so "alert type messages will be announced at highest volume non-interruptible" cannot be demonstrated. `ACTION_PLAN.md` §2 rated this "mostly done"; it is **not demonstrable**.

**Fix (T66):** an SOS control on the Transceiver screen — one-tap preset phrases (the translated `AlertTemplate` set already exists in the domain model) plus free-speech-as-alert — and on the receiver a full-screen alert with the text.

### 10.5 Gap — nothing is a "voice note"

The PS: TTS output *"will be played as a voice note"*. Today the waveform is synthesized, played once and discarded; the chat shows text only. There is no replay (`grep -rni "\.wav\|voiceNote\|replay" app/src/main/java` → only a doc comment and unrelated `SharedFlow` params).

**Fix (T67):** write each received utterance as a 16-bit WAV to `filesDir/voicenotes/<senderId>_<sequence>.wav`, show a play button and duration on the message bubble, and route replays through the T38 playback queue.

### 10.6 Gap — phrase-level pipelining exists in embryo, but is unsafe; fix it before T50

T50 (chunked streaming inference) is correctly flagged as risky: IndicConformer is non-streaming, and chunk seams cost WER.

A cheaper structure costs **no** accuracy: while PTT is held, cut at natural pauses and send each phrase through the existing batch `transcribe()` as a complete utterance. Phone B starts speaking phrase 1 while phone A is still saying phrase 2. This is literally what the PS describes: *"after detecting pauses and stoppages should form the sentences detected and must instantly … stream the data"*.

The capture loop **already does a version of this** — it cuts at 800 ms of silence even while PTT is held and calls `onSpeechReady` → STT → transmit. But it has two real bugs:

1. **Inference blocks capture.** `onSpeechReady(...)` — the whole STT inference — is called *inside* the capture loop. The loop stops reading the microphone for the duration, while `AudioRecord`'s buffer holds only ~200 ms (`CHUNK_SIZE * 2`). Speech during inference is lost.
2. **Concurrent inference.** `stopPTT()` calls `sttModule.transcribe()` directly, possibly while a mid-hold segment is still transcribing on another coroutine. In the working tree, the new FFT code reuses member scratch arrays (`fftRe`, `fftIm`, `powerSpectrum`), and the session cache is a plain `HashMap`; two concurrent calls corrupt each other's features. The release flush can also overtake an earlier phrase.

**Fix (T65):** an inference `Mutex` in `STTModule`, one segment queue with a single consumer (the release flush goes through it too), and a 400 ms cut while PTT is held. Do this before T50 and re-measure whether T50 is still needed. Depends on T41, and on T38 on the receiver so phrases queue instead of overlapping.

### 10.7 Gap — the "embedded device" half of the transport requirement is undemonstrated

The PS: *"stream the data through wifi/Bluetooth connected **embedded device** or another phone"*. The phone-to-phone half is done. Nobody has shown the embedded half. A ~100-line ESP32 sketch that accepts the existing 4-byte length-prefixed Protobuf frame over Bluetooth SPP (the RFCOMM transport already speaks this) and shows the text on a small display, or sounds a buzzer on `ALERT`, closes this visibly (T68). Stretch priority — but almost no other team will have it.

### 10.8 Status — work already done in the working tree

The uncommitted working tree on `docs/comprehensive-documentation` (not yet on `main`) already contains:

| Task | State |
| --- | --- |
| T05, T06, T07 — radix-2 FFT-512, sparse Slaney filterbank built once, scratch buffers | Implemented |
| T08, T09, T10, T12 — `core/telemetry/Telemetry.kt`, send/receive stamps, RTF, CSV | Implemented |
| T11 — peer clock offset from the ping loop | Offset recorded; cross-device number not yet computed |
| T13 — resampler removed, `AudioTrack` at voice-native rate | Implemented |
| T14 — `abiFilters` arm64-v8a only | Implemented |
| T25, T27 — Slaney norm, periodic Hann | Implemented |
| T26, T28 — n_fft 512 and `2^-24` log guard done; `center=True` and unbiased std **not** done | Partial |
| T24 — preemphasis | Not done |

**None of it is committed, and none of it has been measured on a device.** The first action is to commit it and take the T16 baseline — otherwise the 43.6× claim remains a desktop benchmark, not a device measurement.

### 10.9 Minor

- **T14 and low-end phones.** arm64-only is right for the judged demo phone, but some sub-₹8,000 Android Go devices are 32-bit only. Once T02 removes llama.cpp (the only reason `armeabi-v7a` was unusable), an `armeabi-v7a` ABI split costs nothing in the arm64 APK. Decide after T03 names the target phone.
- **Model pinning.** The Silero URL points at `master`, so the model can change under you between two downloads. Pin it to a release tag as part of T62.

### 10.11 Bug — Bluetooth only sends from the phone that hosts

`onSTTResult` and `broadcastAlert` send over Bluetooth only when `isBluetoothFallbackActive` is true, and that flag is set only when **this** phone started the Bluetooth server (the Host Beacon toggle, or three Wi-Fi Direct failures). A phone that connected **outbound** as the Bluetooth client keeps the flag false and sends over TCP — to no one. So over Bluetooth, messages flow only from the hosting phone unless both phones turn Host Beacon on. It also blocks T68: the phone is the client when it connects to an ESP32.

**Fix (T69):** send on every transport that currently has a peer (`bluetoothManager.hasConnections()` plus the TCP broadcast), and drop duplicates on receive by `(senderId, sequence, timestamp)`, in case a peer is reachable over both.

### 10.12 Does the problem statement require translation? No.

The PS text asks for: STT in ten languages; sentence formation after pauses; streaming the text over Wi-Fi/Bluetooth; TTS that *"after receiving the Text data should convert it into intelligible speech"*; alerts at highest volume; PTT and phone modes. It never asks for the text to change language between sender and receiver. The "inclusive" motivation is about **literacy** — audio instead of written messages — not about crossing languages.

Consequences:

- **Do not build translation for the judged build.** An offline Indic MT model (e.g. a distilled IndicTrans2) is hundreds of MB and adds latency, against two criteria that score exactly those, for a feature nobody is scoring.
- **Remove translation claims** from any doc or slide (`RANGE_STRATEGY.md` §10 already walks this back; keep it that way).
- **The receiver's voice must follow the text's language** — see §10.13.
- If a judge asks, the honest answer is: *"Out of scope for PS-26173; the architecture is text-first, so an offline MT stage slots in between STT and transmit without touching anything else."* That is a good answer, not a gap.

### 10.13 Correction — T43 should use `srcLang`, not `dstLang`

T43 (and §7.1) fixes the receiver ignoring the wire's language by switching to `message.dstLang`. But the sender fills `dstLang` from **its own** TTS setting, and no translation exists (§10.12). The text is always in the language that was spoken — `srcLang`. Whenever a sender's STT and TTS settings differ, `dstLang` voicing reproduces the Devanagari-into-a-Malayalam-voice bug. The spec now uses `message.srcLang` and reports a real error if that language has no voice yet — never a different language's voice.

### 10.14 First implementation round — what the device run showed (2026-09-24)

T05–T14, T41, T38, T65 and T62 are committed on `feature/latency-pipeline` and reviewed. `docs/latency-evidence/` holds the first two-phone capture. What it established:

- **Measured:** warm STT at 0.20–0.26× real time; neural VAD cutting phrases from real speech while PTT is held; received phrases playing strictly in order.
- **The mid-hold head start was lost to a lazy model load**, not to user behaviour: `STT('hi') loaded in 2306ms` happened during the first phrase. T45 is revised to warm models when the app binds and when the language changes.
- **The telemetry mislabels two things:** model load lands in `feature_ms`, `stt_ms` and `rtf`; and since T65, queue wait is missing from `stt_ms`. The receiver's `tts_ms` includes playback-queue wait (phrase 2's 4.7 s was waiting for phrase 1's audio to finish). Fixed by T71.
- **The walkie-talkie only ever runs Hindi.** Nothing calls `setSTTLanguage()`/`setTTSLanguage()`; the only picker is on the AI Assistant screen. A PS requirement (ten languages) cannot be shown until T72.
- **Likely concurrency bug in `TTSModule`** — no lock around an unsynchronised model cache. T70.

### 10.10 Revised top of the work order

Items 1–4 below slot in ahead of §8's list; the rest of §8 is unchanged.

| # | Change | Criterion | Effort |
| --- | --- | --- | --- |
| 0 | Commit the working-tree changes (§10.8); take the T16 baseline | all | 0.5 day |
| 1 | T63 echo gate — without it phone mode (a hard requirement) self-oscillates | REQ | 3 hours |
| 1b | T69 transport fix — Bluetooth currently sends only from the hosting phone | REQ | 2 hours |
| 1c | T43 with `srcLang` (§10.13) | ACC, REQ | 1 hour |
| 2 | T66 SOS/alert control — without it a hard requirement cannot be shown | REQ | 1 day |
| 3 | T62 repair Silero VAD; keep T32 as fallback | ACC, EFF | 1 day |
| 4 | T64 + T55 export all ten STT languages CTC-only INT8 from AI4Bharat | ACC, EFF, REQ | 3 days |
| 5 | T67 voice notes | REQ | 0.5 day |
| 6 | T65 phrase-level pipelining (before T50) | LAT | 1 day |
| 7 | T68 ESP32 receiver | REQ (stretch) | 1 day |

---

_iTantra · Smart India Hackathon 2026 · Problem Statement #26173_
