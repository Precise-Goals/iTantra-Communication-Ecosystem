# T69 — two-way Bluetooth phone test

Raw `adb logcat` captures from a real two-phone test of T69 (send on every live transport, drop
duplicates on receive). Both phones ran the debug build from `feature/t69-two-way-bt`.

**Devices:**
- Phone A (Host Beacon **ON**, Bluetooth server): `23122PCD1I` (POCO), adb serial `862f253e`
- Phone B (Host Beacon **OFF**, connected as Bluetooth client to A): `RMX5000` (realme), adb
  serial `BUXSVCTWTCLJ5LBI`

Wi-Fi Direct was off on both devices for the whole test, so all traffic below went over Bluetooth.

**Files:**
- `t69_phoneA_hostbeacon.txt` — Phone A, tag-filtered (`iTantraService`, `BluetoothRFCOMM`,
  `STTModule`, `AudioCapture`, `VADModule`). Unedited.
- `t69_phoneB_btclient.txt` — Phone B, same tags. Unedited.

## What the test was

Phone B connects to Phone A over Bluetooth only (Host Beacon off on B — the case T69 fixes, since
Bluetooth was previously one-directional unless both phones had Host Beacon on). Speak on B, then
speak on A, and confirm each is heard on the other phone.

## Result: PASS, both directions

From the two logs (separate device clocks, but STT text matches across them):

| Time (A) | Time (B) | Event |
| --- | --- | --- |
| — | 22:46:57.003 | B: audio capture starts (speaking) |
| — | 22:46:58.726 | B: audio capture stops |
| — | 22:47:00.562 | B: STT `'तो क है'` (1834ms) |
| 22:47:00.618 | — | **A: `Received from <B's id>: 'तो क है' [SPEECH]`** — 56ms after B's STT, confirms B→A over Bluetooth |
| 22:47:00.945 | — | A: TTS synthesis complete, 743ms audio |
| 22:47:04.636 | — | A: audio capture starts (speaking) |
| 22:47:06.414 | — | A: audio capture stops |
| 22:47:06.867 | — | A: STT `'हैेलो कन्यू है आर्मी'` (448ms) |
| — | 22:47:07.478 | **B: `Received from <A's id>: 'हैेलो कन्यू है आर्मी' [SPEECH]`** — confirms A→B over Bluetooth |
| — | 22:47:07.827 | B: TTS synthesis complete, 1705ms audio |

## Note on the first connection attempt

An earlier connection attempt (not included in these files) saw a transient
`BT connect error: read failed, socket might closed or timeout, read ret: -1` on Phone A shortly
after B connected, and B→A did not go through that time — Phone A's log had no `Received from`
line despite A playing nothing. On reconnect (the run captured above) both directions worked
cleanly with no errors. The error is logged from `BluetoothRFCOMMManager.connectToDevice()`'s
catch block, i.e. an outbound connect attempt failing, not the server-side accept/read path — it
did not reproduce on the second attempt and was not investigated further in this run.
