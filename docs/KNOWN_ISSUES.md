# iTantra — Known issues

Bugs found in passing while testing something else, not yet turned into a spec task. Each entry
has enough evidence to reproduce and fix without re-discovering it from scratch.

---

## Malayalam TTS voice fails to load — truncated on-device file

**Found:** 2026-09-25, while phone-testing T17b (unrelated MMS voice work) on a POCO X6 5G
(`23122PCD1I`, adb serial `862f253e`).

**Symptom:**

```
W/TTSModule: sherpa-onnx TTS load failed for 'ml': OfflineTts_newFromFile: Load model from
/data/user/0/com.itantra.debug/files/models/tts/ml/ml_IN-arjun-medium.onnx failed:Protobuf parsing failed.
D/iTantraService: Warm-up stt=ml:true tts=ml:false in 3482ms
```

Reproduced twice in the same session (selecting Malayalam via the language picker both times).
Handled gracefully — `getOrLoadTts()` catches the exception and returns null, so the app doesn't
crash, it just has no Malayalam voice. This is *not* a T17b regression: T17b never touches
Malayalam, and the file predates this session by three weeks.

**Root cause (confirmed via `adb shell run-as com.itantra.debug`):**

```
$ wc -c files/models/tts/ml/ml_IN-arjun-medium.onnx
15884288 files/models/tts/ml/ml_IN-arjun-medium.onnx
```

`ModelRegistry.kt`'s `TTS_MALAYALAM` entry declares `sizeBytes = 67_222_458L`. The on-device file
is **15,884,288 bytes — 23.6% of the expected size.** It's truncated, not corrupted-but-complete:
loading it as a protobuf/ONNX graph fails outright because the file just stops partway through.
`ls -la` shows the whole `tts/ml/` directory dated **2026-09-02**, so this predates every commit
in the current run history — it's a stale artifact from early testing, not something recent broke.

**Working hypothesis, not confirmed:** `ModelDownloadManager` verifies the downloaded `.tar.bz2`
archive's SHA-256 *before* extraction (`ModelRegistry.TTS_MALAYALAM.sha256` is `null` here though —
"GitHub hasn't published a digest for this asset" — so for this specific pack there's no hash check
at all, only whatever size/completeness check `downloadFile()` does on the raw bytes). If the
*archive itself* downloaded incompletely but still decompressed far enough for `ArchiveExtractor`
to write out truncated files without erroring (bzip2 streams can partially decompress past a
truncation point), the pack would still reach `DownloadState.Downloaded` with corrupt content on
disk, and nothing would re-check it later. This needs verifying against `ModelDownloadManager.kt`
and `ArchiveExtractor.kt` before assuming it's the actual mechanism — flagging as the leading
hypothesis, not a diagnosis.

**Not investigated:** whether other already-downloaded packs (STT models, other TTS voices) on
this or other test devices have the same kind of silent truncation. `isFilePresent()` /
`isModelPresent()` checks presence, not completeness, for already-extracted directory-based packs
(TTS voices, `ESPEAK_NG_DATA`) — worth checking whether that's a real gap or intentional.

**Suggested fix direction (not implemented here):**
1. Reproduce: clear this pack's directory (`rm -rf files/models/tts/ml`), re-download from a clean
   state, and see if the fresh download is the correct 67,222,458 bytes and loads successfully. If
   yes, this confirms it's an old, interrupted download rather than a source-asset problem.
2. If it reproduces on a fresh download too, the bug is in `ModelDownloadManager`/`ArchiveExtractor`
   — likely a completeness check missing on the archive download or the extracted output.
3. Either way, consider adding a lightweight completeness check for extracted TTS voices
   (e.g. verify the `.onnx` file's size against `ModelInfo.sizeBytes` after extraction, not just
   before) so a partial extraction doesn't silently masquerade as `Downloaded`.

**Owner / file scope:** `core/download/ModelDownloadManager.kt` and `ArchiveExtractor.kt` are
Gaurav's files (`core/download/**`); `TTS_MALAYALAM`'s registry entry itself needs no change.
