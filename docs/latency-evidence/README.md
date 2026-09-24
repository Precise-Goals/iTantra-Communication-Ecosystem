# Phrase-level pipelining — latency evidence

Raw artifacts from a real two-phone test of T41 (adaptive endpointing) + T65 (phrase-level
pipelining) + T62 (Silero VAD repair), pulled directly off both devices immediately after the
test. Committed so the claims in the main README are backed by something reproducible, not just
chat transcript.

**Commit under test:** `96329c1` (`fix: correct README/comment inaccuracies, add real
check_silero.py verification`) — `gradlew assembleDebug` was run from a clean tree at this
commit, both devices were force-stopped and cold-started on the resulting APK, and VAD backend
was confirmed as `NEURAL` on both (see the logcat files) before the PTT test below.

**Devices:**
- Sender: `23122PCD1I` (POCO), fingerprint `POCO/garnetp_in/garnet:16/BP2A.250605.031.A3/OS3.0.301.0.WNRINXM:user/release-keys`
- Receiver: `CPH2721` (OPPO), fingerprint `OPPO/CPH2721IN/OP5ED7L1:16/BP2A.250605.015/V.R4T2.42d41b3-1dbb4e5-1dbb4e7:user/release-keys`

**Files:**
- `sender_logcat.txt` / `receiver_logcat.txt` — raw `adb logcat`, tag-filtered, from cold app
  start through the end of the test. Unedited.
- `sender_telemetry.csv` / `receiver_telemetry.csv` — raw `files/telemetry.csv` pulled via
  `adb shell run-as com.itantra.debug cat files/telemetry.csv` immediately after. Contains rows
  from an earlier, unfinished pairing attempt in the same app process (`id` restarts once you
  hit the actual test rows — see below); left in unedited rather than trimmed.

## What the test was

Hold PTT on the sender, speak a short sentence, pause ~1s, speak another short sentence, still
holding, then release.

## Reading the logs — same-device-clock facts (no cross-device sync assumptions)

From `sender_logcat.txt` (all one clock):

| Time | Event |
| --- | --- |
| 15:14:12.448 | PTT pressed, capture starts |
| 15:14:16.087 | **Speech segment complete: 49600 samples** — VAD cut phrase 1, still holding |
| 15:14:16.178 | STT model load for `hi` begins (first STT call of this process) |
| 15:14:17.890 | **Speech segment complete: 22400 samples** — VAD cut phrase 2, still holding |
| 15:14:18.394 | `STT('hi') loaded in 2306ms` — only now can phrase 1 be transcribed |
| 15:14:19.150 | Phrase 1 STT done: `'हेलो हेलो कैनूोग आर्मी हेलो हेलो'` (755ms, self-reported) |
| 15:14:19.385 | PTT released, capture stops |
| 15:14:19.525 | Phrase 2 STT done: `'हेलो हेलो'` (358ms, self-reported) |

**The robust, reproducible claim:** both phrases were cut by VAD *while PTT was still held*
(both "Speech segment complete" lines precede "Audio capture stopped"). That is the thing T41 +
T65 + T62 actually needed to prove, and it's directly visible in an unedited log, tied to a
specific commit and a specific installed APK.

**The head-start-before-release claim needs a correction.** In this run:
- Phrase 1 finished **235ms before** release (19.150 vs 19.385).
- Phrase 2 finished **140ms after** release (19.525 vs 19.385) — release happened while phrase 2
  was still queued behind phrase 1 in the Mutex-serialized STT pipeline.

This is smaller than, and in one case the opposite sign of, the 1.2–5.0s figures previously
written into the README from an earlier, uncommitted test run. **That earlier number is not
backed by committed evidence and should be treated as unverified** — it was read off a live
logcat stream in conversation, not captured to a file.

**Why the head start was lost in this run — corrected 2026-09-24.** An earlier version of this
section blamed how long the user kept holding PTT. The log shows the dominant cause is different:
**the Hindi STT model was loaded lazily, on the first phrase.** Phrase 1 was cut at 16.087, but
the model did not finish loading until 18.394 (`loaded in 2306ms`). Phrase 1 could not start
transcribing before then, and phrase 2 queued behind it. The pipeline was waiting on a one-time
model load, not on the user.

📐 *Projected, not measured:* with the model already loaded, phrase 1 (3.1 s of audio, warm speed
≈ 0.26× real time from the telemetry) would have finished around 16.9, about **2.5 s before
release**; phrase 2 around 18.3, about **1.1 s before release**. This is exactly what task T45
(warm the configured language's models at service start) is for. Re-run this same test after T45
to replace the projection with a measurement.

How long the user holds PTT after speaking still matters, but only at the margin: with warm
models, a phrase is ready well under a second after its pause is detected.

## A more stable metric: per-phrase STT/TTS processing time

From `sender_telemetry.csv`, the last two rows (`id=1,audio_ms=3100` and `id=2,audio_ms=1400`,
matching 49600 and 22400 samples at 16kHz) are this test's rows — earlier rows are from an
aborted pairing attempt earlier in the same app process and should be ignored:

| Phrase | audio_ms | stt_ms | feature_ms | infer_ms | Note |
| --- | --- | --- | --- | --- | --- |
| 1 | 3100 | 3076.8 | 2439.0 | 637.8 | First STT call this process — `feature_ms` **contains the 2306 ms model load** (see below) |
| 2 | 1400 | 360.6 | 13.3 | 347.2 | Warm — representative per-phrase number |

**How to read these columns (corrected 2026-09-24).** The service stamps "capture ended" and then
calls `ensureLoaded()` before transcribing, so `feature_ms` (and therefore `stt_ms` and `rtf`)
includes any model load. For phrase 1 that is 2306 ms of the 2439 ms; actual feature extraction
was ~130 ms, so phrase 1's real processing was ~770 ms for 3.1 s of audio (≈ 0.25× real time,
the same speed as phrase 2). An earlier version of this table attributed the 2439 ms to JIT
warm-up; that was wrong.

A second caveat: since T65, "capture ended" is stamped when the segment queue **picks the phrase
up**, not when the VAD cut it. So `stt_ms` leaves out time spent queued behind an earlier phrase
— phrase 2 waited ~1.2 s behind phrase 1 in this run, and that wait is not in its 360.6 ms. The
rubric's "words said → STT complete" needs the cut time. Task T71 fixes both stamps.

From `receiver_telemetry.csv`, matching rows by receive order:

| Phrase | tts_ms | tts_audio_ms | Note |
| --- | --- | --- | --- |
| 1 | 2343.4 | 2772 | First TTS call this process — includes loading the Hindi voice |
| 2 | 4731.4 | 816 | **Includes waiting for phrase 1 to finish playing** — see below |

**What `tts_ms` measures.** It is *received → first audio sample played*, so it includes any time
the phrase spends waiting in the receiver's playback queue (T38). Phrase 2 is fully explained by
that queue — computed from `receiver_telemetry.csv` and `receiver_logcat.txt`, all on the
receiver's clock:

| Receiver clock | Event |
| --- | --- |
| 18.676 | Phrase 1 received |
| 19.050 | Phrase 2 received |
| 21.019 | Phrase 1 starts playing (18.676 + 2.343) |
| 23.791 | Phrase 1 finishes playing (21.019 + 2.772 s of audio) |
| 23.781 | Phrase 2 starts playing (19.050 + 4.731) — immediately after phrase 1, within 10 ms |

So phrase 2 was not slow: the queue correctly held it until phrase 1 finished, which is what T38
exists to do. (An earlier version of this table called it "unexplained, possibly device
contention"; that was wrong.) A fair per-phrase TTS number needs a separate synthesis-only timing
— `ttsDoneNs − rxNs` — which the telemetry records internally but does not yet write to the CSV.

**Honest summary:** once warm, per-phrase STT is fast and consistent (~350 ms for a 1.4 s phrase;
warm speed 0.20–0.26× real time across three rows of `sender_telemetry.csv`). The first call of
each session pays a one-time model load — 2.3 s for STT in this run, and a similar cost for the
first TTS call — which is what erased the head start here (see above). Receiver `tts_ms` includes
playback-queue wait and should not be read as synthesis speed.

⚠️ **Possible issue, not confirmed:** phrase 1's and phrase 2's synthesis finished only 92 ms
apart in `receiver_logcat.txt` (20.874 and 20.966) although they arrived 374 ms apart. That fits
both calls loading the voice model at the same time: `TTSModule` has no lock, and its model cache
is an unsynchronised map. The logcat here is filtered to other tags, so it cannot confirm this. A
`TTSModule` tag in the next capture will settle it.

## What this does and doesn't prove

**Proven, with committed evidence, on real human speech on real hardware:**
- The NEURAL Silero VAD backend (not the energy fallback) was active on both devices for this
  test (see `VAD initialized — backend: NEURAL` in both logcat files, timestamped at cold start).
- Mid-hold phrase segmentation fires correctly against real human speech: both phrases were cut
  while PTT was still held, in spoken order, via the Mutex-serialized segment queue.
- The receiving device does receive and speak phrases sent mid-hold, not just on release. (In
  this run, playback itself began after release, because of the cold model load described above.)
- Received phrases play strictly one after another, never overlapping (phrase 2 started within
  10 ms of phrase 1 ending).

**Not proven / open:**
- A stable "how many seconds early does the receiver speak" number — this run alone shows a
  range from -140ms to +235ms; the previously-cited 1.2–5.0s range from an uncommitted test
  cannot be independently verified from anything in this repository and should not be relied on
  until it's reproduced with committed evidence the same way this run was.
- The head start with warm models (T45). Projected above at ~1–2.5 s; not yet measured.
- Synthesis-only TTS time per phrase (not in the CSV yet).
- Whether `TTSModule` loads the same voice twice under concurrent messages (see the ⚠️ note).

## Next capture — what to change

1. Implement T70 and T45 (warm-up) first, so the run measures the pipeline rather than a model
   load, and T71, so `stt_ms` starts at the VAD cut and model load is its own column.
2. Add `TTSModule:*` to the logcat tag filter on the receiver.
3. Keep everything else identical (same phones, same two-sentence script), so the result is
   directly comparable with this run.
- Behavior under sustained real background noise (only tested in whatever ambient conditions the
  test room had; not a controlled noise test).

## Run 2

Raw artifacts from a second two-phone test, after implementing T70 (serialised `TTSModule`), T45
(warm models at start), T72 (per-language walkie-talkie), and T71 (fixed telemetry stamps).

**Commit under test:** `bda505c` (`T38/T62: doc-comment and log-label cleanup`, tip of
`feature/latency-pipeline-2` — includes all four tasks above) — `gradlew assembleDebug` was run
from this commit, both devices were force-stopped and cold-started on the resulting APK, and
`Warm-up stt=hi:true tts=hi:true` was confirmed in logcat on both before any PTT press.

**Devices — ⚠️ receiver changed from Run 1, not directly comparable hardware:**
- Sender: `23122PCD1I` (POCO) — **same physical device as Run 1**, fingerprint
  `POCO/garnetp_in/garnet:16/BP2A.250605.031.A3/OS3.0.301.0.WNRINXM:user/release-keys`.
- Receiver: `RMX5000` (realme) — **different device from Run 1's OPPO CPH2721** (that phone
  wasn't available for this session). Fingerprint
  `realme/RMX5000IN/RE6066L1:16/UKQ1.231108.001/U.R4T2.1e7c4a4_6ef579_5c30fb:user/release-keys`.
  Any receiver-side timing difference between the two runs may be partly hardware, not purely
  code. The sender-side numbers (where the phrase-cut/STT work happens) are directly comparable.

**Files** (all raw, unedited, in [`run2/`](run2/)):
- `sender_logcat.txt` / `receiver_logcat.txt` — `adb logcat -v time -s iTantraService:*
  AudioCapture:* STTModule:* TTSModule:* VADModule:* Telemetry:*`, streamed continuously to a file
  from cold start through the end of testing (the `TTSModule` tag, missing in Run 1, is included
  this time per the "Next capture" note above).
- `sender_telemetry.csv` / `receiver_telemetry.csv` — raw `files/telemetry.csv`, pulled via `adb
  shell run-as com.itantra.debug cat files/telemetry.csv` after the test. Contains rows from
  several things tried in the same session before the clean two-phrase take (a connectivity check
  after the very first cold start, a fragmented take, a Kannada check) plus a Tamil check
  afterward — all left in unedited, same policy as Run 1.

### A real gotcha hit during this capture — VAD backend on cold start

The receiver's *first* cold start logged `VAD initialized — backend: BASIC_ENERGY (physical path:
null, neural session loaded: false)` at 18:50:01.774 — the NEURAL backend Run 1 used on both
devices. `adb shell run-as com.itantra.debug ls -la files/models/silero_vad_v4.onnx` showed the
model file *was* present, but with an mtime of 18:51 — about 90 seconds **after** `VADModule` had
already initialized and fallen back to the energy detector. The file wasn't in place yet at the
moment `VADModule` checked for it. A second force-stop + cold start (once the file was actually
there) produced `VAD initialized — backend: NEURAL` on both devices, confirmed before testing —
see `sender_logcat.txt`/`receiver_logcat.txt` line 7–8. The very first cold start's log is
preserved separately as `run2/sender_logcat_prelim_connectivity_check.txt` /
`run2/receiver_logcat_prelim_connectivity_check.txt` for the record, but is **not** part of the
timing analysis below — only the second, NEURAL-confirmed cold start is.

### The clean take — same-device-clock facts

From `sender_logcat.txt` (all one clock), the deliberate two-phrase take starting at 18:57:53:

| Time | Event |
| --- | --- |
| 18:57:53.477 | PTT pressed, capture starts |
| 18:57:56.149 | **Speech segment complete: 32000 samples** — VAD cut phrase 1, still holding |
| 18:57:56.718 | Phrase 1 STT done: `'हलो कैनो हेयर में'` (573.4ms, telemetry `id=11`) |
| 18:58:00.341 | **Speech segment complete: 32000 samples** — VAD cut phrase 2, still holding |
| 18:58:00.907 | Phrase 2 STT done: `'हलो वन टू थ्री'` (565.2ms, telemetry `id=12`) |
| 18:58:01.111 | PTT released, capture stops |

Unlike Run 1, **both** phrases finished transcribing before release this time — no model load
landed on either phrase, and neither queued meaningfully behind the other (`wait_ms` ≈ 1ms for
both, see table below).

### Before / after vs Run 1

Run 1's numbers are the committed rows from `../sender_telemetry.csv` / `../receiver_telemetry.csv`
(`id=1`/`id=2`, the ones the top-level `../README.md` describes). Run 2's are `id=11`/`id=12`
(sender) and the matching two rows (receiver) from this run's CSVs, `wait_ms`/`tts_synth_ms` are
new columns T71 added and did not exist in Run 1.

| Metric | Run 1, phrase 1 | Run 2, phrase 1 | Run 1, phrase 2 | Run 2, phrase 2 |
| --- | --- | --- | --- | --- |
| Head start before release | **+235 ms** | **+4393 ms** | **−140 ms** (finished *after* release) | **+204 ms** |
| `stt_ms` | 3076.8 ms (incl. ~2306 ms model load) | 573.4 ms | 360.6 ms | 565.2 ms |
| `wait_ms` (new, T71) | not recorded | 1.0 ms | not recorded | 0.9 ms |
| `rtf` | 0.9925 (model-load-polluted) | 0.2862 | 0.2576 | 0.2821 |
| `tts_synth_ms` (new, T71, receiver) | not recorded | 255.7 ms | not recorded | 230.1 ms |
| `tts_ms` (receiver, incl. queue wait) | 2343.4 ms | 292.3 ms | 4731.4 ms | 268.1 ms |

**Reading this honestly:**
- **Head start is real and large now**, not merely projected: phrase 1 finished **4.39 s** before
  release (vs. a projected 2.5 s), phrase 2 **204 ms** before release, flipping from *after*
  release in Run 1 to *before* it. This is one run, on one phrase pair, on the pairing described
  above — not a guaranteed number for every utterance length, but it directly demonstrates T45
  doing what it was for: the one-time model load is gone from the hot path.
- `stt_ms` for phrase 1 dropped from 3076.8 ms to 573.4 ms because the ~2306 ms model load that
  used to land inside it (Run 1) is gone (T45) and no longer double-counted into `stt_ms` even
  when it does happen elsewhere (T71). Phrase 2's `rtf` (0.2576 → 0.2821) is in the same band as
  Run 1 — real per-phrase processing speed is unchanged, as expected; T45/T71 do not touch
  inference itself.
- `wait_ms` (new) is ≈1ms for both phrases here because nothing queued behind anything — see the
  Tamil section below for a case where it captured a real wait.
- Receiver `tts_ms` fell from seconds to a few hundred ms mainly because T45 preloads the Hindi
  voice (no cold `sherpa-onnx TTS loaded` mid-message) and because, in this run, phrase 2 arrived
  4.2 s after phrase 1 — long enough for phrase 1's 1.3 s of audio to finish playing well before
  phrase 2 needed the queue, unlike Run 1's tighter timing. `tts_synth_ms` (new) isolates the
  synthesis-only cost for the first time: 255.7 ms / 230.1 ms, both close to `tts_ms` since there
  was no queue wait to inflate the difference this run.

### T70 — no double voice load, now directly observable

Run 1 could only flag a ⚠️ *possible* double-load from timing coincidence; the `TTSModule` tag was
missing from its logcat filter. This run includes it. Across the **entire** receiver session —
warm-up plus 10 messages received and spoken (5 Hindi, 3 Kannada, 2 Tamil) — the string
`sherpa-onnx TTS loaded for 'hi'` appears **exactly once**, at warm-up (18:54:19.704). Every
subsequent message reused the cached voice; none loaded it again, even when messages arrived
seconds apart. Run 1's ⚠️ note is resolved: no double-load was observed with the lock in place.

### T72 — language switch works, and a real gap it surfaced

On the sender, switching to Tamil produced `Warm-up stt=ta:true tts=ta:false in 3442ms` — STT
loaded correctly, and TTS correctly reports unavailable (Tamil has no voice yet, per
`ModelRegistry`) rather than pretending to have one. A held PTT phrase was transcribed correctly:
`STT inference: 'என்ன பாடா' ... [ta]`. Switching to Kannada similarly produced correct `[kn]`
transcriptions.

One of the Tamil phrases (`sender_telemetry.csv id=16`) shows `wait_ms=701.2` — that phrase was
captured and queued while `ensureLoaded('ta')` was still running from the language switch, and
`sttStartNs` wasn't stamped until the load finished. This is exactly the scenario T71's `wait_ms`
column exists to surface, caught in the wild on the very first phrase after a language switch.

**What did *not* happen, and is worth writing down plainly:** the T72 spec's own VERIFY step
predicted the receiver would report a real "TTS not available" error for a language without a
voice. Instead, the receiver spoke every Kannada and Tamil message **in the Hindi voice**
(`receiver_logcat.txt`: `Received from ...: 'என்ன பாடா' [SPEECH]` at 19:00:04.537, immediately
followed by `sherpa-onnx TTS synthesized ... [hi]` — same `[hi]` tag on every Kannada/Tamil
message received), because it mispronounced the text rather than refusing it. The reason
is in `ITantraForegroundService.onTextReceived` (`app/src/main/java/com/itantra/core/service/ITantraForegroundService.kt`,
around the `onTextReceived` override): it calls
`ttsModule.synthesize(message.text, ttsLanguage)` using the **receiver's own locally-selected**
`ttsLanguage`, not `message.srcLang`/`dstLang` — fields `TransceiverMessage` actually carries and
the sender does set. Since the receiver in this test never switched off Hindi, it always had a
voice to (mis-)use. `IMPLEMENTATION_SPEC.md`'s T43 correction says "the receiver's voice must
match the language the text is written in (`srcLang`)" — that is the documented intent, but
`onTextReceived` does not implement it; it was out of scope for T70/T45/T72/T71 to fix, so it
wasn't touched here. Flagging it for whoever picks up the next task, rather than leaving it to be
rediscovered as a mystery mispronunciation bug.

## Run 3

Raw artifacts from a third two-phone test, after implementing Stage A: T43 (re-anchored — receiver
speaks in the text's own language), T73 (re-init VAD after first-install download), T46 (bounded
STT/TTS caches), T47 (real RAM metric), and T74 (AI Assistant shares the service's models).

**Commit under test:** `0d6bde5` (`T74: AI Assistant uses the service's models and playback
queue`, tip of `feature/stage-a`) — `gradlew assembleDebug` was run from this commit, both devices
were force-stopped/reinstalled and cold-started on the resulting APK.

**Devices — same pair as Run 2:**
- Sender: `23122PCD1I` (POCO), fingerprint
  `POCO/garnetp_in/garnet:16/BP2A.250605.031.A3/OS3.0.301.0.WNRINXM:user/release-keys` — installed
  with `adb install -r` (data preserved, so its model cache and prior test history carried over).
- Receiver: `RMX5000` (realme) — **fully uninstalled and reinstalled** (`adb uninstall` +
  `adb install`, not `-r`) specifically to test T73's first-install scenario. `adb shell pm clear`
  was tried first and refused by this ColorOS build with a `SecurityException` (no
  `CLEAR_APP_USER_DATA` permission for the calling shell UID) — worth knowing for future runs on
  this device: use uninstall+reinstall, not `pm clear`, to get an equivalent empty-data state.

**Files** (all raw, unedited, in [`run3/`](run3/)):
- `receiver_logcat_t73_first_install.txt` — the receiver's very first cold start after the fresh
  install, tag-filtered the same as the other logs, from before any model was downloaded through
  the VAD re-init.
- `sender_logcat.txt` / `receiver_logcat.txt` — the main test session, after both devices had all
  core models downloaded, cold-started and confirmed `NEURAL` VAD.
- `sender_telemetry.csv` / `receiver_telemetry.csv` — raw `files/telemetry.csv`, pulled after
  testing. Contains many rows from extensive language-switch testing (T46/T47 check) beyond the
  two-phrase script; left in unedited, same policy as Runs 1 and 2.
- `meminfo.txt` — three `dumpsys meminfo` `TOTAL PSS` readings taken during the T46/T47 language
  cycling, with the exact log lines each was anchored to.

### Two real device gotchas hit during this capture

1. **`adb shell monkey -c LAUNCHER` does not reliably start a microphone-type foreground service on
   this realme/ColorOS build.** It worked fine in Run 2 (right after the app had genuinely been in
   the foreground), but on this run's fresh install and again after ~10 minutes idle, monkey-driven
   launches crashed with `ForegroundServiceStartNotAllowedException` even though `RECORD_AUDIO` was
   already granted (`dumpsys package` confirmed `granted=true`) — Android's "must be in an eligible
   foreground state" check rejected the synthetic launch. A **physical tap on the icon** reliably
   worked every time. See `receiver_logcat_t73_first_install.txt` for the crash traces preceding
   the successful start.
2. **`adb shell pm clear` is blocked by this device's security policy** (see Devices, above) —
   uninstall + reinstall is the reliable way to simulate a first install here.

### T73 — confirmed: first-install VAD upgrades itself, no restart needed

From `receiver_logcat_t73_first_install.txt`, same-device clock:

| Time | Event |
| --- | --- |
| 19:33:37.320 | `VAD initialized — backend: BASIC_ENERGY (physical path: null, neural session loaded: false)` — fresh install, model not downloaded yet |
| 19:33:58.519 | `VAD re-init after download — neural: true` — **21 seconds later, no app restart** |

**Pass.** This directly fixes the exact failure mode Run 2 hit (`run2/receiver_logcat_prelim_connectivity_check.txt`), where only a force-stop recovered the NEURAL backend.

### T43 — confirmed: no more mispronunciation, but the error is invisible

Sender picked Tamil and spoke; receiver stayed on Hindi throughout. `receiver_telemetry.csv` rows
28–30 (`lang=ta`) all show `tts_ms=0.0` and `tts_audio_ms=0` — **no audio was fabricated** for any
of the three Tamil messages received, and no `sherpa-onnx TTS synthesized ... [hi]` line follows
any of them in `receiver_logcat.txt` (contrast with Run 2, where every Kannada/Tamil message got a
`[hi]` synthesis). `sender_logcat.txt` confirms Tamil STT worked normally on the sending side
(`STT inference: 'எண்ணப்போடி' ... [ta]`).

**Pass on behavior. One caveat, checked directly against the code:** the spec's VERIFY step expects
logcat to show a literal `TTS not available for 'ta'` error line. It never appears, anywhere,
because `ITantraForegroundService.errorFlow` — the `SharedFlow` that `TTSModule.synthesize()`
emits that error into — has **zero collectors** (`grep -rn "errorFlow"
app/src/main/java/com/itantra/ui/` returns nothing). The error object is real and correctly
constructed, but it is created and immediately discarded; it never reaches Logcat or the UI. This
predates T43 and is out of its scope to fix, but it means "the user sees why nothing was said" is
not actually true yet — only "nothing false was said" is.

### T46 / T47 — cache is bounded, but PSS is not flat

The chip taps landed on different languages than the planned `Hindi → Tamil → Kannada → Hindi`
script (a repeat of Run 2's mis-tap pattern — see `meminfo.txt` for the full, honest account), so
this became a longer test: **15** `Warm-up stt=...` lines and **12** `Evicted STT session '<lang>'
(LRU)` lines total in `sender_logcat.txt`. Every eviction paired with a new language loading, and
the session count never exceeded 2 — confirmed by inspecting the full ordered sequence, not just
counting lines.

`adb shell dumpsys meminfo com.itantra.debug | grep "TOTAL PSS"`, three readings a few taps apart:

| Reading | When | TOTAL PSS | Change |
| --- | --- | --- | --- |
| 1 | after 5 taps, cache `{kn, hi}` | 991,918 KB (968.7 MB) | — |
| 2 | 2 taps later, cache `{gu, hi}` | 1,212,180 KB (1183.8 MB) | +220.3 MB |
| 3 | 2 taps later, cache `≈{gu,hi}`/`{en,gu}` | 1,281,000 KB (1251.0 MB) | +67.2 MB |

**Partial pass, reported honestly.** The *object* cache is verifiably bounded (12 evictions, never
more than 2 sessions open — this is the part T46 actually controls). But the *process's* memory
footprint is not flat across repeated switches: it grew by 220 MB then 67 MB across two equal-sized
two-tap intervals. The growth rate is clearly decelerating, not linear — consistent with
`onnxruntime`'s native memory arena not immediately returning freed allocations to the OS after
`OrtSession.close()`, rather than an unbounded per-tap leak — but "closing a session frees its RAM"
is not fully true at the OS level, only "closing a session lets that object be garbage collected."
See `meminfo.txt` for the full reasoning and exact log anchors.

**T47 could not be cross-checked against the app's own number**, because
`ITantraForegroundService.ramUsageMbFlow` — which T47 fixed to read `Debug.MemoryInfo().totalPss`
— has no consumer anywhere in `ui/` (`grep -rn "ramUsageMbFlow" app/src/main/java/com/itantra/ui/`
returns nothing). The fix is verified correct by reading the code (same basis as `dumpsys`), not by
a live before/after comparison in the app.

**Also found, incidental to this test, unrelated to Stage A:** `sherpa-onnx TTS load failed for
'gu': ... Protobuf parsing failed` (`sender_logcat.txt`, 19:57:10.562) — the Gujarati TTS voice
file on this specific phone appears corrupted or truncated. Not investigated further.

### T74 — not live-verified this run

The two-phone session ended before the walkie-talkie + AI Assistant same-session check (hold PTT in
Hindi, then use the Assistant's voice input in Hindi, count `STT('hi') loaded` lines) was run.
**Not verified on-device.** T74 remains verified only at the build level: `grep -n
"sttModule.transcribe\|ttsModule.synthesize\|audioPlayback.play"
app/src/main/java/com/itantra/ui/MainViewModel.kt` returns nothing, confirming every Assistant call
site now goes through `activeStt`/`activeTts`/`activePlayback`, which resolve to the service's
shared instances whenever it's bound. This is strong static evidence but not a substitute for the
live single-load check.

### Run 2 vs Run 3 — the clean two-phrase take

Run 3's clean take: PTT held 19:51:05.676–19:51:14.586, phrase 1 cut at 19:51:08.664 (`'हैलो कैन यू
हियर मी'`, telemetry `id=14`), phrase 2 cut at 19:51:11.864 (`'हैलो वन टू थ्री...'`, `id=15`). A
third fragment was flushed at release (`id=16`) — the tail of phrase 2 spilling over — left in the
CSV unedited but excluded from this comparison, same as Run 2 excluded its own messy takes.

| Metric | Run 2, phrase 1 | Run 3, phrase 1 | Run 2, phrase 2 | Run 3, phrase 2 |
| --- | --- | --- | --- | --- |
| Head start before release | +4393 ms | **+5046 ms** | +204 ms | **+2244 ms** |
| `stt_ms` | 573.4 ms | 883.0 ms | 565.2 ms | 481.7 ms |
| `wait_ms` | 1.0 ms | 12.0 ms | 0.9 ms | 5.1 ms |
| `rtf` | 0.2862 | 0.3003 | 0.2821 | 0.2979 |
| `tts_synth_ms` (receiver) | 255.7 ms | 326.1 ms | 230.1 ms | 290.1 ms |
| `tts_ms` (receiver) | 292.3 ms | 372.6 ms | 268.1 ms | 330.3 ms |

**Reading this honestly:** head start improved further in both phrases — likely mostly because this
take's pauses/hold time happened to be longer than Run 2's, not because Stage A changed the
pipeline's speed (T43/T73/T46/T47/T74 don't touch STT/TTS inference). Consistent with that,
`stt_ms`/`rtf`/`tts_ms` are all slightly *higher* than Run 2, not lower — plausibly because this
take was recorded immediately after ~15 language switches' worth of model loading/eviction
activity (see T46/T47 above), which may have left the device under more memory/scheduling pressure
than Run 2's comparatively quiet session. Both runs stay in the same overall performance envelope
(`rtf` 0.28–0.30, sub-second `tts_ms`); nothing here indicates a regression in the pipeline itself,
but the numbers are not perfectly clean before/after either — real device state differed between
the two sessions, and that is reported rather than smoothed over.

### What's not yet nailed down (updated)

- **T74 live confirmation** — not done this run (see above).
- **RAM after many language switches** — bounded object count, but not bounded OS-visible PSS; see
  T46/T47 above. Whether PSS eventually plateaus with more switches, or keeps growing slower and
  slower indefinitely, is not determined from three data points.
- **The Gujarati TTS voice file's corruption** — noticed, not diagnosed.
- **T43's error visibility** — the error is real but silent; not surfaced to logcat or the UI.
