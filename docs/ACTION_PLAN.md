# iTantra — Action Plan to Compete for a Top-5 Placement

> Smart India Hackathon 2026 · Problem Statement PS-26173
> **Status 2026-09-24 (night):** latency work (T41, T38, T65, T62, T70, T45, T72, T71) is on `main`. Stage A (T43, T73, T46, T47) is on `feature/stage-a`, awaiting its run-3 evidence. The AI Assistant is removed in PR #19 (debug APK −38.8 %). Next: **Stage B** — phone mode + echo gate, two-way Bluetooth, an alert toggle (SOS trimmed to the PS minimum), voice notes — then **Stage C**. See [`TASKS.md`](TASKS.md) "Do these next" and `IMPROVEMENT_PLAN.md` §10.16 for the scope decisions.
>
> As of 2026-09-22 · **Revised 2026-09-23** after a second audit — see [`IMPROVEMENT_PLAN.md` §10](IMPROVEMENT_PLAN.md#10-addendum--second-audit-2026-09-23)
>
> **Assumption:** ~6 working weeks and two people (Gaurav on engine, Sarthak on shell). If the real window is shorter, cut from the bottom of §7 — the order is already by value.

### The document set

| Document | What it is | When to open it |
| --- | --- | --- |
| [`IMPROVEMENT_PLAN.md`](IMPROVEMENT_PLAN.md) | Technical audit — what is wrong and why | Understanding a finding |
| **This file** | Strategy, scorecard, six-week plan | Planning and prioritising |
| [`TASKS.md`](TASKS.md) | 70 tickable tasks, T01–T69 | Daily execution and tracking |
| [`IMPLEMENTATION_SPEC.md`](IMPLEMENTATION_SPEC.md) | Code-level detail, part 1 | Hand to whoever writes the code |
| [`IMPLEMENTATION_SPEC_2.md`](IMPLEMENTATION_SPEC_2.md) | Code-level detail, part 2 | Same |

Task IDs (T01–T69) are the shared key across all five. **Never hand `TASKS.md` to a coding agent on its own** — the one-line summaries are for humans; the specs carry the exact anchors that stop a model inventing code.

---

## 1. The honest verdict

**You are further ahead on capability than most teams and further behind on evidence than the rubric requires.**

A working two-device pipeline with real neural STT and TTS, on real hardware, is genuinely rare. Most of 500 teams will demo a half-wired prototype or a cloud API with the network cable hidden. You are not in that group.

But the rubric is **80% quantitative** (Efficiency 20 + Accuracy 40 + Latency 20), and right now:

- You have **never measured a single scored number** — no WER, no RTF, no end-to-end latency, no idle CPU, no PSS.
- Your RTF was **above 1.0** because of one unoptimised function (`IMPROVEMENT_PLAN.md` §2). The fix, plus telemetry, is now **implemented in the uncommitted working tree** of `docs/comprehensive-documentation` — but not committed and not measured on a device (`IMPROVEMENT_PLAN.md` §10.8).
- You ship **5 of the 10 required TTS languages**. Half the Accuracy criterion is unavailable for half the mandated languages.
- Your compulsory model bundle is **2.18 GB** against a criterion that explicitly scores model and flash footprint.
- **Four explicit PS requirements are unmet or undemonstrable**: phone mode (and, once wired, it echoes — `IMPROVEMENT_PLAN.md` §10.2), non-overlapping playback, sending an alert (no UI calls `broadcastAlert()` — §10.4), and voice notes (§10.5). Bluetooth also sends only from the phone that hosts (§10.11).

Judges scoring 500 submissions on a weighted rubric do not reward architecture. They reward a table of numbers taken on a cheap phone. **That table is the deliverable, and you do not have it.**

### The single biggest misallocation

`RANGE_STRATEGY.md` and `RANGE_IMPLEMENTATION.md` — the AFSK modem, PMR446 handhelds, DTN store-carry-forward, BLE Coded PHY, the regulatory research — represent a large share of recent effort and are worth **zero points**.

Read the PS again:

> *"must instantly and efficiently stream the data through wifi/Bluetooth connected embedded device or another phone with same application with minimal latency"*

Wi-Fi and Bluetooth. That is the entire transport requirement, and you already satisfy it. Nothing in the rubric mentions range, kilometres, mesh, relay, or radio. The work is good engineering and it is off-rubric.

**Stop it now and redirect that time.** Keep the docs as an appendix if asked about scale-up; do not spend another day on the branch.

---

## 2. Hard compliance gaps (pass/fail, not points)

These are requirements in the PS text. Failing one can disqualify you regardless of your scores.

| PS requirement | Status | Fix cost |
| --- | --- | --- |
| STT for 10 languages | **9/10** — the registry's mirror lacks Odia, but AI4Bharat publishes [`indicconformer_stt_or_hybrid_ctc_rnnt_large`](https://huggingface.co/ai4bharat/indicconformer_stt_or_hybrid_ctc_rnnt_large) (§10.3). **2026-09-25:** Odia now comes from T77 (SraVaani hybrid) if it passes on the phone, otherwise from T64 | ~1.5–2 days (T77), then T64 only if needed |
| TTS for 10 languages | ✅ **10/10** since T17b (PR #24, 2026-09-25). Was 5/10: Marathi, Kannada, Tamil, Telugu, Odia missing | Done. The truncated Malayalam file is in `KNOWN_ISSUES.md` |
| "if turned off it should work like a phone" | **Unreachable** — `PHONE_MODE` exists in the service, never called from the UI. **Once wired it self-oscillates**: no echo gate, so received TTS is re-transcribed and sent back. **2026-09-25:** echo gate merged (T63, PR #22); only the UI wiring remains | ~1 day (T37) |
| Alert messages "non-interruptible", highest volume | Receive side done (`USAGE_ALARM`, `FLAG_AUDIBILITY_ENFORCED`, max volume, `AUDIOFOCUS_GAIN`). **Send side unreachable** — nothing in `ui/` calls `broadcastAlert()`, so an alert cannot be demonstrated | ~1 day (T66) + 2 h (T39) |
| TTS "played as a voice note" | **Missing** — audio played once and discarded; no stored note, no replay | ~0.5 day (T67) |
| Voice notes must not overlap | **Broken** — no playback queue, concurrent messages garble | ~0.5 day |
| Wi-Fi/Bluetooth transport, both directions | Wi-Fi Direct fine. **Bluetooth sends only from the hosting phone** — the client phone's messages go to TCP, where it has no peer | 2 h (T69) |
| Translation between languages | **Not required** by the PS — do not build it (`IMPROVEMENT_PLAN.md` §10.12) | — |
| Open-source only, fully offline | Satisfied | — |
| Runs on low/mid-range phones | **Unverified** — never measured on one | see §5 |
| Sentence formation after pause detection | Partial — fixed 800 ms endpoint, no sentence segmentation; in PTT nothing is sent until release | ~1 day (T42) + 1 day (T65) |
| "wifi/Bluetooth connected embedded device **or** another phone" | Phone-to-phone done; embedded device never demonstrated | ~1 day, stretch (T68) |

**The TTS gap is the most serious thing in this document.** TTS legibility is half of the 40% Accuracy weight. Shipping 5 of 10 languages caps that half at roughly 50% before quality is even assessed.

---

## 3. Closing the TTS gap is a conversion job, not a URL change

> **Corrected 2026-09-21.** An earlier draft of this section claimed the five missing voices could be added by pasting URLs from the sherpa-onnx `tts-models` release. **That was wrong.** The release was queried directly: of its 645 assets, the only MMS voice is `vits-mms-eng.tar.bz2`. There is no `mar`, `kan`, `tam`, `tel` or `ory`. The comments already in `ModelRegistry.kt` lines 19–25 were correct.

What actually exists in that release for Indic languages:

| Language | Prebuilt voice available |
| --- | --- |
| Hindi | Yes — `vits-piper-hi_IN-*` (3 speakers) |
| Malayalam | Yes — `vits-piper-ml_IN-*` (2 speakers) |
| Gujarati | Yes — `vits-mimic3-gu_IN-cmu-indic_low` |
| Bengali | Yes — `vits-coqui-bn-custom_female` |
| English | Yes — many |
| **Marathi, Kannada, Tamil, Telugu, Odia** | **No. None.** |

### The real path

Upstream checkpoints exist as `facebook/mms-tts-{mar,kan,tam,tel,ory}`, and sherpa-onnx documents an official conversion script. So the work is:

1. Convert each of the five with sherpa-onnx's [MMS conversion procedure](https://k2-fsa.github.io/sherpa/onnx/tts/mms.html).
2. Package each as a `.tar.bz2` mirroring an existing voice's layout.
3. Host them (your own Hugging Face repo is fine) and record real sizes and hashes.
4. Point `ModelRegistry` at your host, not at `k2-fsa`.

**Effort: ~2 days, not 1.** Step-by-step detail is in [`IMPLEMENTATION_SPEC.md`](IMPLEMENTATION_SPEC.md) T17b.

### A verified size win you get for free

The release also publishes **int8 variants of every Piper voice**, which the registry is not using. Confirmed sizes:

| Voice | Current | int8 | Saving |
| --- | --- | --- | --- |
| Hindi pratham | 67.2 MB | 21.0 MB | 46.2 MB |
| Malayalam arjun | 67.2 MB | 20.8 MB | 46.4 MB |
| English lessac | 67.1 MB | 21.1 MB | 46.0 MB |

**138.6 MB saved by changing three strings.** Gujarati and Bengali have no int8 variant. See `IMPLEMENTATION_SPEC.md` T17a.

### Licensing

MMS-TTS is **CC-BY-NC 4.0 — non-commercial**. It is open-source and satisfies the "no proprietary SDK" restriction, but the NC clause is a real term: declare it in your compliance table rather than letting a judge find it. If you want a cleaner licence and better Indic prosody, [AI4Bharat Indic-TTS](https://github.com/AI4Bharat/Indic-TTS) (13 languages) or [IndicF5](https://github.com/AI4Bharat/IndicF5) (11 languages) are the alternatives — both also need an export step, so budget a week rather than two days.

**Recommendation: convert MMS now to reach 10/10, treat AI4Bharat as a stretch goal.** Ten adequate voices score better than five good ones — coverage is a gate, per-voice quality is a tiebreaker.

### Odia STT — corrected 2026-09-23

> An earlier version of this section said to declare 9/10 because no Odia model exists. **That was wrong.** Only the third-party mirror the registry uses (`parismitaglobalsolutions/indicconformer-sherpa-onnx`) lacks Odia. AI4Bharat publishes [`ai4bharat/indicconformer_stt_or_hybrid_ctc_rnnt_large`](https://huggingface.co/ai4bharat/indicconformer_stt_or_hybrid_ctc_rnnt_large), the same family as the other nine.

Export it to a CTC INT8 ONNX graph plus its own `tokens.txt`, host it beside the T17b voices, and register it (T64). Do it in the same job as T55: re-exporting **all ten** languages CTC-only from the AI4Bharat originals gives one consistent source and, at ~120 M parameters, should land near ~125 MB per language rather than the mirror's ~197 MB. The "do not substitute Assamese" rule still stands.

> **Revised 2026-09-25: T77 comes first.** T76 (PR #27) found that SraVaani 1.0 covers Odia (21.69% WER) and is +0.69 WER points better on the 8 non-English shared languages. All-languages-in-one is ~half the 10-language flash footprint (903 MB FP16 vs ~1.84 GB). English is much worse (22.16% vs 12.73%), so the candidate is a **hybrid**: SraVaani for the nine Indic languages, IndicConformer for English. The open question is whether a 430 M-parameter model runs smoothly on a low-range phone. **T77** measures INT8 SraVaani on the phones. If it passes, SraVaani supplies Odia and T64 (with its Step 6 re-export) is dropped. If not, do T64 as above.

---

## 4. What actually separates top-5 from top-50

In a rubric this quantitative, the ranking is decided by four things, in order:

1. **You have numbers and they are real.** A CSV of per-language WER, RTF, and end-to-end latency, measured on a named budget phone, beats any amount of architecture narrative. Most teams will present adjectives. Present a table.
2. **You measured on hardware that matches the PS.** "Low and mid-range mobile phones" is stated. Numbers from a flagship are close to worthless here — and numbers from a budget phone that are still good are the strongest possible signal.
3. **Nothing in the demo is fake.** Your codebase is unusually honest about this already (`AppResult.Error` instead of placeholder text, the VAD backend disclosure). Extend that: every number on every slide gets a provenance label.
4. **The scope is exactly the PS, and visibly so.** Teams lose points for sprawl. Your Phi-3 assistant and your range roadmap both read as "we built other things too," which invites the question of whether the core is finished.

### Do not add translation

The PS never asks for the text to change language between phones (`IMPROVEMENT_PLAN.md` §10.12). An offline MT model would cost hundreds of MB and extra latency against two scored criteria, for an unscored feature. The receiver speaks the text in the language it was spoken in (`srcLang`, corrected T43). If asked: *"out of scope for PS-26173; the text-first design lets an MT stage slot in later."*

### Cut the AI Assistant from the competition build

`LlmModule` pulls a **2.39 GB** Phi-3 model via llama.cpp. The PS does not ask for an LLM, an assistant, translation, or disaster protocols. It asks for STT, TTS, and a walkie-talkie.

That 2.39 GB sits directly against the criterion that scores flash footprint, and the llama.cpp native libraries inflate every APK. Move it behind a build flag, exclude it from the judged build, and mention it in one line as future work if asked. **This is a pure-win deletion.**

---

## 5. The scorecard you need to hit

Target numbers, all measured on a **sub-₹15,000 Android phone** (Snapdragon 6-series / Helio G85-class, 4 GB RAM). Pick one device, name it on every slide, and measure everything on it.

| Metric | Today | Target | Stretch |
| --- | --- | --- | --- |
| **Accuracy (40%)** | | | |
| STT languages shipped | 9/10 | **10/10** | 10/10 |
| TTS languages shipped | 5/10 | **10/10** | 10/10 |
| WER vs published checkpoint WER | unmeasured | **within 3 points absolute** | matches published |
| TTS mean opinion score (informal, 5 native speakers/language) | unmeasured | **≥ 3.5/5** | ≥ 4.0/5 |
| **Latency (20%)** | | | |
| RTF (feature + inference) | **> 1.0** | **< 0.5** | **< 0.3** |
| Speech end → STT complete | unmeasured | **< 1.2 s** | < 0.8 s |
| Text received → first audio sample | unmeasured | **< 0.8 s** | < 0.4 s |
| Sentence said → audio starts on phone B | unmeasured | **< 2.0 s** | < 1.2 s |
| **Efficiency (20%)** | | | |
| APK size | ~150 MB+ (est.) | **< 80 MB** | < 50 MB |
| Model bundle, one language pair | 2.18 GB (all) | **< 250 MB** | < 150 MB |
| Peak RAM (total PSS, active) | unmeasured | **< 700 MB** | < 500 MB |
| Idle-listening CPU (phone mode) | unmeasured | **< 3%** | < 1.5% |

### On the WER target specifically

Do not chase an absolute WER number — you do not control the model. Chase the **gap between your WER and the published IndicConformer WER on the same test set**. Any gap is your pipeline's fault, and closing it is exactly the preprocessing work in `IMPROVEMENT_PLAN.md` §3.1.

This reframing is also a strong thing to say to judges: *"we validated our on-device preprocessing against the reference implementation and land within N points of the published checkpoint."* That is an engineering claim almost no other team will be able to make.

---

## 6. What to stop, start, and keep

| Stop | Start | Keep |
| --- | --- | --- |
| AFSK modem, HDLC, `MeshLink` | **Committing the working-tree FFT + telemetry**, then the WER/RTF harness | Two-device Wi-Fi Direct + BT transport |
| Range/DTN/BLE Coded PHY work | Measuring on a budget phone | The honest-error discipline (`AppResult.Error`, no fabricated output) |
| Phi-3 / llama.cpp in the judged build | The remaining 5 TTS voices | Protobuf wire format — small and correct |
| Landing page polish | Phone mode + echo gate + playback queue | Model download integrity pipeline |
| New feature work outside the PS | SOS send UI + voice notes (both *are* the PS) | Room peer registry |
| Treating the energy VAD as final | Repairing Silero VAD (`IMPROVEMENT_PLAN.md` §10.1) | The energy VAD — as the fallback |

---

## 7. Six-week plan

Each week ends with a measurement run. No week is complete until the scorecard in §5 has been re-taken on the target phone.

### Week 1 — Make it fast and make it measurable

The point of week 1 is that **every subsequent week can be evaluated.**

| Task | Owner | Effort | Spec |
| --- | --- | --- | --- |
| **Commit the working tree first.** T05–T07, T08–T10, T12, T13, T14 and most of T25–T28 are already implemented but uncommitted (`IMPROVEMENT_PLAN.md` §10.8). Commit them task by task, build, install — the week-1 rows below become verify-and-measure, not write | Gaurav | 0.5d | TASKS "Status key" |
| **T05–T07** Radix-2 FFT-512 + precomputed sparse filterbank + pooled buffers | Gaurav | 1d | SPEC T05 |
| **T08–T10, T12** Telemetry object, send/receive stamping, RTF, CSV to `filesDir` | Gaurav | 1d | SPEC2 T08 |
| **T11** Peer clock offset from the ping loop, for the cross-device number | Gaurav | 2h | SPEC2 T11 |
| **T13** Delete `resampleTo16k`, play at native 22050 Hz | Gaurav | 1h | SPEC T13 |
| **T02, T14, T15** ABI split to arm64-v8a; drop duplicate ONNX Runtime; exclude llama.cpp | Sarthak | 6h | SPEC T14 |
| **T03, T16** Acquire the budget phone; take the first full baseline | Both | 4h | SPEC2 T56 |

**Exit criteria:** RTF measured and below 0.5. APK under 80 MB. A CSV with real rows in it.

> **Do T08–T12 before or alongside T05.** If you optimise first and instrument second, you have no "before" column and the 43.6× result becomes an assertion rather than a measurement.

### Week 2 — Close the language gap

| Task | Owner | Effort | Spec |
| --- | --- | --- | --- |
| **T17a** Swap the three Piper voices to their int8 variants — 138.6 MB saved | Gaurav | 2h | SPEC T17a |
| **T17b** Convert Marathi/Kannada/Tamil/Telugu/Odia from `facebook/mms-tts-*`, package, host, register — **these are not downloadable; you must convert them** | Gaurav | 2d | SPEC T17b |
| **T18** Register the five codes in `TTSModule.LANGUAGE_TO_PACK` | Gaurav | 1h | SPEC T17b |
| **T64 + T55** Export all ten STT languages CTC-only INT8 from the AI4Bharat checkpoints, **including Odia**; host; register. Replaces T19. **Gated by T77 (2026-09-25):** only if the SraVaani hybrid is not adopted | Gaurav | 3d | SPEC2 T64 🔬 |
| **T20** Rework `coreTransceiverPacks()` to a chosen language pair, not all nine | Gaurav | 4h | SPEC2 T20 |
| **T21** Downloads screen: per-language selection, sizes computed from `ModelRegistry` | Sarthak | 1d | SPEC2 T21 |
| **T22** Licence table in the README, including MMS CC-BY-NC | Sarthak | 3h | SPEC2 T22 |

**Exit criteria:** 10/10 TTS, 10/10 STT. Bundle for one pair under 250 MB.

> Week 2 now carries ~5 days of Gaurav's work. If it overruns, T64 moves to week 3 — but it stays on the critical path.

### Week 3 — Accuracy

This is the 40% week. Treat it as the most important one.

| Task | Owner | Effort | Spec |
| --- | --- | --- | --- |
| **T23** Extract `cfg.preprocessor` from the NeMo checkpoint — the config wins over any table in these docs | Gaurav | 4h | SPEC2 T23 🔬 |
| **T24–T28** Apply preemphasis, Slaney norm, n_fft 512, periodic Hann, log guard | Gaurav | 6h | SPEC T24 |
| **T29** Golden-reference test: NeMo features in Python vs Kotlin, assert to 1e-3 | Gaurav | 1d | SPEC2 T29 🔬 |
| **T30** WER harness over a public Indic test set, per language, CSV out | Gaurav | 1d | SPEC2 T30 🔬 |
| **T62** Repair Silero VAD: 64-sample context, pinned model version, re-enabled as primary | Gaurav | 1d | SPEC2 T62 🔬 |
| **T31, T32** VAD pre-roll ring buffer + adaptive noise floor — T32 is now the **fallback** behind T62 | Gaurav | 1d | SPEC T31 · SPEC2 T32 |
| **T33, T34** `VOICE_RECOGNITION` source, DC blocker, platform NS/AGC | Gaurav | 4h | SPEC2 T33 |
| **T35** Text normalization before TTS: numbers, abbreviations, Latin tokens | Sarthak | 1d | — |
| **T36** Re-measure WER after the above; record the delta | Gaurav | 3h | SPEC2 T30 |

**Exit criteria:** WER measured per language and within 3 points of published. Golden test green in CI.

> **T23 before T24–T28.** The parameter table in `IMPROVEMENT_PLAN.md` §3.1 lists NeMo *defaults*; this checkpoint may override them. Read the real config first and let it win.

### Week 4 — PS compliance and the remaining latency

| Task | Owner | Effort | Spec |
| --- | --- | --- | --- |
| **T37** Wire `setConnectionMode` to a UI toggle; PTT off = phone mode | Sarthak | 1d | SPEC T37 🎨 |
| **T63** Echo gate: mute capture while speaking + 250 ms tail. **Must land with T37**, not after | Gaurav | 3h | SPEC2 T63 |
| **T69** Send on every live transport + receive dedup — Bluetooth currently only sends from the host | Gaurav | 2h | SPEC2 T69 |
| **T66** SOS control: preset alert phrases + speak-as-alert; full-screen alert on receipt | Sarthak | 1d | SPEC2 T66 🎨 |
| **T67** Voice notes: store each received utterance as WAV, replay from the bubble | Gaurav + Sarthak | 0.5d | SPEC2 T67 🎨 |
| **T38, T39** Single-consumer playback queue; ALERT pre-empts and cannot be ducked | Gaurav | 7h | SPEC T38 |
| **T40** Streaming TTS on sentence/clause boundaries, danda-aware | Gaurav | 1d | SPEC2 T40 |
| **T41** Adaptive endpointing to replace the fixed 800 ms | Gaurav | 4h | SPEC2 T41 |
| **T42** Sentence formation — punctuation and terminators after CTC | Gaurav | 4h | SPEC2 T42 |
| **T65** Phrase-level pipelining: fix the existing mid-hold cut (inference blocks capture; concurrent inference) and shorten it to 400 ms — no WER cost | Gaurav | 1d | SPEC2 T65 |
| **T43, T44** Voice received text in its own language (`srcLang` — corrected, not `dstLang`); softmax the confidence score | Gaurav | 2h | SPEC T43 |
| **T45** Warm the configured language pair at service start | Gaurav | 3h | SPEC2 T45 |
| **T46, T47** LRU model cache; RAM metric switched to total PSS | Gaurav | 6h | SPEC2 T46 · SPEC T47 |

**Exit criteria:** every §2 compliance row green. End-to-end sentence→audio under 2 s.

### Week 5 — Quality and headroom

| Task | Owner | Effort | Spec |
| --- | --- | --- | --- |
| **T48, T49** CTC prefix beam search behind a flag; optional per-language KenLM | Gaurav | 3d | SPEC2 T48 🔬 |
| **T50** Streaming/chunked STT inference — **only if T65 leaves a latency gap, and only if WER degrades < 1 point** | Gaurav | 3d | SPEC2 T50 🔬 |
| **T68** *(stretch)* ESP32 receiver over Bluetooth SPP: text on a display, buzzer on ALERT | Gaurav | 1d | SPEC2 T68 |
| **T51–T53** Idle-listening power: buffer reuse, running totals, longer wakeups | Gaurav | 4h | SPEC2 T51 |
| **T54** Informal TTS listening test, 5 native speakers per language | Sarthak | 2d | — |
| ~~**T55**~~ Moved to week 2 and merged with T64 | — | — | — |

**Exit criteria:** stretch targets attempted. Nothing regressed.

> Week 5 is the only week where a task can be **abandoned on its measurement**. T50 in particular: IndicConformer is a non-streaming architecture, so if chunking costs more than a point of WER, keep batch inference. Losing 40%-weighted accuracy to win 20%-weighted latency is a bad trade.

### Week 6 — The dossier

No new features. Build the thing you are actually judged on.

| Task | Owner |
| --- | --- |
| Full scorecard run on the budget phone, all 10 languages, 3 repeats, medians reported | Both |
| Before/after table: week 0 vs week 6 on every metric | Gaurav |
| Two-device demo script, rehearsed, with a deliberate failure-and-recovery moment | Both |
| Slide deck built around the scorecard, not the architecture | Sarthak |
| README and all docs reconciled with reality — no claim without code behind it | Sarthak |
| Backup demo: screen recording, in case live hardware fails | Sarthak |

---

## 8. The evidence dossier

What you should be able to hand a judge:

1. **Scorecard CSV** — per language, per metric, median of 3 runs, device named.
2. **Before/after table** — the honest story of six weeks of optimisation. The 43.6× FFT result is a genuinely good slide.
3. **Golden-test output** — proof your preprocessing matches the reference implementation.
4. **Licence table** — every model, source URL, licence, including the MMS non-commercial flag.
5. **Two-device video** — sentence spoken in Tamil on phone A, heard on phone B, with a stopwatch visible.
6. **Known limitations page** — one slide, honest. This *raises* scores with technical judges. You already write this way; keep it.

---

## 9. Risk register

| Risk | Likelihood | Mitigation |
| --- | --- | --- |
| Preprocessing fix does not close the WER gap | Medium | Time-box to week 3. If the gap persists, switch to sherpa-onnx's own feature extractor rather than the hand-rolled one — it is already linked into the build |
| MMS voice quality poor for some languages | Medium | Listening test in week 5; fall back to AI4Bharat Indic-TTS for the worst one or two |
| An AI4Bharat checkpoint does not export cleanly to a CTC-only graph | Medium | The checkpoints may need AI4Bharat's NeMo fork — follow the model card. If CTC-only export fails, export the hybrid as the mirror did (bigger, but works). Worst case: state 9/10 with the attempt documented |
| Silero VAD still misbehaves after the context fix | Low–Medium | T32's adaptive energy detector is the fallback; ship whichever measures better on real recorded speech, and say which |
| Phone mode echoes on stage | High if T63 is skipped | T63 lands in the same PR as T37 — never demo phone mode without it |
| Budget phone thermally throttles during demo | Low–Medium | Measure a 10-minute sustained run in week 6; report throttled numbers, not just first-run |
| Six weeks is not available | — | Weeks 1–3 are the irreducible core. Weeks 4–5 are compliance and polish. Week 6 cannot be cut |

---

## 10. So — how much more?

**Roughly four to six focused weeks, and one strategic decision.**

The decision is to stop building new capability and start proving the capability you already have. Every remaining gap on the scorecard is a known, bounded engineering task — not one of them is research. There is no invention left in this project, only execution and measurement.

Concretely, the gap to a top-5 submission is:

- **One commit** (the FFT is already written, in the working tree) standing between you and a passing RTF — then one measurement to prove it.
- ~~**One conversion job** (five TTS voices, T17b)~~ (done, PR #24) and **one phone test** (T77: SraVaani INT8), then either the hybrid switch or **one export job** (Odia STT, T64), standing between you and full language coverage.
- **Five small PS items** — echo gate, Bluetooth both ways, SOS send, voice notes, phrase pipelining — standing between you and every requirement being demonstrable.
- **One config change** standing between you and a competitive app size.
- **One week of measurement work** standing between you and being able to prove any of it.
- **Two deletions** (range work, LLM) that cost nothing and sharpen everything.

That is a genuinely reachable position, and it is closer than the current state of the repository suggests. What it requires is discipline about scope, not more ambition.

The teams that beat you will not have built something you could not build. They will have measured what they built, on a cheap phone, and written the numbers down.

---

_iTantra · Smart India Hackathon 2026 · Problem Statement #26173_
