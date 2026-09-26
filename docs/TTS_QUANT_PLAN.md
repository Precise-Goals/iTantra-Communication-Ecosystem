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
| 16 kHz voices sound duller | ✅ | True for the five MMS voices (16 kHz). Piper hi/ml is 22.05 kHz and played natively since T13. |

**What survives:** quantize the voices, but with a different method: weight-only INT8, with the
2.2 MB duration predictor kept in FP32 (§2.2). On Marathi it measured **114 → 31.3 MB, the same
speed, and no measurable CER loss** (§2.3). The DSP idea survives only as an optional loudspeaker
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

**No MMS voice has ever been timed on a phone.** On desktop, MMS FP32 runs at RTF 0.71 at 1 thread
against Piper Hindi's 0.18, so it is ~4× slower (§2.2). The five MMS languages are therefore the
likeliest to miss the < 500 ms TTS target *regardless of quantization*. G13 must time one of them (§5).

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

**Size, projected from measured per-voice results:**

| Voices | Today | After T80 |
| --- | --- | --- |
| 5 MMS voices | 538.8 MB | ~140 MB (31.3 MB `model.onnx` measured; 26.0 MB archive measured for the all-layers variant, so ~27–28 MB expected) |
| 3 Piper voices | 201.5 MB | ~55 MB (16.7 MB `model.onnx` measured for `hi` without `/dp` kept; +2.2 MB with it; archive not yet built) |
| gu + bn | 188.1 MB | ~50 MB (*projected* at the same ~4× ratio, not yet converted) |
| **All ten** | **928.4 MB** | **~245 MB** (−74 %) |

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
   - time one MMS language (`mr`) alongside Hindi, since no MMS voice has ever been timed on a phone;
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
| ESTOI vs FP32 as a quantization gate | INT8 shifts durations 2–7 %, so aligned metrics measure misalignment. |

## 7. Risks

- **RAM.** Weight-only INT8 does not shrink resident memory, and PSS is already over target with
  SraVaani. T81 decides this, and the fallback is written down there.
- **Android onnxruntime.** Per-axis `DequantizeLinear` is standard at opset 13, and the MMS graph is
  opset 13. It still has to be checked in sherpa-onnx's Android build (T81 step 1: it loads).
- **Desktop ≠ phone.** Every desktop ratio needs confirming on CPH2467 before a number goes on a
  slide.
- **Coqui/Mimic3 voices (gu, bn)** are not yet converted. Their graphs may differ. Budget a retry.
