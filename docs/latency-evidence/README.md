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
| 1 | 3100 | 3076.8 | 2439.0 | 637.8 | First STT call this process — feature_ms is inflated by one-time JIT/cache warmup, not representative |
| 2 | 1400 | 360.6 | 13.3 | 347.2 | Warm — this is the representative per-phrase number |

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

1. Implement T45 (warm-up) first, so the run measures the pipeline rather than a model load.
2. Add `TTSModule:*` to the logcat tag filter on the receiver.
3. Keep everything else identical (same phones, same two-sentence script), so the result is
   directly comparable with this run.
- Behavior under sustained real background noise (only tested in whatever ambient conditions the
  test room had; not a controlled noise test).
