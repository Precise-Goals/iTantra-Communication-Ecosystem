# iTantra — scorecard against the PS-26173 rubric

**As of:** 2026-09-26, `origin/main` @ `dca41b0`. **Owner of the numbers:** Gaurav. **Update this file whenever a number changes.**

Every figure carries one label. Only **Measured** may go on a slide as a result; everything else must keep its label.

| Label | Meaning |
| --- | --- |
| **Measured** | Taken on the named device or desktop, with a committed source file. |
| **Calculated** | Arithmetic on measured parts (e.g. sums of pack sizes). The method is stated. |
| **Projected** | An expected value after a planned task, with its basis. **Not a result.** |
| **Target** | What we aim for: `ACTION_PLAN.md` §5 or PRD §4. |

Devices: **CPH2467** (OPPO, low-range, the judged phone) and **POCO X6** (`23122PCD1I`, Snapdragon 7s Gen 2).
**Desktop** means the dev PC, x86; its results are only comparable with each other.

---

## Efficiency — 20 %

> *Model size, App size (RAM/Flash footprint) and CPU usage during idle listening.*

| Metric | Now | Label / source | After the plan | Target |
| --- | --- | --- | --- | --- |
| APK size (debug) | **60.8 MB** (60,759,386 B) | Measured, T02 / PR #19 (was 99.3 MB) | unchanged | < 80 MB ✅ |
| Models on flash, all 10 languages | **1,398.6 MB** | Calculated from `ModelRegistry.kt` sizes: STT SraVaani 401.6 + STT English 197.6 + TTS 789.8 + espeak 7.3 + VAD 2.3 | **837.4 MB** with T80 (TTS 928.4 → 228.6 MB, measured per voice, `docs/evaluation/tts-quant/`) | as small as possible |
| One Indic language (STT + its voice + shared packs) | **432 MB** (with the Hindi int8 voice) | Calculated: SraVaani 401.6 + voice 21.0 + espeak 7.3 + VAD 2.3 | **427.5 MB** (voice 16.3 MB after T80) | < 250 MB (PRD, "base + 2 packs"): ❌ dominated by the 401.6 MB SraVaani pack |
| TTS voices, all 10 | 789.8 MB (FP32 928.4 before T17a) | Calculated from the registry | **228.6 MB** | — |
| Peak RAM (TOTAL PSS), STT active | **1,364 MB** CPH2467 · 1,235 MB POCO | Measured, `docs/evaluation/sravaani/phone/tdt/README.md`. ⚠️ that folder's `cph2467_sravaani_meminfo.txt` shows **1,598 MB**; reconcile in G13 | T80 adds ~0 (weight-only INT8 keeps FP32-size RAM; T81 checks this) | < 700 MB: ❌ (IndicConformer alone was 737 MB) |
| Idle-listening CPU (phone mode, 10 min) | **not measured** | — (G13 Step 5 measures it) | — | < 3 % |

**Say to judges (only once the numbers are measured):** "60.8 MB APK. All ten languages' voices
shrink 75 % (928 → 229 MB) at no measured speed or accuracy cost. One shared 402 MB STT model
covers nine Indic languages."
**Open gaps:** RAM (SraVaani roughly doubles PSS) and idle CPU (unmeasured).

---

## Accuracy — 40 %

> *Low Word Error Rate for STT and High human legibility and flow for TTS.*

### STT word error rate
Desktop, 100 read-speech clips per language (`docs/evaluation/sravaani/results*.csv`, T76/T78). The
app runs **SraVaani INT8 TDT for the nine Indic languages** and **IndicConformer for English**.

| Lang | App model | WER | CER |
| --- | --- | --- | --- |
| hi | SraVaani INT8 | **10.0 %** | 3.9 % |
| bn | SraVaani INT8 | 16.5 % | 5.0 % |
| kn | SraVaani INT8 | 19.2 % | 4.5 % |
| gu | SraVaani INT8 | 19.9 % | 6.3 % |
| ml | SraVaani INT8 | 19.9 % | 5.4 % |
| mr | SraVaani INT8 | 20.1 % | 6.2 % |
| te | SraVaani INT8 | 22.2 % | 6.4 % |
| or | SraVaani INT8 | 22.3 % | 6.3 % |
| ta | SraVaani INT8 | 32.9 % | 16.9 % |
| en | IndicConformer | 12.7 % | 7.2 % |
| **Mean, 10 languages** | | **19.6 %** (Calculated; 9 Indic = 20.3 %) | |

**Targets:** PRD < 18 % mean ❌ (1.6 points off), and < 10 % for hi/bn/ta: hi 10.0 % (borderline), ❌ bn, ❌ ta.
**Honest framing:** these are desktop numbers on read speech. G13/T56 must repeat a subset on the phone.

### TTS legibility and flow

| Metric | Now | Label / source | Target |
| --- | --- | --- | --- |
| Languages with a voice | **10 / 10** | Measured (registry + T17b) | 10/10 ✅ |
| Round-trip CER (text → TTS → ASR), FP32 voices | hi 2.2 · en 1.6 · kn 2.5 · ml 3.4 · mr 4.6 · ta 4.7 · gu 8.8 · bn 8.8 · te 9.3 % (or: no ASR) | Measured, desktop, `docs/evaluation/tts-quant/results_all_langs.jsonl` | lower is better; ASR on human speech is the reference (T82) |
| Same, after T80 (weight-only INT8) | worst change **+0.62 points** | Measured, desktop | ≤ +1.0 ✅ |
| Human MOS (intelligibility / naturalness) | **not measured** | — (T54) | ≥ 3.5 / 5 (team), > 3.8 (PRD) |
| Numbers and English words in mr/kn/ta/te/or | **silently not spoken** | Measured (MMS `tokens.txt`) | fix: T35 |

**Say to judges:** "Every language has a voice. Its intelligibility is measured by ASR round trip
(most languages 2–5 % CER), and shrinking the voices cost nothing measurable."
**Open gaps:** MOS (T54), the numbers bug (T35), Tamil STT.

---

## Latency — 20 %

> *Words said → STT complete; text received → audio played (TTS) with RTF; sentence said → same sentence starts as audio on the other phone.*

| Metric | Now | Label / source | After the plan | Target |
| --- | --- | --- | --- | --- |
| Endpoint wait after the last word | 400 ms (PTT held) / 500 ms | Fixed in code (`AudioCaptureModule`) | unchanged | — |
| **Speech end → STT complete** | **682.6 ms** CPH2467 · 410–421 ms POCO | Measured, SraVaani, `sravaani/phone/tdt/README.md` | unchanged | < 1.2 s ✅ |
| STT RTF | **0.260** CPH2467 · 0.244 POCO | Measured, same source | unchanged | < 0.5 ✅ |
| **Text received → first audio (TTS)**, Hindi, ~3 s sentences | **1,868 ms** on main (int8 voice) · 942 ms FP32 | Measured, POCO, `latency-evidence/t17a-check/` (PR #47) | **~942 ms** with T84/T80; **~450–550 ms** with T85–T87 streaming | < 0.8 s (team) / < 500 ms (PRD) |
| TTS RTF, Hindi | **0.591** int8 (main) · 0.297 FP32 | Measured, POCO, same | 0.297 with T80, then T88 threads | < 0.5 |
| TTS RTF, mr/kn/ta/te/or (MMS) and bn | **not measured on a phone** | Desktop: 5–7× slower than Piper | ≈ 1–2.4 on the phone (from the desktop ratio × Hindi phone RTF); streaming (T85–T87) hides most of it | < 1.0 (real time) |
| **Sentence said → audio starts on phone B** | **not validly measured** (T11 ran over Bluetooth, clock offset = 0) | T11 evidence README | **~1.7–2.2 s** now; **~1.2–1.7 s** with T84 + T85–T87 | < 2.0 s (team), < 2.5 s (PRD) |

**How the end-to-end projection is built:** endpoint wait 400–500 ms + STT 683 ms + network
(unmeasured; PRD budget 50 ms) + TTS first audio 942 ms ≈ **2.1–2.2 s** with FP32 voices. It's lower
if `stt_ms` already includes the endpoint wait. Streaming replaces the 942 ms with the first clause
(~450–550 ms). This is Calculated/Projected: **G13 must measure it over Wi-Fi Direct**.

**Say to judges (after G13 measures it):** "STT finishes 0.4–0.7 s after you stop speaking, at RTF
0.25. Audio starts on the other phone about N s after the sentence is said, measured with clocks
synchronised over the link."

---

## What moves the score, in order

| # | Task | Criterion | Moves | Effort |
| --- | --- | --- | --- | --- |
| 1 | **T84** revert T17a, or ship **T80** | Lat + Eff | TTS 1,868 → 942 ms; with T80 also TTS flash −75 % | 1 h / 1.5 d |
| 2 | **G13** baseline on CPH2467 (+ mr/bn TTS timing, idle CPU, E2E over Wi-Fi Direct) | all | turns every "not measured" into a number | ½ d |
| 3 | **T85–T87** streamed TTS | Lat | first audio ≈ halved (projected) | 1.5–2 d |
| 4 | **T35** numbers → words | Acc | stops dropped numbers in 5 languages | 1 d + native check |
| 5 | **T54** MOS listening test | Acc | the only human TTS number | 2 d (people) |
| 6 | **T88** TTS threads | Lat | projected 20–40 % less synthesis time | ½ d |
| 7 | RAM: SraVaani load strategy (not planned yet) | Eff | 1.36 GB → ? | design needed |
