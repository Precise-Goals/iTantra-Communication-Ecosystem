# iTantra — Work Split for the Remaining Plan

> Smart India Hackathon 2026 · PS-26173 · Written 2026-09-24
> Splits everything left in [`TASKS.md`](TASKS.md) between two people, with a ready-to-paste AI prompt for every run.

> **Status 2026-09-25.** Done and merged: **G1** (Stage A, PR #21, and the Assistant removal, PR #19), **G2** (T63 PR #22, T69 PR #23), **G3** (T17b PR #24), **G4** (T23 + T29 PR #26), **S0** and **S1** (T76 PR #27).
> **Changed:** G5 no longer follows T76's own verdict. T76's decision rule was our own threshold, and it skipped the phone test. **G5 is now T77**, which measures INT8 SraVaani on the phones. Its result decides between **G5b-hybrid** (SraVaani for the nine Indic languages, IndicConformer for English, no T64) and **G5b-T64** (keep IndicConformer, export Odia). See §4 G5.

| Person | Area | AI assistant | Owns these files |
| --- | --- | --- | --- |
| **Gaurav** | Engine: audio, networking, models, Python model work | **Claude Sonnet** (Claude Code) | `core/audio/**`, `core/network/**`, `core/download/ModelRegistry.kt`, `core/telemetry/**`, `model-export/**`, `firmware/**` |
| **Sarthak** | Shell: UI, ViewModel, downloads UX, evaluation, docs | **Gemini** (Gemini CLI or any Gemini agent with file and shell access) | `ui/**` (incl. `MainViewModel.kt`), `domain/model/ModelManifest.kt`, `docs/evaluation/**`, metadata files |

**Shared file:** `core/service/ITantraForegroundService.kt`. Both people edit it. The merge order in §3 keeps edits from colliding.

---

## 1. How to use this document

- Each **run** is one sitting: paste the prompt into your AI assistant, let it work, then do the "you do" steps yourself (phone tests, merges, account set-up).
- The prompts are **self-contained**. The AI does not need this conversation. It reads the specs in the repo: [`IMPLEMENTATION_SPEC.md`](IMPLEMENTATION_SPEC.md) (spec 1) and [`IMPLEMENTATION_SPEC_2.md`](IMPLEMENTATION_SPEC_2.md) (spec 2). Every spec gives the exact text to find (**ANCHOR**), the exact text to put instead (**REPLACEMENT**), how to check it (**VERIFY**) and what not to do (**DO NOT**).
- **Only a human merges PRs.** The AI pushes a branch and opens a **draft** PR (or tells you to).
- After every merge, the other person runs `git fetch origin && git rebase origin/main` on any open branch before continuing.

---

## 2. Day 0 — do these before anyone starts (Gaurav, ~1 hour)

1. **Stage A:** Sonnet finishes the run-3 two-phone test and opens a PR for `feature/stage-a` (run **G1** below). Merge it.
2. **PR #19** (AI Assistant removal): retarget it to `main` (`gh pr edit 19 --base main`), then merge.
3. **PR #20** (this plan): merge. All prompts below read the specs from `main`, so they need this merged.
4. Give Sarthak write access to the repository if he doesn't have it, and agree a Hugging Face account/organisation for hosting model files (needed in run G3).

---

## 3. Timeline and merge order

Day 1 = the first working day after Day 0. Times are AI work plus human testing, not just code.

| Day | Gaurav (Sonnet) | Sarthak (Gemini) | Merge order that day |
| --- | --- | --- | --- |
| 0 | ✅ G1 — finish Stage A | ✅ S0 — set-up, first build | Stage A PR → PR #19 → PR #20 |
| 1 | ✅ **G2** — T63 echo gate + T69 two-way Bluetooth | ✅ **S1** — T76 SraVaani evaluation (Colab; runs all day, no app code) | T63, then T69 |
| 2 | ✅ **G3** — T17b five missing TTS voices (Colab conversion) | **S2** — T37 phone mode UI (needs T63 merged) → **S3** — T66 alert toggle (needs T69 merged) | T37, then T66 |
| 3 | ✅ G3 continued — host voices, register them | **S4** — T67 voice notes · **joint two-phone Stage B test** | T67, T17b |
| 4 | ✅ **G4** — T23 + T29 match the NeMo preprocessor, golden test | **S5** — T20 + T21 download only the selected language | T20/T21, T23/T29 |
| 5 | **G5** — T77 SraVaani INT8 phone test (Colab + both phones, ~1.5–2 d) | **S6** — T75 metadata cleanup + README pass · **T30** IndicConformer re-run (S1 note) | — |
| 5–6 | **G5b** — hybrid switch design, *or* T64 Odia export (from T77's decision) | Joins the T77 decision (rubric table, §4 G5) | — |
| 6–7 | G6 — T15 single-runtime spike (optional) · G7 — T68 ESP32 (stretch) | **S7** — T54 TTS listening test | — |
| 8+ | Dossier: T56–T61 together (§6) | | |

**Why this order:**
- **T63 lands before T37.** The echo gate is harmless on its own; phone mode without it re-transmits every received message. So the gate merges first and phone mode builds on it.
- **T69 lands before T66.** Both edit the same few lines in the service's `onSTTResult`. Doing them in order avoids a merge conflict.
- **S1 (SraVaani) starts on Day 1** because its verdict decides G5: whether Odia comes from exporting AI4Bharat's model (T64) or from switching to SraVaani.
- **Revised 2026-09-25: T77 comes before T64.** Read against the PS, S1's results make a hybrid worth testing: Odia is mandatory, the 10-language flash footprint is ~half, and non-English WER is +0.69 points. What's missing is the phone numbers. If T77's export route fails early (its Steps 1–3), start T64 the same day, because Odia STT is mandatory either way.

---

## 4. Gaurav's runs (Claude Sonnet)

Repository: `D:\Desktop\Projects\iTantra-Communication-Ecosystem`. Build: `.\gradlew.bat :app:compileDebugKotlin`, tests: `.\gradlew.bat :app:testDebugUnitTest`.

### G1 · Finish Stage A (Day 0) — ✅ done (PR #21, then PR #19)

Sonnet already has the Stage A prompt and pushed the code to `feature/stage-a`. Resume it with:

```text
Resume the Stage A task. The code for T43, T73, T46, T47, T74 is already committed on
feature/stage-a. Do only what is left: the "Run 3 — two-phone evidence" section of your
Stage A prompt (ask me when to start each phone step), then push and open a DRAFT PR from
feature/stage-a into main. Note in the PR that PR #19 (AI Assistant removal) is stacked on
this branch and makes T74 moot. Report the run 2 vs run 3 table and the four pass/fail lines.
```

**You do:** the phone steps when asked; merge the PR; then retarget and merge PR #19.

### G2 · T63 echo gate + T69 two-way Bluetooth (Day 1) — ✅ done (PR #22, PR #23)

```text
You are working on iTantra, an Android (Kotlin) offline walkie-talkie. Repo:
D:\Desktop\Projects\iTantra-Communication-Ecosystem. Two tasks, two separate branches and PRs.

Setup: git fetch origin. Read docs/IMPLEMENTATION_SPEC.md §0 "Rules for the implementing agent"
first — binding. The specs are in docs/IMPLEMENTATION_SPEC_2.md, Group G.

Task 1 — T63 "Echo gate: never capture while this phone is playing".
  git switch -c feature/t63-echo-gate origin/main
  Follow T63 exactly. Its anchors match current main. In Step 2b there is a table of extra
  counters to reset: T41 IS done, so add `speechChunkCount = 0` inside the synchronized block;
  T31 and T52 are NOT done, so skip their rows.
  Build must pass. Commit "T63: echo gate — never capture while this phone is playing".
  Push and open a DRAFT PR into main. Phone mode (T37) is not built yet, so VERIFY steps 2–3
  cannot run: do VERIFY step 1 and step 4 (PTT half-duplex) only, and say so in the PR.

Task 2 — T69 "Send on every live transport, and drop duplicates on receive".
  git switch -c feature/t69-two-way-bt origin/main
  Follow T69 exactly, including Step 2c (T66 is not done, so the broadcastAlert block still
  exists). Build must pass. Commit "T69: send on every live transport; receive dedup".
  Push and open a DRAFT PR into main.

Rules: match anchors exactly, stop and report on any mismatch; change only the listed files;
no reformatting; do not modify domain/contracts/*Callbacks.kt; never push to main.

Phone check for T69 (ask me when ready): phone A Host Beacon ON, phone B Host Beacon OFF,
connect B to A over Bluetooth only (Wi-Fi Direct off). Speak on B — A must hear it. Speak on A —
B must hear it. Save both logcats (tags iTantraService, BluetoothRFCOMM) to
docs/latency-evidence/stage-b/t69_*.txt in the T69 branch and commit them.

Final report: per task — commit, files, build/test result, anything you could not verify.
```

**You do:** the Bluetooth phone test; merge **T63 first**, then T69. Tell Sarthak both are merged (he needs them for S2 and S3).

### G3 · T17b the five missing TTS voices (Days 2–3) — ✅ done (PR #24; truncated Malayalam voice logged in `KNOWN_ISSUES.md`, PR #25)

**You do first:** create a Hugging Face model repository for hosted models (for example `<your-account>/itantra-models`), and a write token.

```text
You are working on iTantra. Repo: D:\Desktop\Projects\iTantra-Communication-Ecosystem.
Task: T17b — produce the Marathi, Kannada, Tamil, Telugu and Odia TTS voices.

Read docs/IMPLEMENTATION_SPEC.md §0 (binding rules) and the T17b section of the same file
("T17b · Produce the five missing TTS voices"). These voices do NOT exist as downloads; they must
be converted from facebook/mms-tts-{mar,kan,tam,tel,ory} with sherpa-onnx's documented MMS
conversion (https://k2-fsa.github.io/sherpa/onnx/tts/mms.html). Never guess a URL.

Part 1 — conversion (Google Colab or Linux; give me the notebook cells to run if you cannot run
them yourself). For each language: convert, then package as .tar.bz2 with EXACTLY the layout of
an existing voice (download and inspect vits-piper-hi_IN-pratham-medium.tar.bz2 first). Test
each converted voice with sherpa-onnx's Python OfflineTts on one sentence per language and save
the WAVs so I can listen. Record each archive's real size (bytes) and sha256.

Part 2 — hosting (I do this): I will upload the five archives to the Hugging Face repo
<HF_REPO_URL — I will give it to you> and confirm the resolve/main base URL.

Part 3 — app wiring, only after I give you the base URL:
  git fetch origin; git switch -c feature/t17b-mms-voices origin/main
  Follow T17b steps 5–8: a new base-URL constant, five registry entries replacing the stubs,
  TTSModule.LANGUAGE_TO_PACK codes, and the ModelRegistry class doc (licence CC-BY-NC 4.0).
  ALSO: if docs/IMPLEMENTATION_SPEC_2.md "T20 (revised 2026-09-24)" is already merged (check for
  `fun ttsPackFor` in domain/model/ModelManifest.kt), add "mr","kn","ta","te","or" to ttsPackFor —
  that file belongs to Sarthak, so keep the edit to those five mappings only.
  Add the MMS licence row (CC-BY-NC 4.0, non-commercial) to the README licence table.
  Build must pass. Commit "T17b: add MMS voices for mr/kn/ta/te/or". Push, open a DRAFT PR.

Final report: sizes and hashes table, how each voice sounded to you (if tested), files changed.
```

**You do:** listen to the five test WAVs, upload the archives, give Sonnet the URL, then run one phone test: Tamil text received → spoken in Tamil. Merge.

### G4 · T23 + T29 match the NeMo preprocessor, with a golden test (Day 4) — ✅ done (PR #26)

```text
You are working on iTantra. Repo: D:\Desktop\Projects\iTantra-Communication-Ecosystem.
Task: make the app's mel-spectrogram features match IndicConformer's training preprocessor,
and lock that in with a golden test. This is the core of the 40% Accuracy criterion.

Read docs/IMPLEMENTATION_SPEC.md §0 (binding rules), then in docs/IMPLEMENTATION_SPEC_2.md
Group E: "T23 + T29 🔬 · Match the NeMo preprocessor, with a golden test". Also read
docs/IMPLEMENTATION_SPEC.md "T24 · Add preemphasis".

Current state (already done, do not redo): radix-2 FFT at n_fft 512, Slaney mel norm, periodic
Hann window, 2^-24 log guard (see core/audio/STTModule.kt). NOT done: preemphasis (T24),
center=True padding (T26), unbiased std (T28).

1. Step 1 of T23 (Colab/Linux): load the real AI4Bharat Hindi checkpoint, print cfg.preprocessor,
   and save it to docs/evaluation/nemo_preprocessor_hi.txt. The config wins over any default
   listed in the docs.
2. Compare it field by field with STTModule.extractLogMelSpectrogram(). Write the comparison
   table into the PR description.
3. git fetch origin; git switch -c feature/t23-nemo-features origin/main
   Apply ONLY the differences the real config shows (typically preemphasis 0.97, center=True,
   unbiased std). T24's anchor may have drifted — re-read STTModule.kt and stop if it does not
   match.
4. Golden test (T29): generate the reference features for a fixed short WAV with NeMo's own
   preprocessor, commit the WAV and the reference matrix under app/src/test/resources/, and add a
   JVM unit test asserting the Kotlin features match to ~1e-3. Expose only what the test needs
   (e.g. an internal function); do not change public behaviour.
5. .\gradlew.bat :app:testDebugUnitTest must pass. One commit per task ID. Push, DRAFT PR.

Never guess config values; if you cannot load the checkpoint, stop and tell me why.
```

**You do:** run the Colab cells if Sonnet cannot; review the config comparison; merge. Then tell Sarthak so the T76 IndicConformer numbers can be taken as the app's own WER (see S1 note).

### G5 · T77 — SraVaani INT8 on the phone (Days 5–6)

*Revised 2026-09-25.* This used to follow S1's verdict directly. S1 said "keep IndicConformer", but only because of our own ≥ 3-point threshold, and it skipped the phone test. Read against the PS, the S1 numbers make a **hybrid** worth measuring before anyone exports Odia:
- SraVaani for `hi gu mr kn ml ta te bn or`.
- IndicConformer kept for `en`.

```text
You are working on iTantra. Repo: D:\Desktop\Projects\iTantra-Communication-Ecosystem.
Task: T77 — measure SraVaani 1.0 INT8 on the phones and decide: hybrid (SraVaani for the nine
Indic languages, IndicConformer for English) vs keeping IndicConformer and doing T64 for Odia.

Read docs/IMPLEMENTATION_SPEC.md §0 (binding), then docs/IMPLEMENTATION_SPEC_2.md Group I →
"T76" (including the revision note under its decision rule) and "T77 🔬 · SraVaani INT8 on the
phone". Read docs/evaluation/sravaani/README.md for the T76 numbers.

Colab part (Steps 1–4): give me the cells one at a time; I run them and paste output back.
Copy every export call from the model card, the installed package's docs/help(), or sherpa-onnx's
docs for the installed version — never guess an API. If Steps 1–3 cannot produce a graph with an
interface STTModule accepts, STOP and tell me the same day (T64 then starts).

Phone part (Step 5): no merged app code. Walk me through the baseline run, the manual swap of
SraVaani into the Hindi slot, the repeat run and the restore, one phone at a time (the cheapest
phone we have first, then the run-1..3 phone). Tell me exactly which files to pull after each run.

Then: git fetch origin; git switch -c eval/t77-sravaani-phone origin/main
Commit results_int8.csv and docs/evaluation/sravaani/phone/ (logs, CSVs, meminfo, README with the
Step 6 rubric table filled in, including numbers that favour IndicConformer). Leave the final
decision line for me and Sarthak. Commit "T77: SraVaani INT8 phone evaluation". Push; DRAFT PR.
Never push to main.
```

**You do:** run the Colab cells and the phone steps. Then **decide with Sarthak** from the Step 6 table, write the decision at the end of the README, and merge.

### G5b · Hybrid switch design *or* T64 (from T77's decision)

**If T77 adopts the hybrid** → do **not** start T64. Paste this prompt and review the design before any code:

```text
You are working on iTantra. Repo: D:\Desktop\Projects\iTantra-Communication-Ecosystem.
Task: design (no code) the hybrid STT switch decided in T77: SraVaani INT8 for
hi gu mr kn ml ta te bn or, IndicConformer (existing mirror file) for en.

Read docs/IMPLEMENTATION_SPEC.md §0, docs/IMPLEMENTATION_SPEC_2.md T76 + T77, the T77 results in
docs/evaluation/sravaani/phone/README.md, and core/audio/STTModule.kt, core/download/ModelRegistry.kt,
domain/model/ModelManifest.kt. Write docs/evaluation/sravaani/switch-design.md covering, with file and
function names from the real code:
1. One shared SraVaani pack for nine language codes: registry entry hosted under the existing
   team base URL (ITANTRA_MODELS_BASE — I upload the file), size, sha256, MIT licence row.
2. STTModule: today it loads stt_{lang}_int8.onnx per language and caches sessions by language
   code (LRU of 2, T46). Nine codes must share ONE session keyed by model file — otherwise switching
   hi -> ta loads a second ~450 MB copy. Say exactly what changes.
3. Feature pipeline: if T77 Step 2 found preprocessing differences, how STTModule selects
   per-model features, plus a second golden test (as T29) for SraVaani.
4. Downloads (T20/T21): what the compulsory pack becomes per selected language, with real sizes.
5. Warm-up (T45), telemetry (T71) and RAM (T47) implications; English <-> Indic switching cost.
6. Removal list: T64 and its Step 6 are dropped; which specs/tasks change.
7. A step-by-step implementation plan in the spec style (ANCHOR / REPLACEMENT / VERIFY / DO NOT),
   split into small PRs, with the phone test for each.
Open a DRAFT PR with only the document. Do not change app code.
```

**You do:** review the design with Sarthak (it touches his `ModelManifest.kt` and the Downloads screen), then schedule its PRs in place of T64.

**If T77 keeps IndicConformer** (or stopped early) → do T64:

```text
You are working on iTantra. Repo: D:\Desktop\Projects\iTantra-Communication-Ecosystem.
Task: T64 — Odia STT from AI4Bharat's checkpoint (the download mirror has no Odia).

Read docs/IMPLEMENTATION_SPEC.md §0 (binding), then docs/IMPLEMENTATION_SPEC_2.md Group G →
"T64 🔬 · Odia STT, and a clean CTC-only export of all ten languages". Read the T77 decision
in docs/evaluation/sravaani/phone/README.md first and quote it in the PR.

Do Steps 1–5 (Colab/Linux) for Odia only. Skip Step 6 (the other nine languages) unless I say so.
Step 7 hosting: I upload to the same Hugging Face repo used for the T17b voices and give you the
base URL — do not invent it. Step 8 app wiring:
  git fetch origin; git switch -c feature/t64-odia-stt origin/main
  Use the existing team-hosting base-URL constant from T17b if it exists.
  If "T20 (revised)" is merged (`fun sttPackFor` exists in ModelManifest.kt), add "or" -> STT_ODIA
  there instead of editing any coreTransceiverPacks list.
  Add "or" to ui/component/Languages.kt STT_LANGUAGES as "or" to "ଓଡ଼ିଆ" (one line — that file is
  Sarthak's, keep the edit minimal).
Build must pass; one commit; push; DRAFT PR. Report size, sha256, the Step 5 transcription check.
```

**You do:** host the files; phone test — pick Odia, speak Odia, see the transcription. Merge. **If T64's export fails**, fall back to SraVaani for Odia only, using the INT8 file from T77.

### G6 · T15 single ONNX runtime — investigation only (optional, Days 6–7)

```text
You are working on iTantra. Repo: D:\Desktop\Projects\iTantra-Communication-Ecosystem.
Investigation, no merge: the APK ships two ONNX runtimes — libsherpa-onnx-jni.so (23.7 MB,
statically linked runtime, used for TTS) and libonnxruntime.so (16.3 MB, onnxruntime-android,
used by core/audio/STTModule.kt and core/audio/VADModule.kt). Evaluate removing
onnxruntime-android by running STT (NeMo CTC) and Silero VAD through sherpa-onnx's own Kotlin
API from the same AAR (app/libs/sherpa-onnx-static-link-onnxruntime-1.13.7.aar).

1. List which sherpa-onnx Kotlin classes the AAR's JNI supports (inspect the AAR; the repo vendors
   only com/k2fsa/sherpa/onnx/Tts.kt). Use only APIs you can see in the AAR or sherpa-onnx's
   official Kotlin sources for that exact version — never guess signatures.
2. Write docs/evaluation/t15-single-runtime.md: what would change, what we would lose (our
   hand-written feature pipeline and telemetry stamps), expected APK saving, risks, and a
   step-by-step migration plan with tests.
3. Do not change app code in this run. Open a DRAFT PR with only the document.
```

### G7 · T68 ESP32 receiver (stretch, needs an original ESP32 board)

```text
You are working on iTantra. Repo: D:\Desktop\Projects\iTantra-Communication-Ecosystem.
Task: T68 — ESP32 receiver (the PS's "embedded device"). Read docs/IMPLEMENTATION_SPEC.md §0
and docs/IMPLEMENTATION_SPEC_2.md Group G → "T68 · (Stretch) ESP32 receiver". T69 is merged, so
its dependency is met. git fetch origin; git switch -c feature/t68-esp32 origin/main. Follow
Steps 1–2 exactly (app: SPP UUID fallback + partial-read fix; firmware file verbatim). Build must
pass; commit; push; DRAFT PR. Then guide me through flashing and the VERIFY steps.
```

---

## 5. Sarthak's runs (Gemini)

### Standard header — paste this at the top of EVERY Gemini prompt

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

### S0 · Set-up (Day 0, ~1 hour, no AI needed) — ✅ done

1. `git clone https://github.com/Precise-Goals/iTantra-Communication-Ecosystem.git` and open it in Android Studio (JDK 17).
2. Create `local.properties` with `sdk.dir=<your Android SDK path>` (Android Studio does this on first open).
3. Run `.\gradlew.bat :app:compileDebugKotlin` once and confirm it passes on `main`.
4. Install the GitHub CLI and run `gh auth login`, or plan to open PRs on the website.
5. Read [`TASKS.md`](TASKS.md) "Status" and "Do these next", and skim this document.

### S1 · T76 — Evaluate SraVaani 1.0 against the current model (Day 1, Google Colab) — ✅ done (PR #27)

> **Result:** over the 9 shared languages, WER was 19.63% for SraVaani vs 19.18% for IndicConformer. Excluding English it was 19.31% vs 19.99%. SraVaani covers Odia (21.69%), is 903 MB FP16 vs ~1.84 GB for all ten IndicConformer models, and is ~1.8× slower on desktop CPU. The rule's "keep IndicConformer" verdict is retired (see T76's revision note). The phone question moves to **T77** (G5). Sarthak joins the T77 decision.

This needs no app code, so it can start immediately. Use a Colab notebook (Python 3.10+, CPU runtime is fine; a GPU runtime only makes SraVaani faster to evaluate, not the speed comparison — the speed comparison must use CPU with 1 thread).

```text
<paste the standard header>

TASK T76 — Evaluate SraVaani 1.0 against IndicConformer. This is an evaluation only. Do NOT change
any app code.

Read docs/IMPLEMENTATION_SPEC_2.md, section "Group I — Evaluations" → "T76 🔬 · Evaluate SraVaani
1.0 against the current IndicConformer models". Follow Steps 1–6 and the decision rule exactly.

Work in a Google Colab notebook. Give me the cells one at a time; I will run them and paste the
output back to you. Important:
- Copy SraVaani's inference code from its model card (https://huggingface.co/ARTPARK-IISc/SraVaani-1.0)
  exactly. Do not write your own loader.
- For IndicConformer use the SAME files the app downloads (listed in the spec, Step 2) and
  sherpa-onnx's documented NeMo CTC recogniser. Check argument names in the installed version's
  help() before using them.
- Use FLEURS test clips exactly as Step 1 says: the first 100 clips per language, sorted by file
  name. Verify the real file paths in the dataset repository before downloading.
- Score both models with the identical normalisation in Step 4. Run speed with 1 CPU thread.
- Report every number, including ones where our current model wins.

When the numbers are in, create the branch and commit the results:
  git fetch origin; git switch -c eval/t76-sravaani origin/main
Put results.csv, the hypotheses/ files and README.md (setup, exact versions, table, verdict per
the decision rule, limitations) in docs/evaluation/sravaani/. Commit "T76: SraVaani vs IndicConformer
evaluation". Push; DRAFT PR.
```

**You do:** run the Colab cells; share the verdict with Gaurav (it decides his run G5).

> **Note for T30 (WER harness):** once Gaurav's T29 golden test passes (run G4), the app's Kotlin features match NeMo's, so the IndicConformer column from this evaluation *is* the app's WER table. Re-run only the IndicConformer part after G4 merges, and add it to the same README as "after T23/T29".
>
> **2026-09-25: G4 is merged (PR #26), so this T30 step is due now.** Finish it before the T77 decision (G5). T77's Step 6 table compares against these IndicConformer WER numbers.

### S2 · T37 — Phone mode toggle (Day 2, after T63 is merged)

```text
<paste the standard header>

TASK T37 — Wire phone mode to the UI. The problem statement requires: push-to-talk walkie-talkie,
and "if turned off it should work like a phone" (continuous listening).

Setup: git fetch origin; git switch -c feature/t37-phone-mode origin/main
First confirm the echo gate is merged: `grep -n "isSuppressed" app/src/main/java/com/itantra/core/audio/AudioCaptureModule.kt`
must print a match. If not, STOP — phone mode without the echo gate makes each phone
re-transmit what it hears from its own speaker.

Read docs/IMPLEMENTATION_SPEC.md section "T37 · Wire phone mode to the UI" and do Step 1 exactly.

Step 2 correction (the spec predates a later UI change): the spec says to place the switch "near
the existing language auto-detect toggle". That toggle no longer exists — it was replaced by a
language pill and a row of language chips (search TransceiverScreen.kt for "T72"). Put the
"Phone mode" Switch next to that language pill instead, styled like the surrounding controls.
When phone mode is on: disable the PTT button and visually de-emphasise it, and disable the
language chips (changing the model while listening would transcribe half a phrase in the wrong
language). Do not invent new colours; reuse the theme colours already used in that file.

Build must pass. Commit "T37: phone mode toggle (PTT off = continuous listening)". Push; DRAFT PR.
```

**You do (with Gaurav, two phones):** both phones in phone mode. Speak one sentence on phone A; phone B speaks it; **nothing comes back to A** (this proves T63 and T37 together). Then speak on B: A hears it. Record the result in the PR. Merge.

### S3 · T66 — "Next message is an ALERT" toggle (Day 2, after T69 is merged)

```text
<paste the standard header>

TASK T66 (minimum version) — let the user send an alert-type message. The receiving side
(alarm volume, non-interruptible playback) already works; nothing in the UI can send an alert.

Setup: git fetch origin; git switch -c feature/t66-alert-toggle origin/main
Confirm T69 is merged: `grep -n "private fun transmit" app/src/main/java/com/itantra/core/service/ITantraForegroundService.kt`
must print a match. If not, STOP and tell me.

Read docs/IMPLEMENTATION_SPEC_2.md section "T66 🎨 · SOS: send alerts from the UI, and show
received alerts". At the top of that section is a table titled "Minimum version — do only this".
Follow that table EXACTLY:
- Step 1a: SKIP.  Steps 1b, 1c, 1d: DO.
- Step 2a: do ONLY the _alertArmed / alertArmed / setAlertArmed parts. Skip _incomingAlert,
  incomingAlert, dismissAlert, sendPresetAlert and the two imports.
- Step 2b: SKIP.  Step 2c: DO.
- Step 3: ONLY the "Next message is an ALERT" toggle on TransceiverScreen.kt, styled like the
  existing controls there. No SOS button, no presets dialog, no receiver dialog.

Build must pass. Commit "T66: next-message-is-an-ALERT toggle". Push; DRAFT PR.
```

**You do (two phones):** toggle on, hold PTT, speak: the other phone plays it at alarm volume. The next message is normal. Merge.

### S4 · T67 — Voice notes (Day 3)

```text
<paste the standard header>

TASK T67 — received speech is kept as a replayable voice note (the problem statement says the
TTS output "will be played as a voice note").

Setup: git fetch origin; git switch -c feature/t67-voice-notes origin/main
Read docs/IMPLEMENTATION_SPEC_2.md → "T67 🎨 · Received speech is kept as a replayable voice note".

- Step 1: create core/audio/VoiceNoteStore.kt exactly as given.
- Step 2: the current onTextReceived has ONE `synth` and ONE
  `audioPlayback.play(synth.samples, synth.sampleRate, ...)` call — so use the FIRST row of the
  table (T13 done, T40 not). The received language variable in that function is `targetLang`;
  you do not need it for this snippet.
- Step 3: the anchor line `fun setTTSLanguage(lang: String) { ttsLanguage = lang }` may already
  be followed by lines added by T66 (`sendNextAsAlert`). Insert the new function directly after
  the setTTSLanguage line and leave T66's lines in place.
- Steps 4 and 5 as written. Step 5 is UI: add a small play icon on RECEIVED message bubbles using
  an icon style already used in TransceiverScreen.kt.

Build must pass. Commit "T67: keep received speech as replayable voice notes". Push; DRAFT PR.
```

**You do:** receive three messages; check `adb shell run-as com.itantra.debug ls files/voicenotes`; tap play on the second one. Merge.

**Joint Stage B test (Day 3, both of you, ~1 hour):** on the same two phones as run 3, test phone mode (no echo), two-way Bluetooth, an alert, and voice-note replay. Save the logcats under `docs/latency-evidence/stage-b/` with a short README of pass/fail lines, in one small PR.

### S5 · T20 + T21 — Download only the selected language (Day 4)

```text
<paste the standard header>

TASK T20 (revised) + T21 — the compulsory download is 2.18 GB (all 9 speech models + all voices).
Make it only the selected language (~210–280 MB), and let the user add more languages.

Setup: git fetch origin; git switch -c feature/t20-per-language-download origin/main

Part 1 — T20. Read docs/IMPLEMENTATION_SPEC_2.md → "T20 (revised 2026-09-24) · Download only
the selected language". IGNORE the older "T20 · Make the core bundle a language pair" in Group D
(it is superseded). Do Steps 1–4 exactly. Step 1 deliberately removes the zero-argument
coreTransceiverPacks(); the build will then fail at any call site you missed — the spec lists all
five. Build must pass. Commit "T20: download only the selected language".

Part 2 — T21 (UI). Read docs/IMPLEMENTATION_SPEC_2.md → "T21 🎨 · Per-language download selection".
On DownloadsScreen.kt:
- The main "Download the Pack" card now downloads ModelPack.coreTransceiverPacks(selectedLanguage).
  Update its title and description text to say which language it is for (use the language's
  native name from IndicLanguage.fromCode(selectedLanguage).nativeName). Do not claim languages
  that have no model or voice.
- Below the compulsory list, add an "Other languages" section listing every other language's STT
  pack (ModelPack.sttPackFor(code)) and voice (ModelPack.ttsPackFor(code)), using the existing
  ModelPackRowItem component, so a user can download extra voices for messages from other
  languages. Languages whose voice is null show "Voice not available yet" as plain text.
- Reuse existing components and colours only.
Build must pass. Commit "T21: per-language download selection". Push; DRAFT PR.
```

**You do:** clear app data (`adb shell pm clear com.itantra.debug`), pick Tamil: the button shows about 200 MB and the Transceiver unlocks after it; pick Hindi: about 270 MB. Merge.

### S6 · T75 — Remove stale claims from metadata + README pass (Day 5)

```text
<paste the standard header>

TASK T75 — remove claims the app does not actually make true.
Setup: git fetch origin; git switch -c docs/t75-metadata origin/main
Files: app/src/main/assets/app_metadata.json, app_metadata.json (repo root copy — keep both
identical), app/src/main/java/com/itantra/domain/model/AppMetadata.kt, and the
<meta-data android:name="com.itantra.*"> entries in app/src/main/AndroidManifest.xml.

The source of truth is README.md. For every value in those files, check it against README.md and
the code. Examples known to be wrong: "8 - 16 kbps Opus narrowband encoded streaming" (the app
sends text, not audio), "AI4Bharat IndicTTS VITS ... ~14MB per language" (voices are
sherpa-onnx Piper/Coqui/Mimic3 VITS, ~20–108 MB), "Silero VAD v4" (it is the v5+ model pinned to
release v6.2.3), "IndicConformer ... ~150MB" (~197 MB per language).
Replace each wrong value with the true one from README.md, or delete the entry if nothing true
can be said. Do not add new claims. Check the JSON is still valid
(python -c "import json;json.load(open('app_metadata.json',encoding='utf-8'))").
First grep the Kotlin code for each AppMetadata constant you change, and do not remove one that
is used. Build must pass. Commit "T75: remove stale claims from app metadata". Push; DRAFT PR,
with a table in the PR body: old value → new value → where the truth comes from.
```

### S7 · T54 — TTS listening test (Days 6–7, mostly people, not code)

```text
<paste the standard header>

TASK T54 — prepare a small, honest listening test for the TTS voices (the Accuracy criterion
scores "human legibility and flow"). No app code changes.

Setup: git fetch origin; git switch -c eval/t54-listening-test origin/main
Create docs/evaluation/tts-listening/ with:
1. protocol.md — 5 native speakers per language; each hears 10 sentences per voice generated by
   the app; they rate intelligibility (1–5) and naturalness (1–5) and write down any word they
   could not understand. Explain that raters must not see the text first.
2. sentences/<lang>.txt — 10 short sentences per available voice (hi, gu, ml, bn, en, plus
   mr, kn, ta, te, or once their voices exist). Use simple alert and everyday sentences.
   Mark every sentence "NEEDS NATIVE-SPEAKER CHECK" — you must not claim they are correct.
3. results_template.csv — columns: rater_id, lang, sentence_id, intelligibility, naturalness,
   words_not_understood.
4. README.md — how to generate the audio from the app (send each sentence as a message and save
   the voice note, T67) and how to compute the mean scores.
Commit "T54: TTS listening test protocol". Push; DRAFT PR.
```

**You do:** get the sentences checked by native speakers; recruit raters; collect results into the CSV.

---

## 6. Together at the end — the dossier (Days 8+)

| Task | Owner | What |
| --- | --- | --- |
| T56 | both | Full scorecard run on the target phone: all languages, 3 repeats, medians |
| T57 | Gaurav | Before/after table from the evidence runs (runs 1–3, Stage B) |
| T58 | both | Rehearse the two-phone demo, including a deliberate failure and recovery |
| T59 | Sarthak | Slide deck built around the scorecard; every number labelled measured / calculated / cited |
| T60 | Sarthak | Final README and docs pass: no claim without a committed file behind it |
| T61 | Sarthak | Backup demo video |

Ask each AI for these in the same style: read `TASKS.md` for the task, quote only numbers from committed files, open a draft PR.

---

## 7. If something goes wrong

- **An ANCHOR does not match:** someone changed the file since the spec was written. Do not force it. `git log -p -- <file>` shows what changed; update the spec in a small docs PR, or ask the other person.
- **Merge conflict in `ITantraForegroundService.kt`:** the person merging second resolves it. Keep both sides' changes: they touch different functions.
- **Build breaks after a merge:** revert the last merge on a branch (`git revert -m 1 <merge-commit>`), open a PR, and tell the other person before doing anything else.
- **A phone test fails:** write the failure in the PR ("what I saw", logcat file). A failing test that is written down is worth more than an untested pass.
