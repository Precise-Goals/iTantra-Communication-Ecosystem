# TTS quantization — step-by-step prompts for a coding agent (Gemini / Antigravity)

Companion to [`TTS_QUANT_PLAN.md`](TTS_QUANT_PLAN.md). Every anchor below was copied from
`origin/main` @ `3b77832` (2026-09-26, after PR #44 G12 and PR #45 merged).

What was verified while writing this:
- **P3 dry run:** all 15 edits were applied with real manifest values. Each anchor matched exactly
  once, `compileDebugKotlin` and `testDebugUnitTest` passed, and the leftover-name check printed
  nothing. Then everything was reverted.
- **P2 inputs:** `tts_quant_fetch.py` download and extract and the `manifest` phase were run.
- **P1/P4:** the adb steps were not run; they need the phone.
- **P8 + P9 dry run (T85–T87, added later):** all edits were applied to main @ `dca41b0`.
  `compileDebugKotlin` passed, `testDebugUnitTest` passed (6/6 new splitter tests), and everything
  was reverted. The edit text in P8/P9 was generated from that same compiled source.

**Paste the standard header, then ONE prompt.** Do them in order:

| # | Prompt | Needs you for | Blocks |
| --- | --- | --- | --- |
| P1 | T17a phone speed check — **done** (PR #47: 2.0× slower) | phone, 10 min | — |
| P2 | T80a — build and gate the ten weight-only voices (Python only) | nothing | — |
| — | **You:** upload the ten archives to Hugging Face | HF login | P3 |
| P3 | T80b — registry + stale-voice fix (Kotlin) | on-device check | P2 + upload |
| P4 | T81 — prove it on CPH2467 | phone, ~1 h | P3 |
| P5 | Add-on for the G13 prompt: time MMS and Bengali | phone | — |
| P6 | T82 — commit the TTS intelligibility table | nothing | P2 |
| P7 | T84 — revert T17a to FP32 voices (only if T80b is > 2 days away) | phone check | — |
| P8 | T85 + T86 — streamed playback + clause splitter (unused until P9) | nothing | — |
| P9 | T87 — stream TTS clause by clause on receive | phone check | P8 merged |
| P10 | T88 — tune TTS threads from phone measurements | phone, ~1 h | P9 merged |
| P11 | Re-measure the Latency rows of `SCORECARD.md` | phone | P7/P3, P9, P10 |

**Not delegated:** T35 (numbers to words in ten languages). A model will invent number words for
Tamil or Odia, so that needs native-speaker-checked tables and its own spec first. T83 (DSP) is
parked (plan §2.3c).

---

## Standard header — paste at the top of EVERY prompt

```text
ROLE AND RULES (read before doing anything)
You are editing iTantra, an Android app written in Kotlin (Jetpack Compose UI). It is an offline
walkie-talkie: speech -> text (on-device STT) -> small Protobuf message over Wi-Fi Direct or
Bluetooth -> text -> speech (on-device TTS) on the other phone.

The repository contains exact written specifications. You MUST follow them literally:
1. Before editing, read docs/IMPLEMENTATION_SPEC.md section "0. Rules for the implementing agent".
   Those rules are binding.
2. Each task gives an ANCHOR (exact current code) and a REPLACEMENT (exact new code). Find the
   ANCHOR text character for character. Before editing, print the file name and line number
   where you found it. If it does not match exactly, STOP and tell me — do not adapt the change
   to similar-looking code, and do not "fix" the spec.
3. Change ONLY the files the task lists. Never rewrite or re-indent a whole file. Never delete
   comments. Do not rename, reorder or "clean up" anything that is not in the task.
4. Never invent an API, URL, file path, constant, dependency or version. If the task does not
   give it to you, stop and ask me.
5. After each task run: ./gradlew :app:compileDebugKotlin   (Windows: .\gradlew.bat ...)
   It must pass. If it fails twice, stop, revert that task (git checkout -- <files>) and show me
   the error.
6. Show me `git diff` before every commit. One commit per task; the message starts with the task
   ID (e.g. "T37: ...").
7. Never commit to main, never force-push, never merge. Push your branch and open a DRAFT pull
   request (gh pr create --draft), or tell me the branch name so I can open it.
8. At the end, report: branch, commit hash, files changed, build result, and anything you could
   not verify.
```

---

## P1 · T17a phone speed check (urgent)

```text
TASK T17a-CHECK — measure whether the int8 Piper voices now on main make TTS slower on the phone.
NO CODE CHANGES. You only build, install, run adb and write one results file.

Background: PR #44 switched Hindi/Malayalam/English to sherpa-onnx "-int8" voices. On desktop they
measured 3.3–3.8x slower than FP32 (docs/TTS_QUANT_PLAN.md §2.2). IMPORTANT: a phone that already
had the FP32 Hindi voice KEEPS it after updating the app, because the app only checks that
files/models/tts/hi/ contains an .onnx file. So you must delete that folder, or you will measure
the old voice.

Phone: CPH2467 (ask me to confirm `adb devices -l`). Package: com.itantra.debug.
Setup: git fetch origin; git switch -c eval/t17a-phone-check origin/main

Run A — int8 (current main):
  .\gradlew.bat assembleDebug
  adb install -r -d app\build\outputs\apk\debug\app-debug.apk
  adb shell run-as com.itantra.debug rm -rf files/models/tts/hi
  adb shell run-as com.itantra.debug rm files/telemetry.csv
  adb shell run-as com.itantra.debug ls -la files/models/tts/     (show me: hi must be gone)
Ask me to open the app and tap "Download the Pack" (it re-fetches only Hindi TTS). Then check:
  adb shell run-as com.itantra.debug ls -la files/models/tts/hi
  It must contain hi_IN-pratham-medium.onnx of about 18–21 MB. If it is ~63 MB, STOP: that is the
  old voice.
Ask me to receive 10 Hindi messages from the second phone. Then:
  adb shell run-as com.itantra.debug cat files/telemetry.csv > docs/latency-evidence/t17a-check/int8_telemetry.csv

Run B — FP32 (the commit before G12 merged):
  git switch --detach 9d6c724
  .\gradlew.bat assembleDebug ; adb install -r -d app\build\outputs\apk\debug\app-debug.apk
  adb shell run-as com.itantra.debug rm -rf files/models/tts/hi
  adb shell run-as com.itantra.debug rm files/telemetry.csv
Same as run A, but the file must be ~63 MB. Save as fp32_telemetry.csv (same folder).
  git switch eval/t17a-phone-check

Write docs/latency-evidence/t17a-check/README.md. It must contain:
  - the device;
  - both .onnx sizes you saw;
  - per run, the median of tts_synth_ms, tts_audio_ms, tts_ms, and tts_synth_ms / tts_audio_ms
    (RTF), over rows with tts_ms > 0.
Compute the medians with a short Python snippet, and paste the snippet into the README.
Verdict rule:
  int8 RTF >= 2x FP32 RTF  -> write "T17a SLOWS TTS — revert in favour of T80";
  otherwise write "no phone slowdown".
Commit "T17a-check: int8 vs FP32 Piper TTS speed on CPH2467". Push; DRAFT PR.
```

---

## P2 · T80a — build and gate the ten weight-only voices (Python only)

```text
TASK T80a — convert all ten TTS voices to weight-only INT8, check them on desktop, and produce a
manifest. NO APP CODE CHANGES. The only repo change is one results file (step 6).
Read docs/TTS_QUANT_PLAN.md §2.3b and §4 T80 first.

Run this on Gaurav's PC only. It needs the T17b MMS exports in
D:\Desktop\Projects\iTantra-Communication-Ecosystem\model-export\mms_work\pkg\vits-mms-{mar,kan,tam,tel,ory}\
If that folder is missing, STOP and tell me.

WORK = D:\itantra-tts-build    (outside the repo; never commit anything from it)

1. Python env (these exact versions were tested):
     python -m venv D:\itantra-tts-build\venv
     D:\itantra-tts-build\venv\Scripts\python.exe -m pip install numpy==2.0.2 onnx==1.19.1 onnxruntime==1.19.2 sherpa-onnx==1.13.8 scipy==1.13.1
   Below, PY means D:\itantra-tts-build\venv\Scripts\python.exe
2. Branch: git fetch origin; git switch -c feature/t80a-wo-voices origin/main
3. Inputs (≈2 GB, resumable — re-run it if it fails):
     PY model-export\tts_quant_fetch.py D:\itantra-tts-build
   Must end with FETCH_DONE.
4. For EACH lang in: hi ml en gu bn mr kn ta te or
     PY model-export\tts_quant_all_langs.py convert <lang> D:\itantra-tts-build . D:\Desktop\Projects\iTantra-Communication-Ecosystem\model-export\mms_work\pkg
   Then, for each lang ONE AT A TIME, with nothing else running on the PC:
     PY model-export\tts_quant_all_langs.py rtf <lang> D:\itantra-tts-build . <same pkg path>
   Then for each lang except "or" (these may run 2–4 at a time in separate terminals):
     PY model-export\tts_quant_all_langs.py cer <lang> D:\itantra-tts-build . <same pkg path>
   Every result is appended to D:\itantra-tts-build\results.jsonl.
5. GATE. Read results.jsonl and make this table for me:
     lang | wo_archive_mb | fp32_rtf_2thr | wo_rtf_2thr | fp32_noise0 | wo_noise0
   A language PASSES if BOTH hold:
     - wo_rtf_2thr <= 1.10 * fp32_rtf_2thr
     - wo_noise0 <= fp32_noise0 + 1.0   ("or" has no cer row: it passes on RTF alone; say so)
   If ANY language fails, STOP and show me. Do not continue to step 6.
   For reference, the values measured 2026-09-26 are in docs/TTS_QUANT_PLAN.md §2.3b. Yours should
   be close. Bengali will differ slightly, because it now keeps its duration predictor FP32.
6. PY model-export\tts_quant_all_langs.py manifest D:\itantra-tts-build
   This writes D:\itantra-tts-build\wo\manifest.csv (lang, file, bytes, sha256), one row per
   voice, 10 rows. Copy it to docs/evaluation/tts-quant/t80_manifest.csv, and copy results.jsonl
   to docs/evaluation/tts-quant/t80_results.jsonl.
   Commit "T80a: weight-only INT8 voices built and gated (manifest)". Push; DRAFT PR.
7. Tell me: "Ready to upload". List the 10 files from D:\itantra-tts-build\wo\*-wo-int8.tar.bz2
   with their sizes. DO NOT upload anything yourself.
```

### You · upload (after P2 says "Ready to upload")

```powershell
# once: pip install -U huggingface_hub ; huggingface-cli login   (write token for Chgauravpc)
cd D:\itantra-tts-build\wo
huggingface-cli upload Chgauravpc/itantra . . --include "*-wo-int8.tar.bz2"
```

Then check that one of them downloads, and that its sha256 matches `manifest.csv`:
`curl -L -o t.tar.bz2 https://huggingface.co/Chgauravpc/itantra/resolve/main/vits-mms-kan-wo-int8.tar.bz2`
followed by `certutil -hashfile t.tar.bz2 SHA256`.

---

## P3 · T80b — point the registry at the new voices + fix stale-voice upgrades (Kotlin)

```text
TASK T80b — switch all ten TTS voices to the team-hosted weight-only INT8 archives, and make
already-installed phones replace their old voice.
Files: app/src/main/java/com/itantra/core/download/ModelRegistry.kt,
       app/src/main/java/com/itantra/core/download/ModelDownloadManager.kt   (ONLY these two)
Precondition: docs/evaluation/tts-quant/t80_manifest.csv exists on origin/main or on the P2
branch, and I have told you the upload is done. If either is not true, STOP.
Setup: git fetch origin; git switch -c feature/t80b-wo-registry origin/main
(if P2's PR is not merged yet: git merge --no-ff origin/feature/t80a-wo-voices, and tell me)

VALUES: every <BYTES_xx> and <SHA_xx> below comes from t80_manifest.csv, row lang=xx.
Write the bytes with an underscore every 3 digits and an L suffix (25632458 -> 25_632_458L). The
sha is the 64-hex string, in quotes. Before editing, print the 10 rows you will use. Never type a
value that is not in that file.

--- ModelRegistry.kt ---

Edit 1. ANCHOR (the mmsTtsInfo helper, whole block):
    /**
     * A team-hosted MMS voice (T17b): converted from `facebook/mms-tts-<lang>` with sherpa-onnx's
     * documented MMS conversion (no espeak-ng phonemization — these are character-frontend
     * models), hosted on our own repo because no prebuilt source exists for these languages.
     * Same shape as [sherpaTtsInfo] but pointing at [ITANTRA_MODELS_BASE] instead.
     */
    private fun mmsTtsInfo(
REPLACEMENT:
    /**
     * A team-hosted TTS voice on [ITANTRA_MODELS_BASE]. Originally the T17b MMS voices (converted
     * from `facebook/mms-tts-<lang>`, character frontend); since T80, all ten voices, as weight-only
     * INT8 re-exports of the FP32 originals (docs/TTS_QUANT_PLAN.md: −75 % download, same speed,
     * no measurable CER loss). Same shape as [sherpaTtsInfo] but pointing at our repo.
     */
    private fun mmsTtsInfo(
(The function name stays — do not rename it.)

Edit 2. ANCHOR:
        // Real, verified voices (sherpa-onnx tts-models release, checked 2026-09-02):
REPLACEMENT:
        // T80: every voice is a team-hosted weight-only INT8 re-export (model-export/
        // tts_quant_all_langs.py); sizes and sha256 from docs/evaluation/tts-quant/t80_manifest.csv.
        // Originals: sherpa-onnx tts-models release (hi/ml/en/gu/bn) and T17b MMS exports.

Edits 3–12: one per voice. For each, the ANCHOR is the whole entry as it is on main, and the
REPLACEMENT keeps the same ModelPack and lang code. It changes the helper to mmsTtsInfo, the file
name to the manifest's, and sizeBytes/sha256 to the manifest's.
  ANCHOR (Hindi):
        ModelPack.TTS_HINDI to sherpaTtsInfo(
            ModelPack.TTS_HINDI, "vits-piper-hi_IN-pratham-medium-int8.tar.bz2", "hi",
            sizeBytes = 20_987_965L,
            sha256 = "20f568c56207c13b9a0d9478aec8b7d1449122e618aeebc7211f6abc942b58b7"
        ),
  REPLACEMENT:
        ModelPack.TTS_HINDI to mmsTtsInfo(
            ModelPack.TTS_HINDI, "vits-piper-hi_IN-pratham-medium-wo-int8.tar.bz2", "hi",
            sizeBytes = <BYTES_hi>,
            sha256 = "<SHA_hi>"
        ),
  Malayalam: anchor "vits-piper-ml_IN-arjun-medium-int8.tar.bz2" / 20_838_242L / "4d0b2a58…9832"
             -> "vits-piper-ml_IN-arjun-medium-wo-int8.tar.bz2", <BYTES_ml>, "<SHA_ml>"
  English:   anchor "vits-piper-en_US-lessac-low-int8.tar.bz2" / 21_070_568L / "af63fbe6…d5e2"
             -> "vits-piper-en_US-lessac-low-wo-int8.tar.bz2", <BYTES_en>, "<SHA_en>"
  Gujarati: the ANCHOR is these 6 lines:
        ModelPack.TTS_GUJARATI to sherpaTtsInfo(
            // Only known source: Mimic3/CMU-Indic — lower "low" quality tier, no higher tier exists.
            ModelPack.TTS_GUJARATI, "vits-mimic3-gu_IN-cmu-indic_low.tar.bz2", "gu",
            sizeBytes = 79_992_004L,
            sha256 = null // GitHub hasn't published a digest for this asset
        ),
     REPLACEMENT: keep the comment line; change the helper to mmsTtsInfo, the file to
     "vits-mimic3-gu_IN-cmu-indic_low-wo-int8.tar.bz2", sizeBytes = <BYTES_gu>, and
     sha256 = "<SHA_gu>" (the trailing "// GitHub hasn't…" comment goes).
  Bengali:  anchor "vits-coqui-bn-custom_female.tar.bz2" / 108_053_596L / sha256 = null // GitHub…
             -> mmsTtsInfo, "vits-coqui-bn-custom_female-wo-int8.tar.bz2", <BYTES_bn>, "<SHA_bn>"
  The five MMS entries (TTS_KANNADA "vits-mms-kan", TTS_TAMIL "vits-mms-tam", TTS_TELUGU "vits-mms-tel",
  TTS_MARATHI "vits-mms-mar", TTS_ODIA "vits-mms-ory") already use mmsTtsInfo. Change only the
  file name ("vits-mms-kan.tar.bz2" -> "vits-mms-kan-wo-int8.tar.bz2", etc.), sizeBytes and
  sha256 — from the manifest rows kn, ta, te, mr, or.
  Keep the existing "T17b … Licence: CC-BY-NC 4.0" comment block above them unchanged.
After the edits, `sherpaTtsInfo` is unused. LEAVE IT (a warning is fine). Do not delete it.

--- ModelDownloadManager.kt ---

Why: the app treats a voice as installed when files/models/tts/<lang>/ has any .onnx. Phones that
already have a voice would therefore never fetch the new one. And extracting on top of an old
folder can leave two .onnx files, of which TTSModule loads the first. The fix: write a marker
with the archive name, treat a TTS folder whose marker does not match the registry as absent, and
empty the folder before extracting. It applies ONLY to folders under "tts/". SraVaani
("stt/sravaani") and espeak-ng-data must be left alone, or every phone re-downloads ~400 MB.

Edit 13. ANCHOR:
    private val TAG = "ModelDownloadManager"
REPLACEMENT:
    private val TAG = "ModelDownloadManager"
    /** T80: written into each extracted TTS voice folder; holds the archive name it came from. */
    private val SOURCE_MARKER = ".source_archive"

Edit 14. ANCHOR:
            if (pack != ModelPack.ESPEAK_NG_DATA && files.none { it.extension == "onnx" }) return false
            return true
REPLACEMENT:
            if (pack != ModelPack.ESPEAK_NG_DATA && files.none { it.extension == "onnx" }) return false
            // T80: a TTS voice extracted from a different archive than the registry now names is
            // stale — report it absent so "Download the Pack" fetches the new one. No marker means
            // it was extracted before T80, which is also stale. Only tts/ folders: SraVaani and
            // espeak-ng-data are untouched.
            if (info.extractDirName.startsWith("tts/") &&
                File(dir, SOURCE_MARKER).takeIf { it.exists() }?.readText()?.trim() != info.fileName
            ) return false
            return true

Edit 15. ANCHOR:
                try {
                    ArchiveExtractor.extractTarBz2(archiveFile, destDir, excludePrefixes)
                    archiveFile.delete()
                } catch (e: Exception) {
REPLACEMENT:
                try {
                    // T80: start TTS voices from an empty folder, so an old voice's .onnx can never
                    // sit next to the new one (TTSModule loads the first .onnx it finds).
                    val isVoice = info.extractDirName.startsWith("tts/")
                    if (isVoice) destDir.deleteRecursively()
                    ArchiveExtractor.extractTarBz2(archiveFile, destDir, excludePrefixes)
                    if (isVoice) File(destDir, SOURCE_MARKER).writeText(info.fileName)
                    archiveFile.delete()
                } catch (e: Exception) {

Build: .\gradlew.bat :app:compileDebugKotlin, then .\gradlew.bat :app:testDebugUnitTest (both must pass).
Then run this check; it must print nothing (no old int8 or FP32 voice names left):
  Select-String -Path app\src\main\java\com\itantra\core\download\ModelRegistry.kt -Pattern '"vits-.*\.tar\.bz2"' | Where-Object { $_.Line -notmatch 'wo-int8' }
Commit "T80b: team-hosted weight-only INT8 voices + replace stale installed voices". Push; DRAFT PR.
The PR body must contain the old -> new size table for all ten voices, from the manifest.

On-device check (ask me; phone CPH2467, WITHOUT clearing app data first):
  1. Install the APK over the existing app. Open it: every TTS language must now show as
     not downloaded, while STT (SraVaani) and espeak-ng-data still show as downloaded. Show me
     the download screen state.
  2. Tap "Download the Pack". Then:
       adb shell run-as com.itantra.debug ls -la files/models/tts/hi files/models/tts/mr
       adb shell run-as com.itantra.debug cat files/models/tts/hi/.source_archive
     Each folder must have exactly ONE .onnx file, and the marker must equal the registry file name.
  3. Receive one Hindi and one Marathi message: both must be spoken.
  Put the outputs in the PR body.
```

---

## P4 · T81 — prove it on CPH2467

```text
TASK T81 — measure the T80 voices on the target phone against FP32. NO CODE CHANGES.
Precondition: T80b is merged to main (ask me). Phone CPH2467 plus a second phone to send messages.
Setup: git fetch origin; git switch -c eval/t81-wo-on-phone origin/main
Output folder: docs/latency-evidence/t81/

For each build — FP32 = `git switch --detach 9d6c724`, WO = `git switch eval/t81-wo-on-phone`:
  .\gradlew.bat assembleDebug ; adb install -r -d app\build\outputs\apk\debug\app-debug.apk
  adb shell run-as com.itantra.debug rm -rf files/models/tts
  adb shell run-as com.itantra.debug rm files/telemetry.csv
  Ask me to tap "Download the Pack", then to receive 10 Hindi and 10 Marathi messages (the same
  10 sentences each time: write them to docs/latency-evidence/t81/sentences.txt first).
  Right after the last message, with both voices still loaded:
    adb shell dumpsys meminfo com.itantra.debug > docs/latency-evidence/t81/meminfo_<build>.txt
    adb shell run-as com.itantra.debug cat files/telemetry.csv > docs/latency-evidence/t81/telemetry_<build>.csv
    adb shell run-as com.itantra.debug ls -la files/models/tts/hi files/models/tts/mr > docs/latency-evidence/t81/files_<build>.txt

README.md in the same folder. It must contain:
  - a table: build | lang | n | median tts_synth_ms | median tts_audio_ms | RTF (= synth/audio) |
    median tts_ms | TOTAL PSS (MB);
  - the Python snippet you used for the medians (rows with tts_ms > 0, grouped by lang);
  - a verdict per the plan: ADOPT if WO RTF is within 10 % of FP32 and TOTAL PSS within +30 MB.
    If PSS rose by more, write "RAM REGRESSION — see T81 fallback in TTS_QUANT_PLAN.md". Do not try
    the fallback yourself.
Also ask one native Hindi speaker and one native Marathi speaker to listen to 5 WO messages each,
and record "understood: yes/no per sentence". Put their answers in the README; never make them up.
Commit "T81: weight-only voices on CPH2467 (RTF, PSS)". Push; DRAFT PR.
```

---

## P5 · Add-on for the G13 baseline prompt

Append this block to the G13 prompt, after its Step 4. The G13 prompt is in the chat, or you can
rebuild it from `WORK_SPLIT.md` §G13.

```text
Step 4b — TTS for the slow voices (the plan's biggest open risk, TTS_QUANT_PLAN.md §2.1):
  On the target phone, receive 10 Marathi (MMS) and 10 Bengali (Coqui) messages as well as the
  Hindi ones. Save them to telemetry_mr.csv / telemetry_bn.csv (clear telemetry.csv between
  languages). baseline.csv gets one extra row per language: "TTS RTF <lang>" = median
  tts_synth_ms / tts_audio_ms, plus "text received -> first audio <lang>" = median tts_ms.
  If any RTF is > 1.0, write in the README: "TTS slower than real time for <lang>".
```

---

## P6 · T82 — commit the TTS intelligibility table

```text
TASK T82 — turn the T80a results into the committed TTS intelligibility table. NO APP CODE.
Input: docs/evaluation/tts-quant/t80_results.jsonl (from P2) and docs/evaluation/sravaani/results.csv (T76).
Setup: git fetch origin; git switch -c eval/t82-tts-cer origin/main

Write model-export/t82_table.py (standard library only). It reads the "cer" rows of
t80_results.jsonl, and writes docs/evaluation/tts-quant/roundtrip_cer.csv with columns:
  lang, n, fp32_cer_noise0, wo_cer_noise0, fp32_cer_appnoise_mean, wo_cer_appnoise_mean,
  asr_cer_on_human_speech
The last column comes from docs/evaluation/sravaani/results.csv. Its header is
`model,lang,n_clips,wer,cer,rtf,load_ms,size_mb,notes`. Take the row where model == "indicconformer"
and lang == "<code>_in" (English is "en_us"). `cer` is a fraction there (0.0463), so multiply by 100.
If a language has no such row, leave the value empty and say so. Do not compute or guess one.
Add a short section to docs/evaluation/tts-quant/README.md titled "T82 — TTS intelligibility",
which shows the table and states: "TTS intelligibility is reported as ASR round-trip CER. STOI is
not computable for TTS (no aligned human reference)."
Do NOT edit PRD.md. Tell me the wording to use instead of "STOI > 0.85", and I will decide.
Commit "T82: TTS round-trip CER table". Push; DRAFT PR.
```

---

## P7 · T84 — revert T17a (interim FP32 voices)

Use it only if T80b (P3) will not merge within about 2 days. Otherwise skip it; T80 replaces these
same three entries.

```text
TASK T84 — put Hindi, Malayalam and English TTS back on the FP32 voices (T17a made TTS 2.0x slower
on the phone: docs/latency-evidence/t17a-check/, PR #47).
File: app/src/main/java/com/itantra/core/download/ModelRegistry.kt   (ONLY this file)
Setup: git fetch origin; git switch -c fix/t84-revert-t17a origin/main

Edit 1. ANCHOR:
            ModelPack.TTS_HINDI, "vits-piper-hi_IN-pratham-medium-int8.tar.bz2", "hi",
            sizeBytes = 20_987_965L,
            sha256 = "20f568c56207c13b9a0d9478aec8b7d1449122e618aeebc7211f6abc942b58b7"
REPLACEMENT:
            ModelPack.TTS_HINDI, "vits-piper-hi_IN-pratham-medium.tar.bz2", "hi",
            sizeBytes = 67_238_438L,
            sha256 = "2084d321e1d2752f2b64ed3012ba27751df01a80da46f52920098cdcb7e35648"

Edit 2. ANCHOR:
            ModelPack.TTS_MALAYALAM, "vits-piper-ml_IN-arjun-medium-int8.tar.bz2", "ml",
            sizeBytes = 20_838_242L,
            sha256 = "4d0b2a58157604b589cddc54884ffd2618b097d15155b254c34bd21830659832"
REPLACEMENT:
            ModelPack.TTS_MALAYALAM, "vits-piper-ml_IN-arjun-medium.tar.bz2", "ml",
            sizeBytes = 67_222_458L,
            sha256 = "3058d098e8b1ffcdd6069e96b1d492f319333235912a627c309c7c54cea59acf"

Edit 3. ANCHOR:
            ModelPack.TTS_ENGLISH, "vits-piper-en_US-lessac-low-int8.tar.bz2", "en",
            sizeBytes = 21_070_568L,
            sha256 = "af63fbe60d8bdcfccdee61ba057304a11dfc077145da383d4d351ec3c594d5e2"
REPLACEMENT:
            ModelPack.TTS_ENGLISH, "vits-piper-en_US-lessac-low.tar.bz2", "en",
            sizeBytes = 67_097_098L,
            sha256 = "8fb427b8637334072ee5723d72fa418c45bfdd4b7deebeacdf2938662618c1cb"

(These FP32 values are exactly what main had before PR #44; check with
 git show 9d6c724:app/src/main/java/com/itantra/core/download/ModelRegistry.kt | Select-String pratham,arjun,lessac)
Build. Commit "T84: revert T17a — FP32 Piper voices (int8 was 2.0x slower on the phone)". Push; DRAFT PR.
IMPORTANT for testing: phones that already have the int8 voice keep it (the app only checks that
files/models/tts/<lang>/ has an .onnx). Before testing, run:
  adb shell run-as com.itantra.debug rm -rf files/models/tts/hi files/models/tts/ml files/models/tts/en
and re-download. Write that into the PR body.
```

---

## P8 · T85 + T86 — streamed playback + clause splitter

```text
TASK T85 + T86 — add a streamed playback path and a clause splitter. NOTHING CALLS THEM YET (that is
P9/T87), so app behaviour must not change in this PR.
Files: app/src/main/java/com/itantra/core/audio/AudioPlaybackManager.kt (T85),
       app/src/main/java/com/itantra/core/audio/TextPostProcessor.kt (T86),
       NEW app/src/test/java/com/itantra/TextPostProcessorSplitUnitTest.kt (T86)
Setup: git fetch origin; git switch -c feature/t85-t86-stream-split origin/main
These edits were applied to main @ dca41b0, compiled, and unit-tested (6/6 new tests passing) before
this prompt was written. If an anchor does not match exactly, STOP.

Edit T85.1 — app/src/main/java/com/itantra/core/audio/AudioPlaybackManager.kt
ANCHOR:
    private class PlaybackItem(
        val waveform: FloatArray,
        val sampleRate: Int,
        val isAlert: Boolean,
        val onFirstFrame: (() -> Unit)?
    )
REPLACEMENT:
    private class PlaybackItem(
        val waveform: FloatArray,
        val sampleRate: Int,
        val isAlert: Boolean,
        val onFirstFrame: (() -> Unit)?,
        /** T85: when set, [waveform] is ignored and these chunks are played as they arrive. */
        val chunks: kotlinx.coroutines.channels.ReceiveChannel<StreamChunk>? = null
    )

    /** T85: one synthesized piece of a streamed message, at the voice's native rate. */
    class StreamChunk(val samples: FloatArray, val sampleRate: Int)

Edit T85.2 — app/src/main/java/com/itantra/core/audio/AudioPlaybackManager.kt
ANCHOR:
                    if (item.isAlert) playAlert(item.waveform, item.sampleRate, item.onFirstFrame)
                    else playNormal(item.waveform, item.sampleRate, item.onFirstFrame)
REPLACEMENT:
                    val chunks = item.chunks
                    if (chunks != null) playStream(chunks, item.onFirstFrame)
                    else if (item.isAlert) playAlert(item.waveform, item.sampleRate, item.onFirstFrame)
                    else playNormal(item.waveform, item.sampleRate, item.onFirstFrame)

Edit T85.3 — app/src/main/java/com/itantra/core/audio/AudioPlaybackManager.kt
ANCHOR:
        playbackQueue.trySend(PlaybackItem(waveform, sampleRate, isAlert, onFirstFrame))
    }
REPLACEMENT:
        playbackQueue.trySend(PlaybackItem(waveform, sampleRate, isAlert, onFirstFrame))
    }

    /**
     * T85: queue a streamed (non-alert) playback and return the channel to feed it. Send each
     * synthesized piece as a [StreamChunk] the moment it exists, then close() the channel — the
     * caller MUST close it (use try/finally), or the playback queue waits forever. Pieces play
     * back-to-back on one AudioTrack, so the first clause is heard while later ones are still
     * being synthesized. Ordering with other messages is the same as [play].
     */
    fun playStreaming(onFirstFrame: (() -> Unit)? = null): kotlinx.coroutines.channels.SendChannel<StreamChunk> {
        val ch = kotlinx.coroutines.channels.Channel<StreamChunk>(kotlinx.coroutines.channels.Channel.UNLIMITED)
        playbackQueue.trySend(PlaybackItem(FloatArray(0), 0, false, onFirstFrame, ch))
        return ch
    }

    /** T85: the AudioTrack is built on the first non-empty chunk (its rate is the voice's). */
    private suspend fun playStream(
        chunks: kotlinx.coroutines.channels.ReceiveChannel<StreamChunk>,
        onFirstFrame: (() -> Unit)?
    ) {
        var track: AudioTrack? = null
        var focused = false
        try {
            for (chunk in chunks) {
                if (chunk.samples.isEmpty()) continue
                val t = track ?: run {
                    requestAudioFocus(isAlert = false)
                    focused = true
                    val bufferSize = AudioTrack.getMinBufferSize(chunk.sampleRate, CHANNEL_CONFIG, AUDIO_FORMAT)
                    AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA) // same as playNormal
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        .setAudioFormat(
                            android.media.AudioFormat.Builder()
                                .setEncoding(AUDIO_FORMAT)
                                .setSampleRate(chunk.sampleRate)
                                .setChannelMask(CHANNEL_CONFIG)
                                .build()
                        )
                        .setBufferSizeInBytes(bufferSize)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build()
                        .also {
                            track = it
                            it.setVolume(1.0f)
                            it.play()
                            onFirstFrame?.invoke()
                        }
                }
                t.write(chunk.samples, 0, chunk.samples.size, AudioTrack.WRITE_BLOCKING)
            }
            track?.stop()
        } finally {
            track?.release()
            if (focused) releaseAudioFocus()
        }
    }

Edit T86.1 — app/src/main/java/com/itantra/core/audio/TextPostProcessor.kt
ANCHOR:
    private val TERMINATORS = charArrayOf('.', '!', '?', '।', '॥')
REPLACEMENT:
    private val TERMINATORS = charArrayOf('.', '!', '?', '।', '॥')

    /** T86: a clause for chunked TTS ends after any of these. */
    private val CLAUSE_END = charArrayOf('.', '!', '?', '।', '॥', ',', ';', ':')
    private const val MAX_CLAUSE_WORDS = 10
    private const val MIN_CLAUSE_WORDS = 3

    /**
     * Splits a message into clauses for chunked TTS (T86), so playback can start after the first
     * clause instead of the whole message. Cuts after clause punctuation, and after
     * [MAX_CLAUSE_WORDS] words when there is none. A later piece shorter than [MIN_CLAUSE_WORDS]
     * words is merged into the one before it (a lone word saves no time and sounds clipped); a
     * short FIRST piece is kept, because it is what makes the first audio fast.
     * Joining the result with single spaces gives back the whitespace-normalised input.
     */
    fun splitForTts(text: String): List<String> {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val pieces = mutableListOf(mutableListOf<String>())
        for (w in words) {
            pieces.last().add(w)
            if (w.last() in CLAUSE_END || pieces.last().size >= MAX_CLAUSE_WORDS) pieces.add(mutableListOf())
        }
        val out = mutableListOf<MutableList<String>>()
        for (p in pieces) {
            if (p.isEmpty()) continue
            if (out.isNotEmpty() && p.size < MIN_CLAUSE_WORDS) out.last().addAll(p) else out.add(p)
        }
        return out.map { it.joinToString(" ") }
    }

New file app/src/test/java/com/itantra/TextPostProcessorSplitUnitTest.kt, exactly:
package com.itantra

import com.itantra.core.audio.TextPostProcessor
import org.junit.Assert.assertEquals
import org.junit.Test

/** T86: clause splitting for chunked TTS. */
class TextPostProcessorSplitUnitTest {

    @Test fun splitsAtComma() = assertEquals(
        listOf("इमारत में आग लगी है,", "तुरंत बाहर निकलें।"),
        TextPostProcessor.splitForTts("इमारत में आग लगी है, तुरंत बाहर निकलें।"))

    @Test fun singleClauseStaysWhole() = assertEquals(
        listOf("सभी जवान अपनी वर्तमान स्थिति की रिपोर्ट करें।"),
        TextPostProcessor.splitForTts("सभी जवान अपनी वर्तमान स्थिति की रिपोर्ट करें।"))

    @Test fun shortTailMergesIntoPrevious() = assertEquals(
        listOf("हाँ, ठीक है।"),
        TextPostProcessor.splitForTts("हाँ, ठीक है।"))

    @Test fun longUnpunctuatedCutsEveryTenWords() {
        val words = (1..23).map { "w$it" }
        val out = TextPostProcessor.splitForTts(words.joinToString(" "))
        assertEquals(listOf(10, 10, 3), out.map { it.split(" ").size })
    }

    @Test fun blankGivesEmpty() = assertEquals(emptyList<String>(), TextPostProcessor.splitForTts("   "))

    @Test fun joinReproducesNormalisedInput() {
        val s = "  बचाव दल   रास्ते में है, कृपया शांत रहें।  "
        assertEquals("बचाव दल रास्ते में है, कृपया शांत रहें।", TextPostProcessor.splitForTts(s).joinToString(" "))
    }
}

Build: .\gradlew.bat :app:compileDebugKotlin, then .\gradlew.bat :app:testDebugUnitTest. Both must
pass, and the report must show TextPostProcessorSplitUnitTest with tests="6" failures="0"
(app\build\test-results\testDebugUnitTest\TEST-com.itantra.TextPostProcessorSplitUnitTest.xml).
Two commits: "T85: streamed playback path (unused until T87)" and "T86: clause splitter for chunked
TTS". Push; DRAFT PR.
```

---

## P9 · T87 — chunked synthesis + stream the receive path

```text
TASK T87 — synthesize incoming (non-alert) messages clause by clause and play each clause as soon as
it exists.
Precondition: the T85 + T86 PR is merged to main (ask me). If `grep -n "fun playStreaming"
app/src/main/java/com/itantra/core/audio/AudioPlaybackManager.kt` finds nothing, STOP.
Files: app/src/main/java/com/itantra/core/audio/TTSModule.kt,
       app/src/main/java/com/itantra/core/service/ITantraForegroundService.kt   (ONLY these two)
Setup: git fetch origin; git switch -c feature/t87-chunked-tts origin/main
These edits were compiled and unit-tested together with P8 on main @ dca41b0.

Edit T87.1 — app/src/main/java/com/itantra/core/audio/TTSModule.kt
ANCHOR:
    suspend fun synthesize(text: String, languageCode: String): SynthesisResult? =
        ttsLock.withLock { synthesizeUnlocked(text, languageCode) }
REPLACEMENT:
    suspend fun synthesize(text: String, languageCode: String): SynthesisResult? =
        ttsLock.withLock { synthesizeUnlocked(text, languageCode) }

    /**
     * T87: synthesize [text] clause by clause ([TextPostProcessor.splitForTts]) and hand each
     * clause's PCM to [onChunk] as soon as it exists, so playback can start after the first clause.
     * Holds [ttsLock] for the whole message, so two messages never interleave. Returns all clauses
     * concatenated (for the voice note and telemetry), or null if any clause failed.
     */
    suspend fun synthesizeChunked(
        text: String,
        languageCode: String,
        onChunk: (SynthesisResult) -> Unit
    ): SynthesisResult? = ttsLock.withLock {
        val clauses = TextPostProcessor.splitForTts(text)
        val parts = ArrayList<FloatArray>(clauses.size)
        var rate = 0
        for (clause in clauses) {
            val r = synthesizeUnlocked(clause, languageCode) ?: return@withLock null
            rate = r.sampleRate
            parts.add(r.samples)
            onChunk(r)
        }
        if (parts.isEmpty()) return@withLock null
        val all = FloatArray(parts.sumOf { it.size })
        var off = 0
        for (p in parts) {
            p.copyInto(all, off)
            off += p.size
        }
        SynthesisResult(all, rate)
    }

Edit T87.2 — app/src/main/java/com/itantra/core/service/ITantraForegroundService.kt
ANCHOR:
                val synth = ttsModule.synthesize(message.text, targetLang)
                utt.ttsDoneNs = System.nanoTime()
REPLACEMENT:
                // T87: normal messages are synthesized clause by clause and streamed to the speaker.
                // Alerts keep the one-shot path below (alarm stream + volume override in playAlert).
                if (!isAlert) {
                    streamToSpeaker(message, targetLang, utt)
                    _pipelineStage.value = PipelineStage.IDLE
                    return@launch
                }
                val synth = ttsModule.synthesize(message.text, targetLang)
                utt.ttsDoneNs = System.nanoTime()

Edit T87.3 — app/src/main/java/com/itantra/core/service/ITantraForegroundService.kt
ANCHOR:
    fun setSTTLanguage(lang: String) { sttLanguage = lang; audioCaptureModule.currentLanguage = lang }
REPLACEMENT:
    /**
     * T87: synthesize [message] clause by clause and play each clause as soon as it is ready.
     * Telemetry keeps its meaning: tts_ms = text received -> first audio frame (now the first
     * clause), tts_synth_ms = text received -> last clause synthesized. The row is written once
     * both have happened, or right after synthesis if no audio will ever play.
     */
    private suspend fun streamToSpeaker(message: TransceiverMessage, targetLang: String, utt: Telemetry.Utterance) {
        val pending = java.util.concurrent.atomic.AtomicInteger(2) // first frame + synthesis done
        val finish = { if (pending.decrementAndGet() == 0) Telemetry.complete(this@ITantraForegroundService, utt) }
        val stream = audioPlayback.playStreaming {
            utt.firstAudioFrameNs = System.nanoTime()
            finish()
        }
        var anyChunk = false
        val synth = try {
            ttsModule.synthesizeChunked(message.text, targetLang) { part ->
                anyChunk = true
                stream.trySend(com.itantra.core.audio.AudioPlaybackManager.StreamChunk(part.samples, part.sampleRate))
            }
        } finally {
            stream.close() // always: the playback queue waits for this channel to close
        }
        utt.ttsDoneNs = System.nanoTime()
        if (synth != null) {
            utt.ttsAudioDurationMs = synth.samples.size * 1000L / synth.sampleRate
            // Keep it as a replayable voice note (T67). Off the playback path.
            val noteSamples = synth.samples
            val noteRate = synth.sampleRate
            serviceScope.launch(Dispatchers.IO) {
                com.itantra.core.audio.VoiceNoteStore.save(
                    this@ITantraForegroundService, message.senderId, message.sequence, noteSamples, noteRate
                )
            }
        }
        finish()                  // synthesis side is done
        if (!anyChunk) finish()   // nothing will play, so no first frame will ever arrive
    }

    fun setSTTLanguage(lang: String) { sttLanguage = lang; audioCaptureModule.currentLanguage = lang }

Build + unit tests must pass. Commit "T87: stream TTS clause by clause on receive". Push; DRAFT PR.

On-device check (ask me; phone CPH2467, second phone sends; clear telemetry.csv first):
  1. Receive the same 10 Hindi sentences as P1 (docs/latency-evidence/t17a-check/README.md §2).
     Every sentence must be heard complete, in order, with no clause missing or repeated.
  2. Receive 3 Marathi sentences, and send one ALERT (the alert must still use the alarm path).
  3. Save telemetry.csv as docs/latency-evidence/t87/telemetry.csv, and write README.md with
     median tts_ms and tts_synth_ms next to P1's numbers for the same voice. Verdict: first audio
     (tts_ms) median must drop; note any audible gap between clauses honestly.
  4. Replay one received message from the voice-note list (T67): it must be the whole sentence.
```

---

## P10 · T88 — tune TTS threads on the phone

```text
TASK T88 — pick numThreads for TTS from phone measurements. One literal changes, but only after
measuring.
File: app/src/main/java/com/itantra/core/audio/TTSModule.kt   (ONLY this file)
Precondition: T87 merged (ask me). Phone CPH2467.
Setup: git fetch origin; git switch -c perf/t88-tts-threads origin/main

The ANCHOR (it occurs once, inside getOrLoadTts's OfflineTtsModelConfig):
                    numThreads = 2,
For N in 2, 3, 4:
  - set that line to `numThreads = N,` (only while measuring — do not commit N=3/4 yet)
  - .\gradlew.bat assembleDebug ; adb install -r -d app\build\outputs\apk\debug\app-debug.apk
  - adb shell run-as com.itantra.debug rm files/telemetry.csv
  - receive the 10 P1 Hindi sentences + 5 Marathi sentences. While the Marathi ones arrive, ALSO
    speak into this phone (PTT) so STT runs at the same time
  - save telemetry.csv as docs/latency-evidence/t88/telemetry_threads<N>.csv
Table in docs/latency-evidence/t88/README.md: N | lang | median tts_ms | median tts_synth_ms |
median RTF (synth/audio) | median stt_ms (from the rows where you spoke).
Choose the smallest N whose TTS RTF is within 5 % of the best, AND whose stt_ms is not more than
10 % worse than N=2. Commit only that value with the comment:
  // T88: measured on CPH2467, docs/latency-evidence/t88/README.md
Commit "T88: TTS numThreads = <N> (measured)". Push; DRAFT PR. If N=2 wins, commit only the README.
```

---

## P11 · Re-take the latency row of the scorecard

```text
TASK P11 — after T84 or T80, T87 and T88 are merged, re-measure the Latency rows of docs/SCORECARD.md on
CPH2467. Use G13's procedure (Wi-Fi Direct for the end-to-end number; clocks synchronised by T11).
Update ONLY the "Now" cells of the Latency table and their source paths, changing the label to
Measured. Keep the old value in brackets, e.g. "612 ms (was 1,868)". Never edit a cell you did not
measure. Commit "Scorecard: latency re-measured after T84-T88". Push; DRAFT PR.
```
