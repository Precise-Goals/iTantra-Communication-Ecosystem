# iTantra — Action Plan to Compete for a Top-5 Placement

> Smart India Hackathon 2026 · Problem Statement PS-26173
> As of 2026-09-21 · Companion to [`IMPROVEMENT_PLAN.md`](IMPROVEMENT_PLAN.md), which is the technical audit
>
> **Assumption:** ~6 working weeks and two people (Gaurav on engine, Sarthak on shell). If the real window is shorter, cut from the bottom of §7 — the order is already by value.

---

## 1. The honest verdict

**You are further ahead on capability than most teams and further behind on evidence than the rubric requires.**

A working two-device pipeline with real neural STT and TTS, on real hardware, is genuinely rare. Most of 500 teams will demo a half-wired prototype or a cloud API with the network cable hidden. You are not in that group.

But the rubric is **80% quantitative** (Efficiency 20 + Accuracy 40 + Latency 20), and right now:

- You have **never measured a single scored number** — no WER, no RTF, no end-to-end latency, no idle CPU, no PSS.
- Your RTF is **above 1.0** because of one unoptimised function (`IMPROVEMENT_PLAN.md` §2).
- You ship **5 of the 10 required TTS languages**. Half the Accuracy criterion is unavailable for half the mandated languages.
- Your compulsory model bundle is **2.18 GB** against a criterion that explicitly scores model and flash footprint.
- **Two explicit PS requirements are unmet**: phone mode, and non-overlapping playback.

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
| STT for 10 languages | **9/10** — Odia missing | ~1 day |
| TTS for 10 languages | **5/10** — Marathi, Kannada, Tamil, Telugu, Odia missing | ~1–2 days (see §3) |
| "if turned off it should work like a phone" | **Unreachable** — `PHONE_MODE` exists in the service, never called from the UI | ~1 day |
| Alert messages "non-interruptible", highest volume | Mostly done (`USAGE_ALARM`, `FLAG_AUDIBILITY_ENFORCED`, max volume, `AUDIOFOCUS_GAIN`) | ~2 hours to harden |
| Voice notes must not overlap | **Broken** — no playback queue, concurrent messages garble | ~0.5 day |
| Open-source only, fully offline | Satisfied | — |
| Runs on low/mid-range phones | **Unverified** — never measured on one | see §5 |
| Sentence formation after pause detection | Partial — fixed 800 ms endpoint, no sentence segmentation | ~1 day |

**The TTS gap is the most serious thing in this document.** TTS legibility is half of the 40% Accuracy weight. Shipping 5 of 10 languages caps that half at roughly 50% before quality is even assessed.

---

## 3. Closing the TTS gap is a download-manifest change, not a research project

This is the best news in the plan. The sherpa-onnx `tts-models` release ships Meta **MMS-TTS VITS voices converted to ONNX** for exactly the five missing languages — Marathi (`mar`), Kannada (`kan`), Tamil (`tam`), Telugu (`tel`), Odia (`ory`) — in precisely the `model.onnx` + `tokens.txt` layout that `TTSModule.getOrLoadTts()` already consumes.

Sources: [sherpa-onnx TTS model index](https://k2-fsa.github.io/sherpa/onnx/tts/all/) · [sherpa-onnx MMS documentation](https://k2-fsa.github.io/sherpa/onnx/tts/mms.html) · [sherpa-onnx VITS pretrained models](https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/vits.html)

**What this means in practice:** `ModelRegistry` currently has five stub entries with `fileName = ""`, `downloadUrl = ""`, `sizeBytes = 0L`. Fill them in, add the language codes to `TTSModule.LANGUAGE_TO_PACK`, and you go from 5/10 to 10/10. No training, no export pipeline, no new inference code.

### One licensing caveat you must handle

MMS-TTS is released under **CC-BY-NC 4.0 — non-commercial**. It is open-source and satisfies the "no proprietary SDK" restriction, but the NC clause is a real term. Two options:

1. **Use MMS now, declare it plainly** in your compliance table as CC-BY-NC 4.0, non-commercial research use. Fast, honest, and defensible for a hackathon. A judge who notices will respect the disclosure far more than a silent omission.
2. **Migrate to [AI4Bharat Indic-TTS](https://github.com/AI4Bharat/Indic-TTS)** (13 Indian languages) or [IndicF5](https://github.com/AI4Bharat/IndicF5) (11 languages) for a cleaner licence and better Indic prosody. This needs an ONNX export step, so it is a week, not a day.

**Recommendation: do (1) this sprint to reach 10/10, and treat (2) as a stretch goal.** Shipping ten mediocre voices scores far better than five good ones, because the rubric multiplies across languages. Quality per voice is a tiebreaker; coverage is a gate.

For Odia STT, check whether AI4Bharat publishes an IndicConformer Odia checkpoint; if not, say so explicitly in your submission rather than leaving a silent hole.

---

## 4. What actually separates top-5 from top-50

In a rubric this quantitative, the ranking is decided by four things, in order:

1. **You have numbers and they are real.** A CSV of per-language WER, RTF, and end-to-end latency, measured on a named budget phone, beats any amount of architecture narrative. Most teams will present adjectives. Present a table.
2. **You measured on hardware that matches the PS.** "Low and mid-range mobile phones" is stated. Numbers from a flagship are close to worthless here — and numbers from a budget phone that are still good are the strongest possible signal.
3. **Nothing in the demo is fake.** Your codebase is unusually honest about this already (`AppResult.Error` instead of placeholder text, the VAD backend disclosure). Extend that: every number on every slide gets a provenance label.
4. **The scope is exactly the PS, and visibly so.** Teams lose points for sprawl. Your Phi-3 assistant and your range roadmap both read as "we built other things too," which invites the question of whether the core is finished.

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
| AFSK modem, HDLC, `MeshLink` | WER/RTF benchmark harness | Two-device Wi-Fi Direct + BT transport |
| Range/DTN/BLE Coded PHY work | Measuring on a budget phone | The honest-error discipline (`AppResult.Error`, no fabricated output) |
| Phi-3 / llama.cpp in the judged build | The remaining 5 TTS voices | Protobuf wire format — small and correct |
| Landing page polish | Phone mode + playback queue | Model download integrity pipeline |
| New feature work of any kind | Fixing the FFT | Room peer registry |

---

## 7. Six-week plan

Each week ends with a measurement run. No week is complete until the scorecard in §5 has been re-taken on the target phone.

### Week 1 — Make it fast and make it measurable

The point of week 1 is that **every subsequent week can be evaluated.**

| Task | Owner | Ref |
| --- | --- | --- |
| Radix-2 FFT-512 + precomputed sparse filterbank + pooled buffers | Gaurav | IMPROVEMENT_PLAN §2 |
| Timing instrumentation: `captureEnd`/`featureDone`/`inferDone`/`tx`/`rx`/`ttsDone`/`firstAudioFrame`, CSV to `filesDir` | Gaurav | §6 |
| RTF computed and logged per utterance, split feature vs inference | Gaurav | §6 |
| Delete `resampleTo16k`, play at native 22050 Hz | Gaurav | §3.5 |
| ABI split to arm64-v8a; drop duplicate ONNX Runtime; exclude llama.cpp from judged build | Sarthak | §5.1 |
| Acquire the target budget phone; take the first full baseline | Both | §5 |

**Exit criteria:** RTF measured and below 0.5. APK under 80 MB. A CSV with real rows in it.

### Week 2 — Close the language gap

| Task | Owner | Ref |
| --- | --- | --- |
| Fill the five `ModelRegistry` stubs with sherpa-onnx MMS voice URLs, hashes, sizes | Gaurav | §3 |
| Register the five codes in `TTSModule.LANGUAGE_TO_PACK` | Gaurav | §3 |
| Source or document the Odia STT checkpoint | Gaurav | §2 |
| Rework `coreTransceiverPacks()` to a chosen language pair, not all nine | Gaurav | IMPROVEMENT_PLAN §5.2 |
| Downloads screen: per-language selection, honest size display | Sarthak | §5.2 |
| Licence table in the README covering MMS CC-BY-NC | Sarthak | §3 |

**Exit criteria:** 10/10 TTS, 10/10 STT or a documented reason. Bundle for one pair under 250 MB.

### Week 3 — Accuracy

This is the 40% week. Treat it as the most important one.

| Task | Owner | Ref |
| --- | --- | --- |
| Extract `cfg.preprocessor` from the NeMo checkpoint; match preemphasis, slaney norm, n_fft, centering, window | Gaurav | IMPROVEMENT_PLAN §3.1 |
| Golden-reference test: NeMo features in Python vs Kotlin output, assert to 1e-3 | Gaurav | §3.1 |
| WER harness over a public Indic test set, per language, CSV out | Gaurav | §5 |
| VAD pre-roll ring buffer + adaptive noise floor + hysteresis | Gaurav | §3.3 |
| Switch to `VOICE_RECOGNITION` audio source | Gaurav | §3.4 |
| Text normalization before TTS: numbers, abbreviations, Latin tokens | Sarthak | §3.6 |

**Exit criteria:** WER measured per language and within 3 points of published. Golden test green in CI.

### Week 4 — PS compliance and the remaining latency

| Task | Owner | Ref |
| --- | --- | --- |
| Wire `setConnectionMode` to a UI toggle; PTT off = phone mode | Sarthak | §7.7 |
| Single-consumer playback queue; ALERT pre-empts | Gaurav | §7.8 |
| Streaming TTS on sentence/clause boundaries | Gaurav | §4.3 |
| Adaptive endpointing to replace the fixed 800 ms | Gaurav | §4.1 |
| Honour `message.dstLang`; softmax the confidence score | Gaurav | §7.1, §7.4 |
| Warm the configured language pair at service start | Gaurav | §4.4 |
| LRU model cache; RAM metric switched to total PSS | Gaurav | §5.3 |

**Exit criteria:** every §2 compliance row green. End-to-end sentence→audio under 2 s.

### Week 5 — Quality and headroom

| Task | Owner | Ref |
| --- | --- | --- |
| CTC prefix beam search, optional per-language KenLM | Gaurav | §3.2 |
| Streaming/chunked STT inference | Gaurav | §4.2 |
| Idle-listening power pass: buffer reuse, longer wakeups | Gaurav | §5.4 |
| Informal TTS listening test, 5 native speakers per language | Sarthak | §5 |
| Re-export STT INT8 CTC-only; measure size and WER delta | Gaurav | §5.2 |

**Exit criteria:** stretch targets attempted. Nothing regressed.

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
| No Odia STT checkpoint exists | Medium | Document it explicitly; 9/10 with a stated reason beats a silent gap |
| Budget phone thermally throttles during demo | Low–Medium | Measure a 10-minute sustained run in week 6; report throttled numbers, not just first-run |
| Six weeks is not available | — | Weeks 1–3 are the irreducible core. Weeks 4–5 are compliance and polish. Week 6 cannot be cut |

---

## 10. So — how much more?

**Roughly four to six focused weeks, and one strategic decision.**

The decision is to stop building new capability and start proving the capability you already have. Every remaining gap on the scorecard is a known, bounded engineering task — not one of them is research. There is no invention left in this project, only execution and measurement.

Concretely, the gap to a top-5 submission is:

- **One function** (the FFT) standing between you and a passing RTF.
- **Five URLs** standing between you and full language coverage.
- **One config change** standing between you and a competitive app size.
- **One week of measurement work** standing between you and being able to prove any of it.
- **Two deletions** (range work, LLM) that cost nothing and sharpen everything.

That is a genuinely reachable position, and it is closer than the current state of the repository suggests. What it requires is discipline about scope, not more ambition.

The teams that beat you will not have built something you could not build. They will have measured what they built, on a cheap phone, and written the numbers down.

---

_iTantra · Smart India Hackathon 2026 · Problem Statement #26173_
