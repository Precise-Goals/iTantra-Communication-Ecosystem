# T78 Step 5 — phone run (PARTIAL: low-range phone only)

> Spec: `docs/IMPLEMENTATION_SPEC_2.md`, Group I → "T78 🔬 · SraVaani TDT engine", Step 5.
> This is T77 Step 5, now possible because T78 Step 4 gives `STTModule` real dual-session
> support for the SraVaani pair. **Only the low-range phone (Checkpoint 3's gate) was run in
> this session. The mid-range phone (Vivo V2338) was not tested — its connection was flaky
> throughout and it was deferred to save time. Do this before treating Checkpoint 3 as final.**

## Phones

| Role | Device | Android | RAM |
| --- | --- | --- | --- |
| Low-range (Checkpoint 3 gate) | CPH2467 (serial `9d6a993e`) | 15 | ~7.6 GB |
| Mid-range (T77 runs 1-3 phone) | Vivo V2338 (serial `10BE561DTE000C9`) | 16 | ~7.6 GB | — **not tested this session** |

Both phones already had all nine existing IndicConformer STT models (`hi gu mr kn ml ta te bn en`),
VAD, and TTS data resident from prior app use (no Odia model exists — expected, per T64's
findings). No downloads were needed for the baseline.

## Method

- Debug APK built from `feature/t78-tdt-engine` @ commit `4e99f58` (Steps 1-4), installed via
  `adb install -r`.
- **A real gap found and worked around:** T78 Step 4's code unconditionally routes all nine
  SraVaani-backed language codes (including `hi`) to the SraVaani backend. This means there is no
  code path left in this build to run Hindi through IndicConformer — so a same-build,
  same-phrase "baseline vs new" comparison isn't directly possible with the shipped code. Worked
  around with a **temporary, uncommitted, local-only edit** (excluding `"hi"` from
  `SRAVAANI_LANGUAGES`, same spirit as T77's "spike, never merged" caution): built, installed,
  captured the IndicConformer baseline, then reverted the edit (`git diff` confirmed clean),
  rebuilt, and installed the real T78 build for the SraVaani measurement. This is a real
  methodology note for whoever runs Step 5 again, not just a one-off inconvenience — the
  same-build baseline comparison this step wants will need this workaround (or a debug-only
  override flag) every time until T64 or T78's routing is finalized.
- **Test phrases** (none existed before this session — created here, should be reused for Step 7's
  re-run): 10 Hindi, 5 Tamil, 5 Odia. Full list with romanization in this PR's conversation record;
  Hindi phrases below for reference:
  1. Sahaayata bhejo abhi (Send help now)
  2. Sthaan kee pushti ho gayee (Location confirmed)
  3. Mujhe sunaaee de rahaa hai (I can hear you)
  4. Sandesh mil gayaa (Message received)
  5. Raasta saaf hai (The path is clear)
  6. Khatra hai, saavadhaan raho (There is danger, be careful)
  7. Baitaree kam hai (Battery is low)
  8. Hum paanch minat mein pahunchenge (We will arrive in five minutes)
  9. Kripaya dobaara boliye (Please say that again)
  10. Sampark toot gayaa thaa (Contact was lost)
- Sustained session **shortened to ~30 seconds** (spec asks for 10 minutes) to fit this session's
  time budget. This is a real limitation, not a substitute — thermal throttling and slow memory
  growth over a full 10-minute session were not checked.
- Tamil and Odia: **not reliably tested.** Neither tester is a native speaker of either language;
  reading Latin-transliterated phrases produced mispronounced, out-of-distribution audio. Odia is
  also not yet in the app's language picker (`ui/component/Languages.kt` `STT_LANGUAGES` — adding
  it is Sarthak's pending Step 6 item), so it cannot be selected at all right now. See "Tamil/Odia"
  below for what was actually observed.
- ANR/crash/kill watched continuously via `adb logcat -b events -b crash -b main`, filtered for
  `am_kill`, `am_anr`, `FATAL EXCEPTION`, and "not responding", for the duration of both runs.

## Results — CPH2467 (low-range)

Full CSVs: `cph2467_indicconformer_baseline_telemetry.csv` (20 utterances),
`cph2467_sravaani_telemetry.csv` (26 utterances, includes phrase test + abbreviated sustained
session). Logcat (filtered to `STTModule` + crash/ANR/kill lines — the full unfiltered dump was
almost entirely unrelated OEM window-manager chatter): `cph2467_stt_logcat_filtered.txt`. Meminfo
snapshot: `cph2467_sravaani_meminfo.txt`.

| Metric | IndicConformer (baseline) | SraVaani (TDT) |
| --- | --- | --- |
| Median RTF | 0.212 | 0.260 |
| Median speech-end → STT-complete | 556.2 ms | 682.6 ms |
| Model load time | not separately isolated (already warm at test start) | 5261 ms (cold load, shared pair) |
| TOTAL PSS snapshot (post-test) | ~737-739 MB | **~1364 MB** |
| Crash / ANR / kill of `com.itantra.debug` | None | None |

(`am_kill` events for other OEM background apps — deskclock, notificationmanager, romupdate,
settings, ChatGPT — were observed during testing; these are the OnePlus/OPlus "OsenseKillAction"
background-app trimmer routinely clearing cached processes, confirmed unrelated to
`com.itantra.debug`, which stayed resident throughout both runs.)

### Checkpoint 3 — the three literal gates, on the low-range phone

| Gate | Result |
| --- | --- |
| No kill or ANR | **PASS** — process alive throughout, no crash/ANR/kill logged |
| RTF < 1.0 | **PASS** — median 0.260 |
| Speech end → STT complete ≤ ~1.5× IndicConformer's | **PASS** — 682.6 ms vs 834.2 ms threshold (1.5 × 556.2 ms) |

**Checkpoint 3 passes on the data collected**, with the caveats above (small sample — 9 SraVaani
vs 20 IndicConformer utterances after VAD segmentation, single 30s sustained window instead of
10 minutes, one phone instead of two).

### Memory — a real concern, not a Checkpoint 3 gate

Checkpoint 3's three criteria don't include a memory threshold (memory is tracked separately for
the final PS-rubric comparison). But the number itself is stark and needs to carry forward:
**TOTAL PSS jumped from ~737 MB (IndicConformer alone) to ~1364 MB with the SraVaani pair
loaded** — an increase of ~627 MB, itself *more* than the 464.7 MB combined on-disk size of the
quantized pair (likely NNAPI delegate compilation + runtime activation buffers on top of the
weights). Against the team's own target of <700 MB peak PSS (`ACTION_PLAN.md` §5, a guide not a
gate) this is nearly double, on the phone the PS specifically calls "low range." This must be
weighed heavily in the eventual adopt/keep decision regardless of Checkpoint 3's formal pass.

### Transcripts

IndicConformer baseline transcripts were noisy (phrases spoken too close together for VAD to
segment cleanly in the first take — see the redone take's transcripts, cleaner but still not
verbatim-perfect, e.g. "चाइता भेजो अभी" for "सहायता भेजो अभी"). SraVaani's Hindi transcripts
during the phrase test were qualitatively closer to the spoken phrases (e.g. exact match on
"स्थान की पुष्टि हो गई" and "हम पाँच मिनट में पहुँचेंगे"). Neither run is a controlled WER
measurement — that's what Step 1's Colab desktop harness is for; this is a smoke test that the
on-device pipeline produces sane text, not a phone-side accuracy number.

An incidental, encouraging observation: during the continuous-listening window, SraVaani
transcribed several unprompted ambient utterances correctly in their own scripts — Malayalam
("വലിക്കുവേണ്ടി", "ഇത് ഷിയും", "അല്ല"), Odia ("ଭଲ ପାଇଛି"), and Bengali/Assamese script
("অসমীয়া") — while the app's language selector stayed on `hi` throughout. This suggests SraVaani
is doing real per-utterance language/script recognition rather than defaulting to one script,
though it's an anecdotal observation from incidental audio, not a controlled test.

### Tamil/Odia

The Tamil language selector did not appear to register — every telemetry row, including the ones
where Tamil phrases were spoken, is still tagged `hi` (harmless for the shared-pair architecture
itself, since `ta` and `hi` route to the identical cache key by code inspection, but this session
did not empirically confirm the UI toggle triggers it). The attempted Tamil audio was transcribed
as Devanagari-script gibberish, not Tamil script — most likely because neither tester speaks Tamil
natively and read a Latin-transliterated phrase aloud, producing mispronounced audio rather than a
fair test of the model. Odia could not be tested at all (not yet in the language picker).
**Tamil and Odia coverage is incomplete and should be redone with a native or fluent speaker**
before this factors into any accuracy conclusion.

## What's still needed before Checkpoint 3 is final

- [ ] Full 10-minute sustained session (this run used ~30 seconds).
- [ ] The mid-range phone (Vivo V2338) — baseline + SraVaani comparison, same protocol.
- [ ] A real Tamil speaker (and, once the language picker is updated, a real Odia speaker) for the
      two SraVaani-only extension languages.
- [ ] A resolution for the "no same-build IndicConformer-Hindi path" gap — either a permanent
      debug-only override flag, or accept the temporary-edit workaround as the standing procedure
      for future re-runs (e.g. Step 7's release-candidate re-test).

## Files in this directory

- `cph2467_indicconformer_baseline_telemetry.csv` — 20 utterances, IndicConformer Hindi baseline
- `cph2467_sravaani_telemetry.csv` — 26 utterances, SraVaani phrase test + abbreviated sustained session
- `cph2467_sravaani_meminfo.txt` — `dumpsys meminfo com.itantra.debug` snapshot, SraVaani loaded
- `cph2467_stt_logcat_filtered.txt` — `STTModule` + crash/ANR/kill lines across both runs (load times, transcripts, ANR/kill watch)
