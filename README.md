# 🛰️ iTantra — Universal Communication Ecosystem

> **Smart India Hackathon 2026 | Problem Statement PS-26173**
> Indian Multilingual TTS & STT Aided Neural Transceiver Radio Access for Low Bitrate Links

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Platform: Android](https://img.shields.io/badge/Platform-Android%208.0+-green.svg)](https://developer.android.com)
[![Version](https://img.shields.io/badge/version-2.0.0-informational.svg)]()
[![Languages: 9 Indic (STT) / 5 (TTS)](https://img.shields.io/badge/Languages-9%20STT%20%2F%205%20TTS-orange.svg)]()

> **This document describes the actual current implementation** (branch `fix/model-pipeline-integrity`, built on real on-device ADB testing — see [Development History](#-development-history--real-fixes) below). It supersedes [`PRD.md`](PRD.md) and [`team.md`](team.md), which capture the *original* sprint-planning design and are kept only for historical reference — several things they describe (a bundled-in-APK model set, a dedicated SOS screen, `IndicTTS VITS` for all 10 languages) were changed or dropped during real implementation, and are called out explicitly below.

---

## 📖 What is iTantra?

**iTantra** is an Android app for offline, AI-powered multilingual voice communication over ad-hoc **Wi-Fi Direct** and **Bluetooth Classic (RFCOMM)** mesh links — built for disaster zones, tactical field operations, and rural areas without GSM infrastructure.

Instead of streaming raw audio, iTantra converts speech to text **on-device** using AI4Bharat's IndicConformer STT models, sends the transcript as a small Protobuf message (~50–300 bytes) over the mesh link, and re-synthesizes it as natural speech on the receiving device using real espeak‑ng‑phonemized VITS voices (via [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx)). A separate, fully offline **AI Tactical Assistant** (on-device Phi‑3‑mini via llama.cpp, with an honest keyword-matching fallback when the LLM isn't loaded) answers first-aid/disaster-protocol queries and can translate across languages.

**Internet is used exactly once** — for the initial one-time download of neural model files from Hugging Face / GitHub release CDNs. Every P2P communication, VAD/STT/TTS inference, and AI Assistant query afterwards is 100% on-device with zero network calls.

### What actually works today

| Capability | Status |
| --- | --- |
| Wi-Fi Direct discovery, group formation, TCP transport (port 8765) | ✅ Real, verified two-device end-to-end |
| Bluetooth Classic RFCOMM discovery, pairing, transport (fallback) | ✅ Real, verified two-device end-to-end |
| Push-to-talk capture → VAD → STT → Protobuf → transmit | ✅ Real pipeline, wired into a bound foreground service |
| On-device STT (AI4Bharat IndicConformer via sherpa-onnx, INT8) | ✅ Real inference, per-language ONNX graphs — **9 languages** (no Odia — see [Language Coverage](#-language-coverage--a-documented-gap)) |
| On-device TTS (real espeak-ng phonemized VITS voices) | ✅ Real inference — **only 5 languages** have a verified free offline voice (Hindi, Gujarati, Malayalam, Bengali, English) |
| Voice Activity Detection | ⚠️ Falls back to simple RMS energy thresholding — the bundled Silero VAD ONNX model was found non-functional on real audio and is honestly disabled (see [Known Limitations](#-known-limitations--honest-gaps)) |
| On-device AI Assistant (Phi-3 Mini, llama.cpp) | ✅ Real inference when the optional 2.39 GB model is downloaded and the device is arm64-v8a/x86_64; otherwise a keyword-matching fallback answers instead — the UI discloses which one is active |
| Model download/verify/extract pipeline | ✅ Real OkHttp downloads, SHA-256 verification, tar.bz2 extraction, resumable/retryable |
| Peer authorization whitelist (Room-persisted) | ✅ Real, survives app restarts |
| Emergency / SOS broadcast screen | ❌ **Does not exist.** Dropped from the original 5-screen design — no SOS button, no distress UI anywhere in the app today, though the `ALERT` message type and alarm-volume/DND-bypass playback path still exist in the wire protocol and `AudioPlaybackManager` |
| Per-peer session screen (`PeerSessionScreen`) | ⚠️ UI-only stub — not wired to the real transceiver pipeline (see [Screens](#peersessionscreen-stub)) |

---

## 🏗️ Architecture Overview

```
┌──────────────────────────────────────────────────────────────────────┐
│                            SENDER DEVICE                              │
│  Mic → AudioRecord (16kHz mono PCM_16BIT, 100ms/1600-sample chunks)  │
│      → VADModule (BASIC_ENERGY RMS threshold — neural path disabled) │
│      → 800ms silence → flushAndTranscribe()                          │
│      → STTModule (sherpa-onnx IndicConformer INT8, per-language)     │
│           mel-spectrogram(80-bin) → OrtSession.run() → CtcDecoder     │
│      → ProtobufSerializer.encode() → TransceiverMessage               │
│      → SocketTransport (TCP :8765) / BluetoothRFCOMMManager           │
└──────────────────────────────────────────────────────────────────────┘
                     4-byte length-prefixed Protobuf frame
                                    ▼
┌──────────────────────────────────────────────────────────────────────┐
│                          RECEIVER DEVICE                              │
│  ITantraForegroundService socket listener                             │
│      → ProtobufSerializer.decode()                                    │
│      → TTSModule (sherpa-onnx VITS + espeak-ng phonemizer, 5 langs)  │
│      → AudioPlaybackManager (AudioTrack, USAGE_MEDIA)                │
│  [ALERT type] → STREAM_ALARM @ max volume + DND-bypass attempt        │
└──────────────────────────────────────────────────────────────────────┘
```

### Technology Stack (as actually configured in `build.gradle.kts` / `libs.versions.toml`)

| Layer | Technology | Notes |
| --- | --- | --- |
| Language | Kotlin 2.3.10 | `compileSdk 36` (bumped from 35 solely to satisfy `llamacpp-kotlin`'s Kotlin-metadata version requirement) |
| UI | Jetpack Compose (BOM 2024.08.00) + Material 3 | Single-Activity, no XML layouts |
| Navigation | Navigation-Compose 2.7.7 | 6 routes, see [Navigation](#navigation--screen-flow) |
| STT | AI4Bharat IndicConformer, sherpa-onnx ONNX INT8 export, per-language graph + `tokens.txt` | via `onnxruntime-android` 1.18.0 |
| TTS | Real espeak-ng-phonemized VITS voices (Piper / Coqui / Mimic3) | via `sherpa-onnx-static-link-onnxruntime` AAR (v1.13.7), **not** a custom IndicTTS export |
| VAD | Silero VAD v4 ONNX (loaded but its inference is disabled — see below) | RMS-energy fallback is what's actually active |
| AI Assistant LLM | Phi‑3‑mini‑4k‑instruct GGUF (q4), optional 2.39 GB download | via `llamacpp-kotlin` 0.4.0 (arm64-v8a + x86_64 only) |
| Networking | `WifiP2pManager` (primary) + `BluetoothAdapter`/RFCOMM (fallback) | TCP port 8765; UUID `6E400001-B5A3-F393-E0A9-E50E24DCCA9E` |
| Wire format | Protocol Buffers v3, `protobuf-javalite` 3.25.3 | 4-byte big-endian length-prefixed frames |
| Persistence | Room 2.8.4 (peer registry), DataStore Preferences 1.1.1 (device profile) | |
| Model downloads | OkHttp 4.12.0, streamed with SHA-256 verification | `androidx.work` is a declared dependency but **is not actually used** — downloads run on a plain `CoroutineScope(SupervisorJob() + Dispatchers.IO)`, not WorkManager |
| Archive extraction | Apache Commons Compress 1.26.2 | pure-JVM tar+bzip2, no native code |
| Serialization | kotlinx.serialization.json 1.7.1 | model manifest entries |
| Async | Kotlin Coroutines 1.8.1 + StateFlow/SharedFlow | |
| Background | Single `Service` (`ITantraForegroundService`), `FOREGROUND_SERVICE_TYPE_MICROPHONE` | |

---

## 📁 Actual Project Structure

```
iTantra/
├── app/src/main/java/com/itantra/
│   ├── ITantraApp.kt                     # Application class — minimal, extracts bundled models async
│   ├── MainActivity.kt                   # Single Activity; permission requests; bottom-nav Compose shell
│   ├── core/
│   │   ├── ai/
│   │   │   ├── LanguageDetector.kt       # Pure heuristic (Unicode-script + keyword) language ID — NOT the downloaded fastText model
│   │   │   ├── LlmModule.kt              # Real Phi-3/GGUF inference via llama.cpp; device-support gate; stop-sequence/token cap
│   │   │   └── TacticalAiEngine.kt       # Honest keyword-matching fallback (9 languages) when the LLM isn't available
│   │   ├── audio/
│   │   │   ├── AudioCaptureModule.kt     # AudioRecord capture, VAD-gated speech buffering
│   │   │   ├── VADModule.kt              # Silero VAD (present but disabled) + BASIC_ENERGY fallback
│   │   │   ├── STTModule.kt              # sherpa-onnx IndicConformer inference, per-language lazy load
│   │   │   ├── CtcDecoder.kt             # Pure-Kotlin CTC greedy decode (unit-tested)
│   │   │   ├── TTSModule.kt              # sherpa-onnx VITS synthesis, 5 languages
│   │   │   └── AudioPlaybackManager.kt   # AudioTrack playback (normal + ALERT/alarm modes)
│   │   ├── download/
│   │   │   ├── ModelDownloadManager.kt   # OkHttp download → verify → extract state machine
│   │   │   ├── ModelRegistry.kt          # Per-model source URLs, hashes, sizes
│   │   │   ├── ModelHashStore.kt         # Trust-on-first-download SHA-256 persistence
│   │   │   ├── HashUtils.kt              # SHA-256 + header normalization (unit-tested)
│   │   │   ├── ArchiveExtractor.kt       # tar.bz2 extraction (Commons Compress)
│   │   │   └── ModelAssetExtractor.kt    # Extracts APK-bundled assets to physical files
│   │   ├── network/
│   │   │   ├── WifiDirectManager.kt      # WifiP2pManager wrapper, reconnect backoff
│   │   │   ├── BluetoothRFCOMMManager.kt # BT Classic server/client
│   │   │   ├── MeshHardwareManager.kt    # Discovery/hosting coordinator for the UI radar
│   │   │   └── SocketTransport.kt        # TCP transport, length-prefixed framing, ping/ACK RTT
│   │   ├── proto/
│   │   │   └── ProtobufSerializer.kt     # TransceiverMessage <-> domain model + wire framing
│   │   └── service/
│   │       └── ITantraForegroundService.kt  # Owns every module above; bound by MainViewModel
│   ├── data/
│   │   ├── DeviceProfileRepository.kt    # DataStore: deviceId (UUID), displayName, buildAlias
│   │   ├── PeerRegistryRepository.kt     # Room-backed peer list + authorization whitelist
│   │   └── db/PeerDatabase.kt            # Room database (peers table)
│   ├── domain/
│   │   ├── contracts/                    # NetworkCallbacks, AudioCallbacks ("frozen post Sprint 1" — has grown since)
│   │   └── model/                        # PeerDevice, TransceiverMessage, AppResult, AppState, DeviceProfile, ModelManifest, AppMetadata
│   └── ui/
│       ├── MainViewModel.kt              # Single state hub; binds to the foreground service
│       ├── navigation/NavGraph.kt        # 6 routes; no gating at the nav-graph level
│       ├── component/                    # ModelDownloadGate, OnboardingDialog
│       ├── screen/                       # HomeScreen, TransceiverScreen, PeerSessionScreen, RadarScreen, AIAssistantScreen, DownloadsScreen
│       └── theme/                        # Monochrome black/white theme, Poppins typography
│   └── (vendored) com/k2fsa/sherpa/onnx/Tts.kt   # sherpa-onnx JNI wrapper, v1.13.7, do-not-hand-edit
├── app/src/main/proto/itantra.proto      # TransceiverMessage wire schema (8 fields, incl. `sequence`)
├── app/src/test/java/com/itantra/        # CtcDecoderUnitTest, DomainModelUnitTest, HashUtilsUnitTest, LlmModuleUnitTest
├── model-export/                         # Legacy Python export/quantization scripts — SUPERSEDED, see note below
├── landing/                              # Vite+React scaffold, unmodified from `npm create vite` — not a built landing page
├── PRD.md, team.md                       # Original design/sprint docs — historical, see banner above
└── dev.ps1 / dev.bat                     # Developer CLI: build/install/logs/screenshot helpers (Windows)
```

> **Note on `model-export/`:** these Python scripts (`export_indicconformer.py`, `quantize_models.py`, `validate_wer.py`) implement the *original* plan of bundling a single multilingual IndicConformer + custom IndicTTS export directly in the APK. The shipped app instead **downloads pre-exported, per-language sherpa-onnx models at runtime** (see [Model Download Pipeline](#-model-download--integrity-pipeline)) — these scripts are not part of the current model-acquisition path and are kept only as historical/reference tooling.

---

## 🎙️ Audio & Speech Pipeline

### Capture — `AudioCaptureModule`
`AudioRecord(MIC, 16000Hz, MONO, PCM_16BIT)` on a dedicated `Dispatchers.IO` scope. Reads 1600-sample (100ms) chunks, converts to `Float` PCM, and feeds each chunk to `VADModule`. Speech chunks accumulate into a buffer guarded by a `synchronized` lock — added after a real `ConcurrentModificationException` was hit on-device when PTT-release flushed the buffer while the capture loop was still appending to it. End-of-speech = **8 consecutive silent 100ms chunks (800ms)**; hard cap of 30s per utterance. Public surface: `startCapture()`, `stopCapture()`, `suspend fun flushAndTranscribe(): FloatArray?`.

### Voice Activity Detection — `VADModule`
Loads the bundled Silero VAD v4 ONNX model, but **its neural inference path is deliberately disabled**. Direct quote from the code:

> *"The bundled `silero_vad_v4.onnx` loads and runs cleanly with the correct input/window shapes... but its output is empirically non-functional as a speech detector — pulled the exact file off-device and tested it in Python against silence, a loud 200Hz sine wave, and loud white noise: all three produce the same near-zero probability (0.0005–0.002)... This isn't a code bug to work around — the model itself doesn't discriminate speech from silence in this configuration."*

`activeBackend` is therefore forced to `BASIC_ENERGY`: RMS thresholding (`rms > 0.025 → 0.85 probability`, else `0.05`) against `SPEECH_THRESHOLD = 0.5`. The neural session is still loaded (for future re-enablement) and has a runtime demotion path — if it ever throws during inference (a confirmed on-device error was `"expected [1,3) found 4"`), the module closes the session, permanently switches to `BASIC_ENERGY`, and retries.

### Speech-to-Text — `STTModule` + `CtcDecoder`
Uses **AI4Bharat IndicConformer**, exported by a third party as per-language sherpa-onnx ONNX INT8 graphs (`stt_{lang}_int8.onnx` + `stt_{lang}_tokens.txt`) — **not** a single shared multilingual model as originally planned. Each language is lazy-loaded and cached on first use (`OrtSession` + vocab + resolved I/O tensor names). `SessionOptions`: 2 intra-op threads, `ALL_OPT`, attempts NNAPI delegate with a CPU/XNNPACK fallback.

Feature extraction is a hand-rolled 80-bin log-mel spectrogram pipeline (25ms frame / 10ms hop, Hann window, naive DFT — a comment flags that production should use an FFT library via JNI instead), with **per-feature mean/std normalization across the whole utterance** — required because IndicConformer is NeMo-trained with `normalize: per_feature`; without it, the encoder was confirmed on-device to collapse to the same repeated output token regardless of input audio. Decoding uses a from-scratch, unit-tested `CtcDecoder.greedyDecode()`, with `blankId = vocab.size - 1` (NeMo/IndicConformer convention — confirmed against a real `tokens.txt` where `<blk>` is the last, not the first, vocabulary entry). On failure the module returns a real `AppResult.Error` (`MODEL_LOAD_FAILED`, `STT_INFERENCE_FAILED`, or empty-transcription) — it never fabricates placeholder text.

### Text-to-Speech — `TTSModule`
Uses **sherpa-onnx's `OfflineTts`** with real espeak-ng-phonemized VITS voices (Piper/Coqui/Mimic3), not a custom IndicTTS VITS export as originally planned. Only 5 languages have a verified, freely-licensed offline voice:

| Language | Voice source | Notes |
| --- | --- | --- |
| Hindi | Piper | |
| Gujarati | Mimic3 | explicitly documented as "lower quality tier (only source found)" |
| Malayalam | Piper | |
| Bengali | Coqui | |
| English | Piper | |
| Kannada, Tamil, Telugu, Marathi, Odia | — | **No free offline TTS source found** after checking Piper/Coqui/Mimic3/MMS and every VITS voice in sherpa-onnx's release catalog. Not offered for download; `TTSModule.synthesize()` returns a real error rather than fabricated/wrong-language audio for these. |

Output is resampled to 16kHz for playback and cached per-language `OrtSession`-equivalent (`OfflineTts` instance).

### Playback — `AudioPlaybackManager`
Normal messages play via `AudioTrack` with `USAGE_MEDIA` — deliberately *not* `USAGE_VOICE_COMMUNICATION`, which was found on-device to route through the earpiece at reduced volume ("the volume is too low"). `ALERT`-type messages use `STREAM_ALARM` forced to max volume, attempt to flip ringer mode to bypass Do-Not-Disturb (gracefully catching `SecurityException` if the policy-access permission isn't granted), and use `USAGE_ALARM` + `FLAG_AUDIBILITY_ENFORCED`.

---

## 🤖 On-Device AI Assistant

The "AI Tactical Assistant" screen has **two independent answer sources**, with the UI honestly disclosing which one is currently active:

1. **Real LLM path** (`LlmModule`) — Microsoft **Phi-3-mini-4k-instruct**, GGUF q4 quantized, run via the `llamacpp-kotlin` bindings. Gated by `isDeviceSupported()` (native libs exist only for `arm64-v8a` and `x86_64` — no `armeabi-v7a` build) **and** whether the ~2.39 GB optional model has been downloaded (it's explicitly optional in the Downloads screen: *"Assistant already works without this"*). Context window 4096 tokens, generation bounded to **250 tokens max** with stop sequences `<|end|>` / `<|user|>` — added after runaway generations were observed running past 200 tokens on-device with no natural stop.
2. **Fallback path** (`TacticalAiEngine`) — an honest, hardcoded keyword-matching responder across 9 languages (first-aid/disaster-protocol phrases matched against query substrings like `"cpr"`, `"pani"`, `"sos"`, `"earthquake"`), used whenever the real LLM isn't available. A `MainViewModel.isUsingRealLlm` flag drives the UI's "on-device Phi-3 (real generation)" vs. "Quick-reference assistant (Phi-3 not loaded)" status line, so the fallback is never presented as if it were generated text.

**Language detection** (`LanguageDetector`) is a pure heuristic, not a model: it counts Unicode code points per Indic script block, disambiguates Hindi vs. Marathi via script-specific characters/keywords, and matches romanized ("Hinglish"-style) text against hardcoded token lists for code-switch detection. Note: `ModelRegistry` does register a downloadable fastText `lid.176.ftz` model (`LANG_DETECTION` pack), but it is **not actually consumed anywhere** in the current `LanguageDetector` implementation — the download exists but the model is unused.

---

## 📡 Networking & Mesh Transport

### Wi-Fi Direct — `WifiDirectManager` (primary)
Registers a `WifiP2pBroadcastReceiver` for state/peers/connection-changed actions. Deliberately does **not** run its own `discoverPeers()` loop — that's owned by `MeshHardwareManager` instead, because two independent discovery calls were found to collide on-device (`Discovery failed: reason=2`/BUSY). On group formation, the group owner starts a TCP server; the client connects to the owner's IP. Reconnection is exponential backoff — `500ms × 2^attempt`, capped at 30s, up to **3 attempts** — after which it emits a `WIFI_DIRECT_UNAVAILABLE` error that the foreground service uses to trigger Bluetooth fallback.

### Bluetooth Classic RFCOMM — `BluetoothRFCOMMManager` (fallback)
UUID `6E400001-B5A3-F393-E0A9-E50E24DCCA9E`. `listenUsingRfcommWithServiceRecord()` for hosting, `createRfcommSocketToServiceRecord()` for connecting. Same 4-byte length-prefixed framing as the Wi-Fi Direct transport.

### `SocketTransport` — TCP layer
Server on **port 8765**. Frame = 4-byte big-endian length prefix + raw Protobuf bytes (max 64KB/message). Runs a ping/ACK loop every 5s to measure per-peer round-trip latency, reported via `NetworkCallbacks.onLatencyMeasured` (feeds the Radar screen and peer list).

### `MeshHardwareManager` — discovery/UI coordinator
Drives both Wi-Fi Direct peer discovery and classic Bluetooth device discovery together, and exposes the combined live peer list, hosting state, and group-role state (`STANDALONE` / `HOSTING_GROUP` / `GROUP_OWNER` / `CLIENT`) that the Radar and Transceiver screens render.

---

## 🔌 Protobuf Wire Protocol

```protobuf
message TransceiverMessage {
  enum MessageType { SPEECH = 0; ALERT = 1; ACK = 2; PING = 3; }
  MessageType type       = 1;
  string      text       = 2;
  string      src_lang   = 3;
  string      dst_lang   = 4;
  string      sender_id  = 5;
  int64       timestamp  = 6;
  float       confidence = 7;
  uint32      sequence   = 8;   // NOT in the original PRD — added for dedup/ordering
}
```

`ProtobufSerializer` hand-builds the 4-byte big-endian length prefix (not a library helper) and maps the generated `TransceiverProto` class to/from the domain `TransceiverMessage` (which additionally carries a UI-only `direction: SENT|RECEIVED` field that is **not** on the wire).

---

## 🧩 Domain Model, Contracts & Persistence

### The "frozen" contract has grown

`team.md`/`PRD.md` describe `NetworkCallbacks`/`AudioCallbacks`/`AppResult<T>` as **frozen post Sprint 1**. In the real code both interfaces have grown beyond their documented shape:

```kotlin
// domain/contracts/NetworkCallbacks.kt — 6 methods (team.md/PRD.md only ever listed 5)
interface NetworkCallbacks {
    fun onNodeDiscovered(device: PeerDevice)
    fun onNodeConnected(device: PeerDevice)
    fun onNodeDisconnected(deviceId: String)
    fun onTextReceived(message: TransceiverMessage)
    fun onNetworkError(error: AppResult.Error)
    fun onLatencyMeasured(deviceId: String, latencyMs: Long)   // ⬅ added — feeds SocketTransport's ping/ACK RTT into Radar/peer list
}

// domain/contracts/AudioCallbacks.kt — signatures widened, one method added
interface AudioCallbacks {
    fun onVADTriggered(isSpeech: Boolean, probability: Float)              // ⬅ probability param added
    fun onSTTResult(result: AppResult<String>, confidence: Float, inferenceMs: Long)  // ⬅ inferenceMs param added
    fun onTTSSynthesisComplete(durationMs: Long)
    fun onAudioError(error: AppResult.Error)
    fun onAudioFocusChanged(gained: Boolean)                               // ⬅ added
}
```

Both interface files still carry the doc-comment `"FROZEN POST SPRINT 1 — Do not modify signatures unilaterally"` and describe the counterparty as `NetworkOrchestrator` — **no `NetworkOrchestrator` class exists anywhere in the codebase**; that orchestration logic is inlined directly in `ITantraForegroundService`. Both are stale doc comments left over from the original design.

### `AppResult<T>` / `ErrorCode`

```kotlin
sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    data class Error(val code: ErrorCode, val message: String) : AppResult<Nothing>()
    object Loading : AppResult<Nothing>()
}

enum class ErrorCode {
    MODEL_LOAD_FAILED, NETWORK_TIMEOUT, NETWORK_DROPPED, AUDIO_FOCUS_LOST,
    VAD_ERROR, STT_INFERENCE_FAILED, TTS_SYNTHESIS_FAILED, SOCKET_ERROR,
    PERMISSION_DENIED,
    BLUETOOTH_UNAVAILABLE,       // ⬅ not in team.md's original 9-value list
    WIFI_DIRECT_UNAVAILABLE      // ⬅ not in team.md's original 9-value list — this is what actually triggers BT fallback
}
```

### Domain models (actual fields)

- **`PeerDevice`** — `deviceId` (MAC, primary key), `deviceName`, `rssi: Int = -70`, `connectionType: ConnectionType` (`WIFI_DIRECT`/`BLUETOOTH`), `latencyMs: Long`, `lastSeenAt: Long`, `isConnected: Boolean`, `isAuthorized: Boolean`, `isNew: Boolean`.
- **`TransceiverMessage`** (domain wrapper) — `type`, `text`, `srcLang`, `dstLang`, `senderId`, `timestamp`, `confidence`, `sequence: Int`, plus **`direction: SENT|RECEIVED`** (UI-only, never on the wire).
- **`DeviceProfile`** — `deviceId`, `displayName: String?`, `buildAlias` (e.g. `"ITantra-A3F7"` — first 2 bytes of `SHA-256(Build.FINGERPRINT + deviceId)`, hex-encoded), `isProfileComplete = displayName != null`.
- **`IndicLanguage`** enum — all 10 languages with BCP-47 code + native-script display name; `fromCode()` falls back to `HINDI` for an unknown code.
- **`AlertTemplate`** — 4 predefined SOS templates (Medical Emergency, Fire, Structural Failure, Evacuation), each translated into 5 languages (hi/en/ta/te/bn). **This exists in the domain layer but is dead code from the UI's perspective** — there is no SOS screen anywhere that reads it (see [Known Limitations](#-known-limitations--honest-gaps)).
- **`ConnectionMode`** (`PUSH_TO_TALK`/`PHONE_MODE`), **`AudioPipelineState`**, **`NetworkState`**, **`InferenceDelegate`** — all defined in `AppState.kt`, but **there is no single `AppState` data class** in that file despite the filename; it's a grab-bag of state-related enums/small data classes (`AlertEvent`, `ConnectionMode`, `AudioPipelineState`, `NetworkState`, `InferenceDelegate`). The actual UI state hub is `MainViewModel`'s collection of individual `StateFlow`s, not a single aggregate `AppState`.
- **`ModelPack`** enum — 23 entries: `VAD_MODEL`, 9× `STT_*`, `LANG_DETECTION`, `ESPEAK_NG_DATA`, 9× `TTS_*` (including the 5 unsupported languages, kept with `sizeMb=0` so nothing else in the codebase dangles), `AI_ASSISTANT`. `ModelPack.coreTransceiverPacks()` returns the 17 packs the Downloads screen calls "compulsory."
- **`AppMetadata`** — a static `object` of display constants (app name, version, problem-statement info, hardware targets, supported-language list) mirroring `assets/app_metadata.json` and the manifest's `<meta-data>` tags. Its model-description constants (`STT_MODEL`, `TTS_MODEL`, `AI_MODEL`) still describe the **original** plan (single ~150MB multilingual STT model, IndicTTS VITS for all languages, "Phi-3 Mini 3.8B ~2.2GB") rather than the per-language sherpa-onnx models and 4k-instruct GGUF actually shipped — cosmetic/about-screen text only, not consumed by any pipeline logic.

### Persistence

- **`DeviceProfileRepository`** — DataStore Preferences (`"device_profile"`), keys `device_id`, `display_name`, `build_alias`. Device ID is generated once (`UUID.randomUUID()`) and persisted; `saveDisplayName()` is the only mutator besides first-run generation.
- **`PeerRegistryRepository`** — Room (`itantra_peers.db`, single `peers` table, schema version 1, `exportSchema = false`, no migrations). `upsertPeer()` deliberately **preserves the existing `isAuthorized` value** on every discovery-driven update rather than overwriting it with the incoming (usually `false`) value — this is what makes peer authorization survive across re-discovery and app restarts. `PeerDao` also exposes `disconnectAll()` (used on service teardown) and `countAuthorized()`.

---

## 📦 Model Download & Integrity Pipeline

Models are **not bundled in the APK** (aside from small always-available assets extracted on first launch by `ModelAssetExtractor`). Everything else is fetched at runtime by `ModelDownloadManager` into `context.filesDir/models/`. The Downloads screen currently tracks **23 model packs**, of which **17 form the "compulsory" core Transceiver bundle** (VAD + language-auto-detector + shared espeak-ng phoneme data + 9 STT languages + 5 TTS voices); the optional Phi-3 LLM and the 5 TTS languages with no available voice make up the rest.

**Flow:** `download(pack)` → state `Queued` → OkHttp GET streamed in 32KB chunks to a `.part` file (resuming/skipping any already-good piece on retry) → SHA-256 verified, in priority order: (1) a hash pinned in `ModelRegistry`, (2) HuggingFace's `X-Linked-ETag` header — read off the **redirect** response, not the final CDN response, after a real on-device bug where every HF-hosted download failed integrity checks because the ETag was being read from the wrong response in the redirect chain — (3) trust-on-first-download via `ModelHashStore` for sources with no published hash → on success, `.part` renamed to the final filename; on mismatch, deleted and marked `Failed` → if the pack is an archive bundle, `ArchiveExtractor` extracts the `.tar.bz2` (stripping the release's top-level wrapper folder and de-duplicating each Piper voice's bundled `espeak-ng-data/` copy against the one shared copy) → `Downloaded`.

**The stalled-download fix:** OkHttp's default `Dispatcher` caps concurrent requests to the *same host* at 5. With most STT files hosted on `huggingface.co` and up to 17 packs queued in parallel, downloads 6+ just sat at 0% forever. Fixed by:
```kotlin
.dispatcher(Dispatcher().apply {
    maxRequests = 40
    maxRequestsPerHost = 40
})
```

**Model sources actually used:**
- STT: `huggingface.co/parismitaglobalsolutions/indicconformer-sherpa-onnx` — 9 languages (hi, gu, mr, kn, ml, ta, te, bn, en); **no Odia** (the source repo only has an Assamese model, and it is deliberately not substituted for Odia).
- TTS: `github.com/k2-fsa/sherpa-onnx` `tts-models` release — 5 languages (see table above).
- VAD: Silero VAD v4 ONNX, GitHub raw.
- Language ID: fastText `lid.176.ftz`, `fbaipublicfiles.com` (downloaded but currently unused by the app — see [AI Assistant](#-on-device-ai-assistant)).
- AI Assistant: `microsoft/Phi-3-mini-4k-instruct-gguf` on Hugging Face.

---

## ⚙️ Foreground Service

`ITantraForegroundService` owns every audio/network/download module and is the real engine behind push-to-talk. It exposes a `PipelineStage` state machine (`IDLE → LISTENING → TRANSCRIBING → TRANSMITTING`, and `RECEIVING → SPEAKING` on the receive side) so the UI is never a black box about what's currently happening. `MainViewModel` starts and **binds** to it on `init` — this bind is a recent fix: the service was previously declared in the manifest but never started or bound anywhere, so pressing PTT had nothing to call.

---

## 🖥️ Screens (actual, not the original 5-screen plan)

Bottom navigation, left to right: **Home → Radar → Radio (Transceiver, center hero button) → Downloads → Assistant.** `PeerSessionScreen` is a 6th route, reached only by tapping a connected peer (no bottom-nav entry of its own).

- **Home** — dashboard: node identity card (callsign, build ID), a "System Architecture" spec bento grid, and a profile-edit dialog (closest thing to a Settings screen — there's no dedicated Settings route). Also hosts the mandatory-callsign `OnboardingDialog`.
- **Transceiver** — the real operational screen: Host Beacon / Search Peers toggles, live peer list (Wi-Fi Direct + Bluetooth, merged), giant hold-to-talk PTT button wired to the foreground service, live pipeline-stage and connection-status indicators, language auto-detect toggle. Gated behind `ModelDownloadGate` until the core model bundle is downloaded.
- <a id="peersessionscreen-stub"></a>**PeerSessionScreen** — a per-peer chat-style view. **Not wired to the real pipeline**: its PTT button is a local toggle that inserts a hardcoded placeholder string (`"Voice captured — STT engine transcription appears here."`) rather than calling real STT, and typed messages stay in local UI state only. Best understood as an in-progress mock layered on top of the working Transceiver screen.
- **Radar** — Canvas-drawn sweep visualizing peers by RSSI-derived distance; angle is a hash of the device ID (no real compass bearing).
- **AI Assistant** — chat UI for the on-device assistant described above, with an STT input-language picker and per-message speak/stop controls.
- **Downloads** — the model pack manager described above.

There is **no Settings screen and no SOS/Emergency screen** in the current app.

---

## 🌐 Language Coverage — a documented gap

| Language | STT | TTS |
| --- | --- | --- |
| Hindi | ✅ | ✅ |
| Gujarati | ✅ | ✅ (lower-quality voice) |
| Marathi | ✅ | ❌ no source found |
| Kannada | ✅ | ❌ no source found |
| Malayalam | ✅ | ✅ |
| Tamil | ✅ | ❌ no source found |
| Telugu | ✅ | ❌ no source found |
| Odia | ❌ no source found | ❌ no source found |
| Bengali | ✅ | ✅ |
| English | ✅ | ✅ |

This is disclosed directly in the Downloads screen's own copy, but is *not* reflected in the "10 Indic Languages" claims elsewhere (Home screen spec grid, app metadata) — those overstate actual voice-synthesis coverage.

---

## ⚠️ Known Limitations / Honest Gaps

- **VAD is energy-threshold based, not neural** — the bundled Silero model doesn't discriminate speech from silence in the current pipeline configuration (see above). This is more sensitive to background noise than a real neural VAD.
- **TTS voice coverage is 5 of 10 languages**, and Gujarati's voice is explicitly documented as lower quality.
- **STT has no Odia model** — no known free offline source exists yet.
- **No SOS/Emergency broadcast UI** — dropped from the original design; the `ALERT` message type and alarm-volume playback path exist in the code but there is no screen or button that produces one. `AlertTemplate` (4 templates × 5 languages) still exists in `domain/model/AppState.kt` but is now dead code — nothing in the UI references it.
- **Contract drift** — `NetworkCallbacks`/`AudioCallbacks` are still doc-commented "frozen post Sprint 1" but have both grown real methods/params since (`onLatencyMeasured`, VAD probability, STT inference timing, `onAudioFocusChanged`); the `NetworkOrchestrator` class both interfaces' doc comments refer to was never actually built — see [Domain Model, Contracts & Persistence](#-domain-model-contracts--persistence).
- **`PeerSessionScreen` is a UI mock**, not connected to the real transceiver pipeline.
- **AI Assistant's language-ID model download is unused** — `LanguageDetector` is pure heuristic code, not the downloaded fastText model.
- **`androidx.work` (WorkManager) is a declared but unused dependency** — model downloads run on a plain coroutine scope, not a scheduled/persisted WorkManager job, so a download does not survive process death.
- **`model-export/` Python scripts are stale** — they implement the original bundled-multilingual-model plan, superseded by the runtime sherpa-onnx download pipeline.
- **`landing/`** is an unmodified Vite+React starter template, not a built marketing site.

---

## 🔐 Permissions (actual `AndroidManifest.xml`)

| Permission | Why |
| --- | --- |
| `RECORD_AUDIO` | Mic capture for STT/VAD |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` / `NEARBY_WIFI_DEVICES` (API 33+) | Required by Android for Wi-Fi Direct peer discovery |
| `BLUETOOTH*` (`CONNECT`, `SCAN`, `ADVERTISE`, legacy `BLUETOOTH`/`BLUETOOTH_ADMIN` ≤ API 30) | BT Classic RFCOMM discovery/pairing/transport |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE` | Persistent background engine |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `RECEIVE_BOOT_COMPLETED` | Keep the service alive |
| `MANAGE_NOTIFICATION_POLICY`, `WAKE_LOCK`, `VIBRATE` | ALERT playback: DND bypass attempt, screen/CPU wake |
| **`INTERNET`, `ACCESS_NETWORK_STATE`** | **One-time model downloads only** — manifest comment: *"All P2P communication is local Wi-Fi Direct / Bluetooth — no internet required post-download."* |

---

## 🚀 Building & Running

### Prerequisites
Android Studio (AGP 8.11.2 / Gradle wrapper included), JDK 17, `compileSdk 36`, `minSdk 26`, `targetSdk 35`. No Python/model-export step is required to build and run — models are fetched at runtime by the app itself.

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

On Windows, `dev.ps1` / `dev.bat` provide a small developer CLI:
```powershell
.\dev.ps1 run          # build + install + launch
.\dev.ps1 fast         # fast deploy (assembleDebug + fastdeploy install)
.\dev.ps1 logs         # tail ModelDownloadManager/MainViewModel/STT/TTS/VAD/network logcat tags
.\dev.ps1 screenshot   # pull a device screenshot
```

### First run
1. Install and launch on two Android devices (API 26+).
2. Complete the mandatory callsign onboarding dialog on each.
3. Open **Downloads**, tap "Download the Pack" to fetch the ~169 MB core bundle (VAD + STT + the 5 available TTS voices + language-ID data). The optional 2.39 GB Phi-3 model is only needed for real LLM AI-Assistant replies.
4. Open **Transceiver** on both devices; one taps "Host Beacon", the other "Search Peers" and connects.
5. Hold the PTT button and speak — the transcribed text and synthesized reply should appear/play on the other device.

---

## 🧪 Testing

Unit tests that exist today (`app/src/test/java/com/itantra/`):

| Test | Locks in |
| --- | --- |
| `CtcDecoderUnitTest` | Greedy CTC decode merges repeats, drops blanks, token-vocab parsing — "verifies the real tokenizer-backed decode path (replacing the old stub that always returned empty string)" |
| `DomainModelUnitTest` | `IndicLanguage.fromCode()` mapping + unknown-code fallback to Hindi; `AlertTemplate` has at least Hindi+English translations; extended `TransceiverMessage`/`PeerDevice` shapes (with `sequence`, `isConnected`, etc.) |
| `HashUtilsUnitTest` | SHA-256 header normalization (quotes, `sha256:` prefix, case), rejects malformed/weak (non-LFS) ETags, case-insensitive hash comparison |
| `LlmModuleUnitTest` | ABI gating — `arm64-v8a`/`x86_64` supported, `armeabi-v7a`-only devices correctly get **no** native LLM |

There is no automated instrumentation/UI test suite; integration verification has been done via real two-device hardware testing (see below).

---

## 🛠️ Development History — real fixes

The commit history documents genuine on-device debugging rather than a straight-line implementation, which is worth knowing when reading the code:

- Made IndicConformer STT genuinely transcribe instead of fabricating text
- Real TTS phonemization (sherpa-onnx), honest SHA-256 verification, fixed wrong-repo TTS sources, honest VAD fallback
- Fixed STT `tokens.txt` 404s and wasted re-downloads found via real device testing
- Wired real on-device Phi-3 inference into the AI Assistant (llama.cpp)
- Fixed on-device bugs found via live ADB testing: STT crash/decoding, an audio race condition, an LLM stall
- Bounded LLM generation to a stop sequence / max-token cap
- Fixed Wi-Fi Direct discovery collision; investigated and honestly disabled the broken neural VAD
- Wired the real PTT walkie-talkie transport into the running app
- Added Bluetooth bonded-device picker; fixed unconnected peers never being tappable
- Added real Bluetooth discovery + pairing flow; fixed a wrong-transport connect bug
- Fixed model downloads silently stalling under OkHttp's default per-host cap
- Verified a real two-device walkie-talkie session end-to-end; fixed Bluetooth server start and playback volume
- Surfaced real Host Beacon/connection/pipeline state in the UI (replacing state that previously didn't reflect reality)

---

## 🛡️ Open-Source Compliance

| Component | License |
| --- | --- |
| AI4Bharat IndicConformer (sherpa-onnx export) | Apache 2.0 |
| sherpa-onnx / Piper / Coqui / Mimic3 voices | Apache 2.0 / MIT (voice-dependent) |
| Silero VAD | MIT |
| ONNX Runtime Mobile | MIT |
| llama.cpp / `llamacpp-kotlin` | MIT |
| Phi-3-mini-4k-instruct | MIT |
| Protocol Buffers (javalite) | BSD-3-Clause |
| Jetpack Compose, Room, DataStore, WorkManager | Apache 2.0 |
| OkHttp | Apache 2.0 |
| Apache Commons Compress | Apache 2.0 |

**No proprietary SDKs. Internet is used only for the one-time model download.**

---

## 👥 Team

| Developer | Domain | Responsibility |
| --- | --- | --- |
| **Gaurav** | Engine | Audio/VAD/STT/TTS inference, networking, model pipeline, foreground service |
| **Sarthak** | Shell | Jetpack Compose UI, navigation, ViewModel/state |

See [`team.md`](team.md) for the original sprint plan (historical).

---

## 📜 License

Application code: **Apache 2.0**. See individual component licenses above.

---

_iTantra v2.0.0 · Smart India Hackathon 2026 · Problem Statement #26173_
