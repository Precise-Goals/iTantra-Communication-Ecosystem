# TTS quantization — step-by-step prompts for a coding agent (Gemini / Antigravity)

Companion to [`TTS_QUANT_PLAN.md`](TTS_QUANT_PLAN.md). Every anchor below was copied from
`origin/main` @ `3b77832` (2026-09-26, after PR #44 G12 and PR #45 merged).

What was verified while writing this:
- **P3 dry run:** all 15 edits were applied with real manifest values. Each anchor matched exactly
  once, `compileDebugKotlin` and `testDebugUnitTest` passed, and the leftover-name check printed
  nothing. Then everything was reverted.
- **P2 inputs:** `tts_quant_fetch.py` download and extract and the `manifest` phase were run.
- **P1/P4:** the adb steps were not run; they need the phone.

**Paste the standard header, then ONE prompt.** Do them in order:

| # | Prompt | Needs you for | Blocks |
| --- | --- | --- | --- |
| P1 | T17a phone speed check (**urgent: T17a is already on main**) | phone, 10 min | — |
| P2 | T80a — build and gate the ten weight-only voices (Python only) | nothing | — |
| — | **You:** upload the ten archives to Hugging Face | HF login | P3 |
| P3 | T80b — registry + stale-voice fix (Kotlin) | on-device check | P2 + upload |
| P4 | T81 — prove it on CPH2467 | phone, ~1 h | P3 |
| P5 | Add-on for the G13 prompt: time MMS and Bengali | phone | — |
| P6 | T82 — commit the TTS intelligibility table | nothing | P2 |

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
