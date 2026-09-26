# TTS size, speed and intelligibility — evaluated plan (T79–T83)

**Date:** 2026-09-26 · **Base:** `origin/main` @ `9d6c724` (T78 step 7 merged) · **Owner:** Gaurav
**Runs after:** G12 (in flight) and G13 (baseline). **One exception, act now:** §3 (T17a).

This document checks one external proposal against the repo, the current code and real
measurements. It turns what survives into tasks. The proposal was: "replace the voices with Piper-Low
INT8 for every language, unlock NNAPI, add DSP to compensate". Every number below was measured on
2026-09-26 with the scripts committed alongside it (`model-export/weight_only_int8.py`,
`eval_tts_quant.py`, `tts_roundtrip_cer.py`), unless it is labelled *projected*.
Evidence and exact commands: [`docs/evaluation/tts-quant/README.md`](evaluation/tts-quant/README.md).

---

## 1. Verdict on the proposal

| Claim | Verdict | Evidence |
| --- | --- | --- |
| Use Piper-Low INT8 for all languages | ❌ | Piper has only hi, ml and en voices (`IMPLEMENTATION_SPEC.md` "Facts you may rely on"). Seven of our ten languages have no Piper voice. Training them means datasets plus GPU-weeks. |
| ~15–18 MB per voice from "Low" | ⚠️ | The size comes from INT8, not from "Low". `en_US-lessac-low` is 67.1 MB, the same as `medium`. |
| INT8 unlocks NNAPI/NPU, RTF < 0.4 | ❌ | Our INT8 is `quantize_dynamic`, which emits `ConvInteger`. That op is not an NPU path, and on CPU it is **~3× slower** (§2.2). NNAPI is deprecated from Android 15. |
| Piper avoids MMS's transliteration step | ❌ | Our MMS export rejects uroman models (`mms_work/vits-mms.py:62-64`); MMS reads native script directly. Piper is the one with a front-end step (espeak-ng). |
| DSP can compensate INT8 loss | ❌ as stated | INT8 changes the long-term spectrum by **≤ 0.3 dB in every band** (§2.3). There is no tonal loss for an EQ to undo. The real INT8 effect is timing (2–7 % duration drift), and no filter restores that. |
| DRC + pre-emphasis "audio output fix" improves clarity | ❌ not shown | Tested in 9 languages, clean and through a simulated loud phone speaker: CER change −0.22 to +0.48 points (mean ≈ +0.1), with clipping essentially unchanged (§2.3c). |
| 16 kHz voices sound duller | ✅ | True for the five MMS voices (16 kHz). Piper hi/ml is 22.05 kHz and played natively since T13. |

**What survives:** quantize the voices, but with a different method: weight-only INT8, with the
2.2 MB duration predictor kept in FP32 (§2.2). Across **all ten voices** it measured **928 → 229 MB
of downloads, the same speed, and no CER loss beyond +0.62 points** in the nine languages that
could be scored (§2.3b). The DSP idea survives only as an optional loudspeaker
chain (T83), not as quantization compensation.

---

## 2. Measurements

Desktop is this dev machine's x86 CPU. Desktop RTF is useful for comparing variants against each
other, not as a phone figure. Phone numbers come from the committed telemetry.

### 2.1 What ships today (from `ModelRegistry.kt`)

| Voices | Archive size |
| --- | --- |
| Hindi, Malayalam, English (Piper, FP32) | 67.2 + 67.2 + 67.1 MB |
| Gujarati (Mimic3), Bengali (Coqui) | 80.0 + 108.1 MB |
| Marathi, Kannada, Tamil, Telugu, Odia (MMS, FP32, self-converted T17b) | 5 × 107.7 = 538.8 MB |
| **All ten TTS voices** | **928.4 MB** (+ 7.3 MB espeak-ng-data) |

Phone TTS latency has been measured **for Hindi (Piper FP32) only**:

| Evidence | n | synth ms (median) | audio ms | text → first audio ms | synth RTF |
| --- | --- | --- | --- | --- | --- |
| `latency-evidence/run2` receiver | 11 | 186 | 870 | 236 | 0.20 |
| `latency-evidence/run2` sender | 7 | 281 | 1021 | 421 | 0.35 |
| `latency-evidence/run3` receiver | 21 | 235 | 952 | 281 | 0.22 |
| `latency-evidence/t11` POCO | 14 | 387 | 1288 | 500 | 0.28 |
| `latency-evidence/t11` realme | 3 | 473 | 1277 | 570 | 0.31 |

**No MMS or Coqui voice has ever been timed on a phone.** On desktop at 2 threads (§2.3b), the
five MMS voices run at RTF 0.48–0.52 and Coqui Bengali at 0.59. Piper hi/ml/en and Mimic3 gu run at
0.07–0.10, so **bn and the MMS voices are 5–7× slower**. Applying that ratio to Hindi's measured phone
RTF of 0.20–0.35 *projects* RTF ≈ 1–2.4 on the phone for mr/kn/ta/te/or/bn. That is slower than
real time, and a 3 s sentence would take several seconds before it finished synthesizing. **This is
the largest open TTS latency risk, and it is independent of quantization.** G13 must time one MMS
language and Bengali (§5). If confirmed, the options are: synthesize and play sentence by sentence
(T40 already splits segments), and raise `numThreads` from 2 for these voices only.

### 2.2 Quantization variants — size and speed

**MMS Marathi** (`vits-mms-mar`, 114.0 MB FP32). 10 sentences from `sravaani_mr_in.tsv`, 1 thread.
Graph weights: decoder 57.3 MB, flow 28.4 MB, text encoder 25.2 MB, duration predictor 2.2 MB.
There are no MatMul ops; all weights are Conv/ConvTranspose.

| Variant | model.onnx | Desktop RTF | vs FP32 |
| --- | --- | --- | --- |
| FP32 (today) | 114.0 MB | 0.71–0.74 | — |
| `quantize_dynamic`, all layers (what sherpa's `-int8` voices are) | 38.0 MB | 2.15 | **3.0× slower** |
| … duration predictor kept FP32 | 39.6 MB | 2.16 | 3.0× slower |
| … + decoder in/out convs FP32 | 41.6 MB | 2.13 | 3.0× slower |
| … whole decoder FP32 | 74.4 MB | 0.68 | same |
| FP16 (naive `onnxconverter_common`) | 57.8 MB | — | does not load (Cast type error) |
| Weight-only INT8 (T79) | 29.7 MB (26.0 MB `.tar.bz2`) | 0.65 | same |
| **Weight-only INT8, `/dp` FP32 (T79, recommended)** | **31.3 MB** | **0.657** (FP32 0.658, same idle re-time) | **same** |

**Piper Hindi** (`hi_IN-pratham-medium`), 10 sentences from `sravaani_hi_in.tsv`:

| Variant | model.onnx | RTF 1 thread | RTF 2 threads (app uses 2) |
| --- | --- | --- | --- |
| FP32 (today) | 63.1 MB | 0.18 | 0.12–0.14 |
| sherpa `-int8` release (**what T17a switches to**) | 18.6 MB | 0.49 | **0.46 (3.3–3.8× slower)** |
| **Weight-only INT8 (T79)** | **16.7 MB** | — | **0.13** |

Weight-only INT8 stores the weights as int8 behind `DequantizeLinear` and keeps activations FP32.
There is no `ConvInteger`, so FP32 kernels run. **Its cost is RAM:** once loaded, the weights are float
again, so resident memory stays FP32-sized. sherpa's dynamic INT8 does save RAM. That matters,
because peak TOTAL PSS is already 1364 MB with SraVaani on CPH2467 (`WORK_SPLIT.md` T78 C3),
against a 700 MB team target. T81 measures it.

### 2.3 Quantization variants — fidelity

- **Spectrum:** mean long-term spectrum difference vs FP32, over 8 bands from 100 Hz to 8 kHz. It is
  within **±0.3 dB** for every variant, weight-only included. INT8 does not dull or tilt these voices.
- **Timing:** with `noise_scale = noise_scale_w = 0` (deterministic), every INT8 variant still shifts
  total duration by 2–7 %. That includes the ones with the duration predictor kept FP32, because it is
  fed by the quantized text encoder. As a result, **sample-aligned ESTOI against FP32 is unusable**
  (0.1–0.36). It measures misalignment, not quality. This retires the "ESTOI ≥ 0.95 vs FP32" gate
  proposed earlier.
- **Intelligibility (ASR round trip, the gate that is used instead):** text → voice → IndicConformer
  Marathi → CER against the text, 20 digit-free sentences, app-default noise:

  | Variant | model.onnx | CER, noise 0 (1 run) | CER, app noise (3 runs) | mean |
  | --- | --- | --- | --- | --- |
  | FP32 (today) | 114.0 MB | 4.56 % | 5.43 / 4.77 / 6.53 | **5.58 %** |
  | dynamic INT8, all | 38.0 MB | — | 6.09 (1 run) | — |
  | dynamic INT8, `/dp` FP32 | 39.6 MB | 4.39 % | 4.94 (1 run) | — |
  | dynamic INT8, `/dp` + `/dec` FP32 | 74.4 MB | 4.72 % | 5.49 / 5.49 / 6.53 | 5.84 % |
  | weight-only INT8 | 29.7 MB | 5.05 % | 5.82 / 5.27 / 5.87 | 5.65 % |
  | **weight-only INT8, `/dp` FP32 (recommended)** | **31.3 MB** | **4.39 %** | 4.77 / 5.60 / 5.16 | **5.18 %** |

  FP32's own spread across three runs is **4.77–6.53 %**, because VITS noise is random per run.
  Every variant lands inside that spread. **The recommended variant shows no measurable
  intelligibility loss:** 4.39 vs 4.56 % deterministic, and 5.18 vs 5.58 % mean at app noise. Its
  speed is also identical, re-timed on an idle CPU: RTF 0.657 vs 0.658 at 1 thread and 0.504 vs
  0.505 at 2. This comes from 19 sentences in one language, so T81/T82 must confirm it on more
  languages and on the phone.

### 2.3b All ten voices (weight-only INT8, duration predictor FP32 where it can be named)

`model-export/tts_quant_all_langs.py`, raw rows in `docs/evaluation/tts-quant/results_all_langs.jsonl`.
Setup: RTF over 10 sentences at 2 threads (the app's setting), timed one language at a time on an
idle CPU. CER over 18–20 digit-free sentences, scored by IndicConformer for that language.

| Lang | Voice | model FP32 → WO | WO archive | RTF FP32 → WO | CER noise 0: FP32 → WO (Δ) | CER app noise, mean of 2: FP32 → WO |
| --- | --- | --- | --- | --- | --- | --- |
| hi | Piper | 63.1 → 18.3 MB | 16.3 MB | 0.086 → 0.091 | 2.22 → 1.89 (−0.33) | 2.52 → 2.45 |
| ml | Piper ¹ | 62.9 → 16.5 MB | 14.9 MB | 0.088 → 0.097 | 3.41 → 3.12 (−0.29) | 3.53 → 3.24 |
| en | Piper | 63.1 → 18.3 MB | 16.4 MB | 0.074 → 0.078 | 1.55 → 2.06 (+0.51) | 1.96 → 1.91 |
| gu | Mimic3 ¹ | 76.3 → 19.8 MB | 18.0 MB | 0.098 → 0.096 | 8.78 → 9.40 (+0.62) | 12.67 → 11.06 |
| bn | Coqui ¹ ² | 114.3 → 30.0 MB | 25.6 MB | 0.594 → 0.615 | 8.84 → 8.89 (+0.05) | 8.68 → 8.49 |
| mr | MMS | 114.0 → 31.3 MB | 27.5 MB | 0.478 → 0.478 | 4.61 → 4.45 (−0.16) | 5.76 → 5.62 |
| kn | MMS | 114.0 → 31.3 MB | 27.5 MB | 0.478 → 0.483 | 2.54 → 2.43 (−0.11) | 3.29 → 3.29 |
| ta | MMS | 114.0 → 31.3 MB | 27.5 MB | 0.499 → 0.499 | 4.72 → 4.46 (−0.26) | 5.29 → 5.52 |
| te | MMS | 114.0 → 31.3 MB | 27.4 MB | 0.516 → 0.505 | 9.29 → 9.17 (−0.12) | 8.73 → 10.03 ³ |
| or | MMS | 114.0 → 31.3 MB | 27.5 MB | 0.524 → 0.511 | — no Odia IndicConformer | — |
| **All ten** | | **938 → 240 MB** | **228.6 MB** (today 928.4) | **within ±10 % everywhere** | **Δ −0.33 … +0.62** | |

¹ Their duration predictor could not be kept FP32, so it was quantized too. The ml (older Piper)
and gu (Mimic3) exports have anonymous node names (`Conv_32`…). For bn, the prefix
`/duration_predictor/` was only added to the script after this run. None of the three shows a
loss, so no re-run is needed.
² The Coqui voice is not fully deterministic at noise 0: two identical FP32 runs gave 8.84 and
9.74 % (the second is in the DSP table below).
³ Telugu's two WO app-noise runs were 9.11 and 10.95. The deterministic delta is −0.12, so this
is sampling spread. T81 should still re-check Telugu with more sentences.

**Verdict:** weight-only INT8 passes the T80 gate (noise-0 CER ≤ FP32 + 1.0) in all nine
languages that have an ASR model, at unchanged speed, for **−75 % download size**. Odia is
size- and speed-checked only. It is the same MMS architecture as the four that passed, but T81
must confirm it by ear or with SraVaani.

### 2.3c The proposed "audio output fix" (DSP), tested in all nine languages

`dsp_chain()` in `tts_quant_all_langs.py` is the proposal as it would run before `AudioTrack.write`:
DC blocker → 50 % pre-emphasis (α 0.7) → compressor/DRC (−24 dBFS threshold, 3:1, 5/60 ms) →
peak limiter at −1 dBFS → 5 ms fades. `cheap_speaker()` **simulates** a small phone speaker at
high volume: level-match, +14 dB, hard clip, 400 Hz high-pass. It is a simulation, not a real
speaker measurement. FP32 voices, noise 0, CER %:

| Lang | clean | clean + DSP | speaker sim | speaker sim + DSP | clipped samples: raw → DSP |
| --- | --- | --- | --- | --- | --- |
| hi | 2.22 | 2.05 (−0.17) | 2.22 | 2.22 (±0) | 6.94 → 6.87 % |
| ml | 3.41 | 3.35 (−0.06) | 3.18 | 3.29 (+0.11) | 6.63 → 6.42 % |
| en | 1.55 | 1.86 (+0.31) | 2.63 | 2.63 (±0) | 7.19 → 6.69 % |
| gu | 8.78 | 8.11 (−0.67) | 8.95 | 8.78 (−0.17) | 5.99 → 6.44 % |
| bn | 9.74 | 8.89 (−0.85) | 8.25 | 8.73 (+0.48) | 5.5 → 6.3 % |
| mr | 4.61 | 4.61 (±0) | 4.72 | 4.50 (−0.22) | 7.57 → 7.05 % |
| kn | 2.54 | 2.54 (±0) | 2.43 | 2.71 (+0.28) | 7.46 → 6.85 % |
| ta | 4.72 | 5.13 (+0.41) | 4.77 | 4.97 (+0.20) | 7.24 → 6.89 % |
| te | 9.29 | 9.88 (+0.59) | 9.70 | 9.94 (+0.24) | 7.53 → 6.92 % |

**Verdict:** no measurable benefit. Through the simulated speaker, DSP moves CER by −0.22 to +0.48
points (mean ≈ +0.1). It barely changes clipping: pre-emphasis raises the very peaks that the
compressor lowers. The simulated speaker itself barely hurts CER either, so ASR is robust to this
kind of damage, or the simulation is too mild. Either way, **nothing here justifies shipping the
chain.** T83 stays optional and is gated on a real phone-speaker loopback test (§4).

### 2.4 Two text-coverage bugs found on the way (not quantization)

From each MMS voice's `tokens.txt`:

| Voice | Native digits | ASCII digits | Latin letters | `.` / `।` |
| --- | --- | --- | --- | --- |
| mar | none | 0 1 2 4 6 7 9 (no 3 5 8) | none | none |
| kan | none | all ten | none | none |
| tam | none | 9 of ten (no 8) | 2 | none |
| tel | none | only `6` | none | none |
| ory | none | 9 of ten (no 8) | none | none |

sherpa-onnx logs `Skip unknown character` and drops these characters. So in the five MMS languages,
**any number written in native digits, and any English word, is silently not spoken.** This was seen
live: Devanagari digits U+0967, U+096F, U+096D and U+096C were skipped on real Marathi sentences.
The T42 terminators are dropped the same way, which is harmless. The fix is **T35** (text
normalization before TTS): expand numbers to words in the message's language, and transliterate or
spell out Latin text. Piper voices go through espeak-ng, which reads digits, so they are unaffected.

---

## 3. Act now, while G12 is in flight: hold T17a

T17a (`feature/t17a-int8-piper`) switches Hindi, Malayalam and English to sherpa's `-int8` voices.
It saves 138.6 MB, but those voices measured **3.3–3.8× slower** (§2.2). On the phones, Hindi FP32 is
RTF 0.20–0.35 and text → first audio is 236–570 ms. Scaled by the measured slowdown, int8 would push
TTS well past the < 500 ms target (PRD §4.3). That scaling is a *projection*, not a phone measurement.

**Decision:** do not merge T17a on size alone.

1. Install the T17a branch on CPH2467 and send 10 Hindi messages. Compare `tts_synth_ms` and `tts_ms`
   in `telemetry.csv` against main.
2. If the phone confirms the slowdown (≥ 2×), **close T17a** and let T80 deliver the size win with
   weight-only voices instead. If the phone shows no slowdown, merge T17a and note it here.
3. Either way, take the G13 baseline on `main` **without** T17a, so the "before" column is FP32.

---

## 4. The plan

| ID | Task | Owner | Effort | Depends on | Criterion |
| --- | --- | --- | --- | --- | --- |
| **T79** | Weight-only INT8 exporter + evaluation scripts (**done in this PR**) | G | — | — | EFF |
| **T80** | Weight-only INT8 for all ten voices: export, package, host, register | G | 1–1.5 d | T79, G13 | EFF |
| **T81** | On-device check of T80: RTF, TOTAL PSS, round-trip CER, listening spot check | G + S | ½ d | T80 | EFF/LAT/ACC |
| **T35** ↑ | Text normalization before TTS: numbers → words, Latin handling (**raise priority**, §2.4) | S | 1 d | — | ACC |
| **T82** | TTS round-trip CER for all ten languages, as the committed intelligibility number | G | ½ d | T79 | ACC/DOC |
| **T83** | *Optional* playback chain: DC blocker, fades, limiter (+ mild presence boost on 16 kHz voices) | G | ½ d | T81 | ACC |
| T54 | Human listening test (MOS) — unchanged, still the only MOS claim | S | 2 d | T80 ideally | ACC |

### T80 · Weight-only INT8 for all ten voices

1. **Sources:**
   - MMS ×5: `model-export/mms_work/pkg/vits-mms-*/model.onnx` (already local).
   - Piper hi/ml/en, `gu` Mimic3 and `bn` Coqui: download the FP32 `.tar.bz2` that `ModelRegistry.kt`
     already points at. Do **not** start from the `-int8` ones.
2. `MSYS_NO_PATHCONV=1 python model-export/weight_only_int8.py <voice>/<model>.onnx out/<bundle>/ 4096 /dp/`
   for each voice. `/dp/` keeps the duration predictor FP32; both the MMS and the Piper graphs name
   it `/dp/...`. Git Bash rewrites `/dp/` into a Windows path without `MSYS_NO_PATHCONV=1`. Copy
   `tokens.txt` (the script does this) and the voice's other non-espeak files (`*.onnx.json`,
   `MODEL_CARD`, lexicons if any). Do **not** include `espeak-ng-data/`: the app ships it once as
   `ESPEAK_NG_DATA`, and `ArchiveExtractor` already drops it from Piper bundles.
3. Package as `.tar.bz2` with one top-level folder, as T17b did (`ArchiveExtractor` strips it, and
   `TTSModule` finds the model by the `.onnx` extension, so no Kotlin change is needed).
4. Desktop gate per voice, before hosting:
   - it loads in sherpa-onnx;
   - desktop RTF is ≤ 1.1× FP32;
   - round-trip CER **at `NOISE=0`** is ≤ FP32 CER + 1.0 point (every language with an
     IndicConformer model). Use noise 0 because at app noise FP32 alone spreads 1.8 points across
     runs (§2.3), which would swamp a 1-point gate.
5. Host next to the T17b voices (`ITANTRA_MODELS_BASE`). Update each `ModelRegistry` entry's file
   name, `sizeBytes` and `sha256` with real values (`ls -l`, `sha256sum`). Keep the MMS licence
   comment (CC-BY-NC 4.0 still applies to derivatives).

**Size, measured (archives built by `tts_quant_all_langs.py convert`, §2.3b):**

| Voices | Today | After T80 |
| --- | --- | --- |
| 5 MMS voices | 538.8 MB | 137.4 MB |
| 3 Piper voices (hi, ml, en) | 201.5 MB | 47.6 MB |
| gu (Mimic3) + bn (Coqui) | 188.1 MB | 43.6 MB |
| **All ten** | **928.4 MB** | **228.6 MB (−75 %)** |

The archives built by the script are exactly what T80 would host. Before hosting, rebuild bn with
the current script so that its duration predictor is kept FP32.

For comparison, T17a alone reaches 789.8 MB, at a measured 3× TTS slowdown.

### T81 · Prove it on the phone (CPH2467, the G13 device)

Use the same procedure as G13 Step 4, for `hi` (Piper) and `mr` (MMS), FP32 vs weight-only:

- `tts_synth_ms` / `tts_audio_ms` → RTF;
- `tts_ms` (text → first audio);
- peak `dumpsys meminfo` TOTAL PSS, with both TTS voices of the LRU loaded;
- a 10-sentence listening spot check by one native speaker.

**Adopt** if RTF is within 10 % of FP32 and PSS is within +30 MB. **If PSS rises materially**
(onnxruntime keeps both the int8 and the float copy), try dynamic INT8 on the text encoder and
flow only, with the decoder FP32. That measured 74.4 MB and was not slower (§2.2). Report whichever
wins, with both numbers.

### T82 · A committed TTS intelligibility number

Run `tts_roundtrip_cer.py` for every language with an ASR model: IndicConformer for 9 languages,
SraVaani for Odia. Use FP32 and the shipped voices, 20 digit-free sentences each. Commit
`docs/evaluation/tts-quant/roundtrip_cer.csv`, with the ASR's own CER on the matching human-speech
T76 hypotheses beside it as the baseline.

This replaces the PRD's **"STOI > 0.85"**, which cannot be computed for TTS: there is no aligned human
reference of a synthesized sentence. Change the PRD row to "TTS round-trip CER (ASR) ≤ ASR CER on
human speech + N". Keep MOS for T54.

### T83 · Optional loudspeaker chain (only if T81/T54 say it's needed)

**Status after §2.3c: deprioritised.** The chain below was already tested in simulation across nine
languages and showed no CER gain. Do it only if a real phone speaker test (T81 or T54 listeners)
reports distortion at high volume. Then measure with an actual loopback before merging.

In `AudioPlaybackManager`, before `AudioTrack.write`:

1. DC blocker (`y = x − x₋₁ + 0.995·y₋₁`);
2. 5 ms fade in/out;
3. peak limiter at −1 dBFS;
4. for 16 kHz voices only, a mild presence boost (pre-emphasis α ≈ 0.9 or +3 dB at 2–4 kHz),
   placed *before* the limiter.

**No compensation EQ.** §2.3 shows there is nothing to compensate. Gate: loopback test, meaning phone
speaker → a second phone's mic at 1 m → IndicConformer. CER must improve, not just "sound better".

---

## 5. Order of work

1. **Now (inside G12):** §3, the phone check of T17a before merging it.
2. **G13 (baseline), with two additions:**
   - time one MMS language (`mr`) and Bengali alongside Hindi. Neither has ever been timed on a
     phone, and the desktop projects them past real time (§2.1);
   - record TOTAL PSS with a TTS voice loaded.
3. **T80 → T81** (with T82 run on desktop in parallel, since it needs no phone).
4. **T35** (Sarthak), in parallel with 3. It is independent of quantization and fixes dropped numbers
   today.
5. **T54** once T80 voices are in (so MOS is measured on what ships). **T83** only if T81/T54 show a
   loudspeaker problem.

## 6. Not doing, and why

| Idea | Reason |
| --- | --- |
| Piper-Low for every language | Voices don't exist for 7 of 10 languages. |
| NNAPI / NPU for TTS | Dynamic INT8 isn't an NPU format; VITS ops fall back to CPU; NNAPI is deprecated. |
| sherpa `-int8` / `quantize_dynamic` voices | Measured 3–3.8× slower on CPU (§2.2). |
| MatMul-only mixed precision | These VITS graphs contain no MatMul ops. |
| Naive FP16 | Conversion produced a graph sherpa-onnx cannot load; weight-only INT8 is smaller anyway. |
| DSP "quantization compensation" EQ | INT8 changes the spectrum by ≤ 0.3 dB (§2.3). |
| Shipping the DRC + pre-emphasis chain now | No CER gain in 9 languages, clean or through a simulated speaker (§2.3c). |
| ESTOI vs FP32 as a quantization gate | INT8 shifts durations 2–7 %, so aligned metrics measure misalignment. |

## 7. Risks

- **RAM.** Weight-only INT8 does not shrink resident memory, and PSS is already over target with
  SraVaani. T81 decides this, and the fallback is written down there.
- **Android onnxruntime.** Per-axis `DequantizeLinear` is standard at opset 13, and the MMS graph is
  opset 13. It still has to be checked in sherpa-onnx's Android build (T81 step 1: it loads).
- **Desktop ≠ phone.** Every desktop ratio needs confirming on CPH2467 before a number goes on a
  slide.
- **MMS and Coqui latency on the phone** (§2.1). It is projected past real time, and weight-only
  INT8 neither causes nor fixes it. This is the first thing G13 should settle.
- **Odia** is not CER-scored (no IndicConformer). Check it by ear or with SraVaani in T81.
