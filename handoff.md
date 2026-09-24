# Session handoff

> Written 2026-09-24, at the end of the latency-pipeline session (PRs #13, #14, #15).
> Read this before starting new work — it says what's actually true on `main` right now,
> what's still open, and a few real gotchas that cost time to rediscover.

## Update — 2026-09-24 night (read this first)

- **PR #17 is merged.** Stage A (T43, T73, T46, T47, T74) is on `feature/stage-a`, pushed, **no PR yet**; its run-3 two-phone evidence is still to be captured.
- **The AI Assistant is removed** in **PR #19**, stacked on `feature/stage-a`: merge Stage A first, then retarget #19 to `main`. Debug APK 99.3 MB → 60.8 MB. Tag `assistant-last` keeps the old code.
- **Scope decisions** (`docs/IMPROVEMENT_PLAN.md` §10.16): no translation, no Android/Google voice packs, no audio-instead-of-text; SOS is trimmed to a "next message is an ALERT" toggle (T66 minimum).
- **Next:** Stage B in `docs/TASKS.md` → "Do these next". **Who does what, with prompts:** `docs/WORK_SPLIT.md` (Gaurav + Claude Sonnet, Sarthak + Gemini).

## Update — 2026-09-24 evening

- **PR #17** (`feature/latency-pipeline-2`, open) implements **T70, T45, T72, T71** and adds run-2
  evidence in `docs/latency-evidence/run2/`. Reviewed against the code and the raw files: phrase 1
  was ready 4.39 s before PTT release with warm models. A human needs to merge it.
- The three "not implemented" bullets below for **T45, T71 and T70 are done in PR #17**.
- **Next work** is in [`docs/TASKS.md`](docs/TASKS.md) → "Do these next": Stage A (T43, T73,
  T46+T47, T74 — bugs run 2 exposed), then Stage B (phone mode + echo gate, two-way Bluetooth,
  SOS, voice notes), then Stage C (10/10 languages, per-language downloads, WER). Stage A specs are
  in `docs/IMPLEMENTATION_SPEC_2.md` Group H, anchored to PR #17's code.
- New gotcha: on a **first install** the VAD starts before its model has downloaded and stays on
  the energy detector until a force-stop (T73 fixes it). Force-stop and relaunch after the first
  download before any measurement.

## What's on `main` right now

T41 (adaptive endpointing), T38 (playback queue), T65 (phrase-level pipelining), and T62
(Silero VAD repair) are merged and verified on real hardware. See:
- [`README.md`](README.md)'s "Phrase-level pipelining & latency" section and Core Capabilities
  table for the current, accurate status of each.
- [`docs/latency-evidence/`](docs/latency-evidence/) — raw logcat + telemetry from an actual
  two-phone test, tied to a specific commit and APK. Read `docs/latency-evidence/README.md`'s
  "what this does and doesn't prove" section before citing any number from this work — several
  numbers reported earlier in the session turned out to be wrong (see "Mistakes made and
  corrected" below) and the surviving doc reflects the corrected reading.
- [`model-export/check_silero.py`](model-export/check_silero.py) +
  `model-export/check_silero_results.txt` — the VAD diagnosis, verified for real against the
  pinned model with a synthesized voice sample (not a human recording — say so if you extend it).

## Open items (real, not yet done)

These are called out honestly in the README's Roadmap and in `docs/latency-evidence/README.md`'s
"what's not yet nailed down" — don't assume they're done just because adjacent work is:

- **T39 (alert queue pre-emption)** — NOT implemented. Playback is FIFO-serialized (T38); an
  ALERT message does not jump ahead of queued normal messages. Verify this is still true in
  `AudioPlaybackManager.kt` before claiming otherwise — it was originally mis-documented as done
  and had to be corrected mid-session.
- **T45 (preload STT/TTS models at service start)** — ✅ done in PR #17 (see update above). Original note: The committed latency-evidence
  test's head-start-before-release number was largely erased by a one-time ~2.3s STT model load on
  the first phrase of the session. `docs/latency-evidence/README.md` has a *projected* (not
  measured) head-start with warm models (~1–2.5s) — re-run the same test after T45 lands to turn
  that projection into a real number.
- **T71 (telemetry timestamp fix)** — ✅ done in PR #17. Original note: Right now `feature_ms`/`stt_ms` are
  measured from `captureEndNs`, which is stamped at *queue dequeue* time (post-T65), not at the
  original VAD cut — so per-phrase `stt_ms` hides any time a phrase spent waiting in the queue
  behind an earlier phrase. Also, `feature_ms` on a session's first STT call includes the full
  `ensureLoaded()` model-load time, not just feature extraction. Both are documented, neither is
  fixed.
- **T70 (possible unsynchronized double voice load in `TTSModule`)** — ✅ lock added in PR #17; run 2 shows one voice load for 10 messages. Original note: Two
  phrases' TTS synthesis finished suspiciously close together (92ms apart) despite arriving 374ms
  apart in one test run. `TTSModule`'s model cache is an unsynchronized map with no lock — this
  might mean two concurrent messages in the same new language both trigger a full voice load. Add
  a `TTSModule` tag to the next capture's logcat filter to confirm or rule this out.
- **T50 (streaming/chunked STT inference)** — intentionally not started this session, per the
  original task's explicit instruction to measure T65 first. Given the committed evidence (steady-state
  STT ~350–400ms per short phrase), it's unclear whether T50 is still worth the complexity — that's
  a call for whoever picks this up next, not a foregone conclusion either way.
- Everything else in the README's Roadmap section (phone mode + echo gate, bidirectional
  Bluetooth, voice notes, SOS controls, Odia STT, remaining TTS voices) — untouched this session.

## Mistakes made and corrected this session — read before trusting any number

This session got two things wrong and corrected them; worth knowing so the same mistakes aren't
repeated:

1. **Reported latency numbers from chat, not from committed evidence.** Early in the session, a
   "receiver speaks 1.2–5.0s before PTT release" figure was reported based on reading a live
   logcat stream in conversation. It was never captured to a file. A later, rigorous re-test
   (rebuilt from a clean commit, cold-started both devices, evidence committed) showed the real
   number is much smaller and not stable (ranged from -140ms to +235ms) — dominated by a one-time
   model-load cost, not by pipeline speed. **Lesson: don't report a number as fact unless it's
   backed by something committed to the repo that another session can independently check.**
2. **Guessed at telemetry anomalies instead of reading the code.** The first version of
   `docs/latency-evidence/README.md` attributed an inflated `feature_ms` to "JIT warmup" and an
   inconsistent receiver `tts_ms` to "possibly device contention" — both guesses, both wrong. PR
   #13 (merged) traced both through the actual code (`Telemetry.kt`, `ITantraForegroundService.kt`)
   and found the real causes: `feature_ms` includes the STT model load because of where
   `captureEndNs` is stamped relative to `ensureLoaded()`, and the receiver's `tts_ms` is
   `received → first audio played`, so it includes playback-queue wait, not synthesis time.
   **Lesson: when telemetry looks weird, trace the actual timestamp-stamping code before writing
   an explanation — a plausible-sounding guess is not the same as a checked one.**

## Useful gotchas from this session (device/tooling, not code)

- **Wi-Fi Direct needs the Wi-Fi radio ON**, even though it never joins an access point.
  `discoverPeers`/`createGroup` fail with reason code `2` (BUSY) if Wi-Fi is off — check
  `adb shell settings get global wifi_on` if pairing silently fails.
- **ColorOS (Oppo/Realme) devices have a separate "Install via USB" toggle** in Developer options,
  distinct from USB debugging. If off, `adb install` hangs indefinitely with no on-screen dialog —
  looks identical to a stuck/frozen install. Toggle it on if an install to such a device hangs.
- **`adb install -r` preserves app data** (permissions, peer pairing) across reinstalls, but
  `am force-stop` + relaunch is required to actually reinitialize in-memory singletons like
  `VADModule`'s backend — just relaunching an already-running app brings the existing process to
  the foreground without reinitializing anything.
- **Two phones' clocks are not synchronized.** Cross-device timestamp deltas from logcat need a
  measured offset (`adb shell date "+%s.%N"` on both, several samples) and even then carry
  meaningful uncertainty (~tens of ms). Same-device-clock deltas (e.g. sender-side "segment cut" →
  "STT done") don't have this problem — prefer them when they answer the question.
- **This device's logcat ring buffer rotates fast** under heavy OEM background service noise
  (backup services, HWC composer spam, etc.) — a live capture that's stopped and re-dumped even a
  minute later can come back completely empty. Stream continuously to a file for the whole test
  window rather than dumping after the fact.
- **`git log A..B --name-only` can be misleading in this repo's history** — there's a merge commit
  (`be91c0e`, "Merge origin/main using 'ours' strategy") that deliberately discarded a parallel
  rewrite from a teammate while keeping both parent lineages in the DAG. Commit-list-based file
  diffs will list files from that discarded branch as if they changed; use `git diff A B`
  (tree-based) for an accurate picture of what's actually different.
- **`main` had independently rewritten the same README sections** this session was also editing
  (VAD status, Roadmap), describing the pre-fix state. If touching README.md again, check
  `git log` on `main` for recent unrelated doc work before assuming your branch's version is the
  only one that changed.

## Branch/PR state as of this handoff

- `main` has everything: PRs #12, #13, #15 are merged (#12 and #13 by the user directly via
  `gh pr merge`, after Claude Code's auto-mode classifier denied merging PRs without review — that
  restriction will apply again next session too, so plan on the human merging, not the agent).
- PR #14 was closed unmerged (superseded by #15, which targeted `main` directly rather than the
  stale `docs/comprehensive-documentation` branch).
- `feature/latency-pipeline`, `feature/latency-pipeline-main`, and `docs/latency-evidence-corrections`
  still exist as remote branches post-merge; safe to delete if the repo owner wants to tidy up, not
  done automatically this session.
