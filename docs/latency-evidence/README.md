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
| 15:14:17.890 | **Speech segment complete: 22400 samples** — VAD cut phrase 2, still holding |
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
logcat stream in conversation, not captured to a file. The most likely explanation for the
difference: "head start before release" is mostly a function of how long the *user* keeps
holding the button after they finish talking, not a fixed property of the pipeline — the earlier
test happened to hold PTT for several more seconds after finishing speaking, which gives the
STT queue more time to catch up before release. It is not a stable metric and the README should
not present it as one.

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
| 1 | 2343.4 | 2772 | First TTS call this process |
| 2 | 4731.4 | 816 | Warm call took *longer* than the cold call — not the STT pattern; unexplained, reported as observed rather than smoothed over. Possibly device contention (Wi-Fi Direct/Bluetooth discovery was still active during this test) |

**Honest summary:** once warm, per-phrase STT is fast and consistent (~350–400ms for a short
phrase, matching an earlier unrecorded test's ~280–330ms). TTS timing was inconsistent between
the two phrases in this run (2.3s then 4.7s) and that inconsistency is reported here rather than
cherry-picked away. The first call of each session pays a one-time model-load/JIT cost
(~2.3–3.1s combined) that later calls don't pay.

## What this does and doesn't prove

**Proven, with committed evidence, on real human speech on real hardware:**
- The NEURAL Silero VAD backend (not the energy fallback) was active on both devices for this
  test (see `VAD initialized — backend: NEURAL` in both logcat files, timestamped at cold start).
- Mid-hold phrase segmentation fires correctly against real human speech: both phrases were cut
  while PTT was still held, in spoken order, via the Mutex-serialized segment queue.
- The receiving device does receive and speak phrases sent mid-hold, not just on release.

**Not proven / open:**
- A stable "how many seconds early does the receiver speak" number — this run alone shows a
  range from -140ms to +235ms; the previously-cited 1.2–5.0s range from an uncommitted test
  cannot be independently verified from anything in this repository and should not be relied on
  until it's reproduced with committed evidence the same way this run was.
- Why receiver-side TTS timing was inconsistent between the two phrases here.
- Behavior under sustained real background noise (only tested in whatever ambient conditions the
  test room had; not a controlled noise test).
