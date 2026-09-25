# T11 — cross-device latency evidence (real two-phone run)

Raw artifacts from a real two-phone test of the T11 clock-offset fix
(`docs/TASKS.md` T11, `docs/WORK_SPLIT.md` §G10), pulled directly off both devices.

**Commit under test:** `90d5422` (`T11: join script for firstAudio(B) - offset - speechEnd(A)`,
branch `feature/t11-clock-offset`) — `gradlew assembleDebug` from a clean tree, both devices
uninstalled and reinstalled fresh on the resulting APK, Hindi STT/TTS models re-downloaded on
both before the test.

**Devices:**
- Phone A: `23122PCD1I` (POCO), fingerprint `POCO/garnetp_in/garnet:16/BP2A.250605.031.A3/OS3.0.301.0.WNRINXM:user/release-keys`
- Phone B: `RMX5000` (realme), fingerprint `realme/RMX5000IN/RE6066L1:16/UKQ1.231108.001/U.R4T2.1e7c4a4_6ef579_5c30fb:user/release-keys`

**Files:**
- `phoneA_poco_telemetry.csv` / `phoneB_realme_telemetry.csv` — raw `files/telemetry.csv` pulled
  via `adb shell run-as com.itantra.debug cat files/telemetry.csv` from each phone. Unedited.
  Conversation was bidirectional (both phones sent and received), so each file contains both
  send-side rows (`speech_end_epoch_ms` set) and receive-side rows (`first_audio_epoch_ms` set).
- `joined.csv` — both directions joined by `(sender_id, sequence)` via
  `model-export/t11_join_latency.py`, run twice (once per direction, swapping which CSV is
  `--sender` vs `--receiver`) and concatenated.

## Important caveat: this run measured Bluetooth, not Wi-Fi Direct — offset was never captured

The plan going in was to test Wi-Fi Direct first, then Bluetooth, and flagged in advance
(before this run) that `BluetoothRFCOMMManager.kt` has no PING/ACK exchange at all — it's
outside T11's file list — so the clock offset can only be measured over Wi-Fi Direct.

In practice, **Wi-Fi Direct never formed a group on these two phones.** `adb shell dumpsys
wifip2p` on Phone A showed `groupFormed: false` and every historical P2P group session had
`numConnectedClients=0`. `adb shell dumpsys bluetooth_manager` showed Phone A and the realme
phone repeatedly connecting/disconnecting over Bluetooth Classic through the test window, then
staying connected — i.e. the app's automatic Bluetooth fallback carried the entire conversation
below, not Wi-Fi Direct as originally intended. This looks like an OEM Wi-Fi Direct P2P quirk
(common on MIUI/Realme), not an app bug, but it wasn't root-caused further.

**Consequence:** every row in both CSVs has `peer_offset_ms = 0` and `peer_rtt_ms = 0` — not a
bug in the new code (the ACK/offset logic in `SocketTransport.kt` was never exercised because
no `SocketTransport` connection was ever established), just the known gap materializing. The
`delta_ms` column below is therefore `firstAudio(B) - speechEnd(A)` with **zero offset
subtracted** — it silently assumes phone A's and phone B's system clocks already agree, which is
exactly the assumption T11 set out to stop making.

**Numbers, reported honestly, not as a validated T11 result:**

| | A said → B heard (n=3) | B said → A heard (n=14) | Combined (n=17) |
| --- | --- | --- | --- |
| median `delta_ms` | 2835 | 791 | 1014 |
| mean `delta_ms` | 4913.7 | 2494.9 | 2921.8 |
| min / max | 2233 / 9673 | 38 / 16647 | 38 / 16647 |

The two directions' medians differ by roughly 2s, which is itself weak evidence of a real,
uncorrected clock offset between the phones (if the physical/processing delay were symmetric
in both directions and the clocks matched, the medians should be close) — a live illustration of
why T11 exists. Until the offset is actually measured on this transport, this table is not a
believable headline latency number.

## What would fix this

1. Get Wi-Fi Direct group formation actually working on these two phones and rerun (the
   originally scoped path — `SocketTransport`'s NTP exchange would then populate
   `peer_offset_ms`/`peer_rtt_ms` and `delta_ms` would be a real same-clock number), or
2. Extend the same ping/ack NTP exchange to `BluetoothRFCOMMManager.kt` (a deliberate,
   separate change — out of T11's original file list, flagged rather than done silently) so the
   fallback transport measures offset too.

Neither has been done here. This directory documents what a real run currently produces and
why the number can't be trusted yet, per the user's decision to report it with this caveat
rather than treat it as done.
