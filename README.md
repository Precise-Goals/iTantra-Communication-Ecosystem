# 🛰️ iTantra — Universal Communication Ecosystem

> **Smart India Hackathon 2026 | Problem Statement PS-26173**
> Indian Multilingual TTS & STT Aided Neural Transceiver Radio Access for Low Bitrate Links

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Platform: Android](https://img.shields.io/badge/Platform-Android%208.0+-green.svg)](https://developer.android.com)
[![Version](https://img.shields.io/badge/version-2.0.0-informational.svg)]()
[![Languages: 9 Indic (STT) / 5 (TTS)](https://img.shields.io/badge/Languages-9%20STT%20%2F%205%20TTS-orange.svg)]()

> **This document describes the real, working implementation** on branch `fix/model-pipeline-integrity`, verified through extensive on-device testing across two physical Android devices. See [`PRD.md`](PRD.md) and [`team.md`](team.md) for the original sprint-planning design docs, kept for historical reference.

---

## 📖 What is iTantra?

**iTantra** is an Android app for offline, AI-powered multilingual voice communication over ad-hoc **Wi-Fi Direct** and **Bluetooth Classic (RFCOMM)** mesh links — built for disaster zones, tactical field operations, and rural areas without GSM infrastructure.

Instead of streaming raw audio, iTantra converts speech to text **on-device** using AI4Bharat's IndicConformer STT models, sends the transcript as a small Protobuf message (~50–300 bytes) over the mesh link, and re-synthesizes it as natural speech on the receiving device using real espeak‑ng‑phonemized VITS voices (via [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx)). A separate, fully offline **AI Tactical Assistant** (on-device Phi‑3‑mini via llama.cpp, with an always-available quick-reference mode) answers first-aid/disaster-protocol queries and can translate across languages.

**Internet is used exactly once** — for the initial one-time download of neural model files from Hugging Face / GitHub release CDNs. Every P2P communication, VAD/STT/TTS inference, and AI Assistant query afterwards is 100% on-device with zero network calls.

### Core capabilities

| Capability | Status |
| --- | --- |
| Wi-Fi Direct + Bluetooth Classic mesh networking | ✅ Verified end-to-end across two real physical devices |
| Push-to-talk voice pipeline (capture → VAD → STT → transmit) | ✅ Fully wired into a persistent foreground service |
| On-device Speech-to-Text (AI4Bharat IndicConformer, sherpa-onnx INT8) | ✅ Real neural inference across **9 Indic languages** |
| On-device Text-to-Speech (real espeak-ng-phonemized VITS voices) | ✅ Natural-sounding voices for Hindi, Gujarati, Malayalam, Bengali & English, with more languages in progress |
| Voice Activity Detection | ✅ Adaptive, field-tested energy-based detector, with a neural model already in the pipeline for a future upgrade |
| On-device AI Tactical Assistant (Phi-3 Mini via llama.cpp) | ✅ Real generative answers once the optional model is downloaded, with a built-in quick-reference mode so the assistant is never unavailable — the UI always shows which mode answered |
| Model download / integrity pipeline | ✅ Resumable, SHA-256 verified downloads with automatic archive extraction |
| Peer authorization whitelist | ✅ Room-persisted, survives app restarts |

---

## 🏗️ Architecture Overview

```
┌──────────────────────────────────────────────────────────────────────┐
│                            SENDER DEVICE                              │
│  Mic → AudioRecord (16kHz mono PCM_16BIT, 100ms/1600-sample chunks)  │
│      → VADModule (adaptive energy-based speech detection)            │
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
│      → TTSModule (sherpa-onnx VITS + espeak-ng phonemizer)           │
│      → AudioPlaybackManager (AudioTrack, USAGE_MEDIA)                │
│  [ALERT type] → STREAM_ALARM @ max volume + DND-bypass attempt        │
└──────────────────────────────────────────────────────────────────────┘
```

### Technology Stack

| Layer | Technology | Notes |
| --- | --- | --- |
| Language | Kotlin 2.3.10 | `compileSdk 36` |
| UI | Jetpack Compose (BOM 2024.08.00) + Material 3 | Single-Activity, no XML layouts |
| Navigation | Navigation-Compose 2.7.7 | 6 routes, see [Screens](#-screens) |
| STT | AI4Bharat IndicConformer, sherpa-onnx ONNX INT8 export, per-language graph + `tokens.txt` | via `onnxruntime-android` 1.18.0 |
| TTS | Real espeak-ng-phonemized VITS voices (Piper / Coqui / Mimic3) | via `sherpa-onnx-static-link-onnxruntime` AAR (v1.13.7) |
| VAD | Silero VAD v4 ONNX (bundled) + adaptive energy-based detector | Energy-based detection is the active default, tuned via real-device testing |
| AI Assistant LLM | Phi‑3‑mini‑4k‑instruct GGUF (q4), optional 2.39 GB download | via `llamacpp-kotlin` 0.4.0 (arm64-v8a + x86_64) |
| Networking | `WifiP2pManager` (primary) + `BluetoothAdapter`/RFCOMM (fallback) | TCP port 8765 |
| Wire format | Protocol Buffers v3, `protobuf-javalite` 3.25.3 | 4-byte big-endian length-prefixed frames |
| Persistence | Room 2.8.4 (peer registry), DataStore Preferences 1.1.1 (device profile) | |
| Model downloads | OkHttp 4.12.0, streamed with SHA-256 verification | Tuned concurrency for fast parallel multi-file downloads |
| Archive extraction | Apache Commons Compress 1.26.2 | pure-JVM tar+bzip2, no native code |
| Serialization | kotlinx.serialization.json 1.7.1 | model manifest entries |
| Async | Kotlin Coroutines 1.8.1 + StateFlow/SharedFlow | |
| Background | Single `Service` (`ITantraForegroundService`), `FOREGROUND_SERVICE_TYPE_MICROPHONE` | |

---

## 📁 Project Structure

```
iTantra/
├── app/src/main/java/com/itantra/
│   ├── ITantraApp.kt                     # Application class — extracts bundled models async on first launch
│   ├── MainActivity.kt                   # Single Activity; permission requests; bottom-nav Compose shell
│   ├── core/
│   │   ├── ai/
│   │   │   ├── LanguageDetector.kt       # Fast Unicode-script + keyword language identifier
│   │   │   ├── LlmModule.kt              # Real Phi-3/GGUF inference via llama.cpp; device-support gate; stop-sequence/token cap
│   │   │   └── TacticalAiEngine.kt       # Reliable keyword-matching assistant, always available (9 languages)
│   │   ├── audio/
│   │   │   ├── AudioCaptureModule.kt     # AudioRecord capture, VAD-gated speech buffering
│   │   │   ├── VADModule.kt              # Adaptive energy-based voice detection (Silero neural model also present)
│   │   │   ├── STTModule.kt              # sherpa-onnx IndicConformer inference, per-language lazy load
│   │   │   ├── CtcDecoder.kt             # Pure-Kotlin CTC greedy decode (unit-tested)
│   │   │   ├── TTSModule.kt              # sherpa-onnx VITS synthesis
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
│   │   ├── contracts/                    # NetworkCallbacks, AudioCallbacks
│   │   └── model/                        # PeerDevice, TransceiverMessage, AppResult, DeviceProfile, ModelManifest, AppMetadata
│   └── ui/
│       ├── MainViewModel.kt              # Single state hub; binds to the foreground service
│       ├── navigation/NavGraph.kt        # 6 routes
│       ├── component/                    # ModelDownloadGate, OnboardingDialog
│       ├── screen/                       # HomeScreen, TransceiverScreen, PeerSessionScreen, RadarScreen, AIAssistantScreen, DownloadsScreen
│       └── theme/                        # Monochrome black/white theme, Poppins typography
│   └── (vendored) com/k2fsa/sherpa/onnx/Tts.kt   # sherpa-onnx JNI wrapper, v1.13.7
├── app/src/main/proto/itantra.proto      # TransceiverMessage wire schema
├── app/src/test/java/com/itantra/        # CtcDecoderUnitTest, DomainModelUnitTest, HashUtilsUnitTest, LlmModuleUnitTest
├── model-export/                         # Historical Python export/quantization scripts from early model-bundling experiments
├── landing/                              # Vite+React scaffold for a future marketing site
├── PRD.md, team.md                       # Original design/sprint docs — historical reference
└── dev.ps1 / dev.bat                     # Developer CLI: build/install/logs/screenshot helpers (Windows)
```

---

## 🎙️ Audio & Speech Pipeline

### Capture — `AudioCaptureModule`
`AudioRecord(MIC, 16000Hz, MONO, PCM_16BIT)` on a dedicated `Dispatchers.IO` scope. Reads 1600-sample (100ms) chunks, converts to `Float` PCM, and feeds each chunk to `VADModule`. Speech chunks accumulate into a thread-safe buffer. End-of-speech = **8 consecutive silent 100ms chunks (800ms)**; hard cap of 30s per utterance. Public surface: `startCapture()`, `stopCapture()`, `suspend fun flushAndTranscribe(): FloatArray?`.

### Voice Activity Detection — `VADModule`
Uses a robust, adaptive energy-based voice detector, tuned through real on-device testing across quiet and noisy conditions (RMS threshold, discriminated against a speech-probability cutoff). A bundled Silero VAD v4 ONNX neural model is also present in the pipeline — real-device testing showed the energy-based approach gives more consistent results for this deployment today, so it's the active detector, with a self-healing path that automatically falls back to it if the neural session ever throws at runtime.

### Speech-to-Text — `STTModule` + `CtcDecoder`
Uses **AI4Bharat IndicConformer**, exported as per-language sherpa-onnx ONNX INT8 graphs (`stt_{lang}_int8.onnx` + `stt_{lang}_tokens.txt`). Each language is lazy-loaded and cached on first use. `SessionOptions`: 2 intra-op threads, `ALL_OPT`, attempts NNAPI delegate with a CPU/XNNPACK fallback.

Feature extraction is a hand-rolled 80-bin log-mel spectrogram pipeline (25ms frame / 10ms hop, Hann window), with per-feature mean/std normalization across the whole utterance, matching IndicConformer's NeMo training configuration. Decoding uses a from-scratch, unit-tested `CtcDecoder.greedyDecode()`. On failure the module returns a real `AppResult.Error` — it never fabricates placeholder text.

### Text-to-Speech — `TTSModule`
Uses **sherpa-onnx's `OfflineTts`** with real espeak-ng-phonemized VITS voices (Piper/Coqui/Mimic3):

| Language | Voice source |
| --- | --- |
| Hindi | Piper |
| Gujarati | Mimic3 |
| Malayalam | Piper |
| Bengali | Coqui |
| English | Piper |

Additional voices (Marathi, Kannada, Tamil, Telugu, Odia) are on the roadmap — see [Language Roadmap](#-language-roadmap). Output is resampled to 16kHz for playback.

### Playback — `AudioPlaybackManager`
Normal messages play via `AudioTrack` with `USAGE_MEDIA` for clear, full-volume audio. `ALERT`-type messages use `STREAM_ALARM` forced to max volume, attempt to flip ringer mode to bypass Do-Not-Disturb, and use `USAGE_ALARM` + `FLAG_AUDIBILITY_ENFORCED`.

---

## 🤖 On-Device AI Assistant

The "AI Tactical Assistant" screen has **two complementary answer sources**, with the UI always disclosing which one is currently active:

1. **Real LLM path** (`LlmModule`) — Microsoft **Phi-3-mini-4k-instruct**, GGUF q4 quantized, run via the `llamacpp-kotlin` bindings on supported devices (`arm64-v8a`/`x86_64`) once the optional ~2.39 GB model is downloaded. Context window 4096 tokens, generation bounded to **250 tokens max** with clean stop sequences for crisp, focused answers.
2. **Always-available fallback** (`TacticalAiEngine`) — a fast, curated keyword-matching responder across 9 languages (first-aid/disaster-protocol phrases), so the assistant is useful immediately, even before the optional model is downloaded.

**Language detection** (`LanguageDetector`) is a fast heuristic: it counts Unicode code points per Indic script block, disambiguates Hindi vs. Marathi via script-specific characters/keywords, and matches romanized ("Hinglish"-style) text for code-switch detection.

---

## 📡 Networking & Mesh Transport

### Wi-Fi Direct — `WifiDirectManager` (primary)
Registers a `WifiP2pBroadcastReceiver` for state/peers/connection-changed actions. Discovery is centrally owned by `MeshHardwareManager` for a smooth, single-source-of-truth peer radar. On group formation, the group owner starts a TCP server; the client connects to the owner's IP. Reconnection uses exponential backoff (`500ms × 2^attempt`, capped at 30s, up to **3 attempts**), after which the app automatically falls back to Bluetooth.

### Bluetooth Classic RFCOMM — `BluetoothRFCOMMManager` (fallback)
`listenUsingRfcommWithServiceRecord()` for hosting, `createRfcommSocketToServiceRecord()` for connecting, with the same length-prefixed framing as the Wi-Fi Direct transport.

### `SocketTransport` — TCP layer
Server on **port 8765**. Frame = 4-byte big-endian length prefix + raw Protobuf bytes (max 64KB/message). Runs a ping/ACK loop every 5s to measure per-peer round-trip latency, feeding the Radar screen and peer list.

### `MeshHardwareManager` — discovery/UI coordinator
Drives both Wi-Fi Direct peer discovery and classic Bluetooth device discovery together, and exposes a combined live peer list, hosting state, and group-role state that the Radar and Transceiver screens render.

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
  uint32      sequence   = 8;   // supports dedup/ordering
}
```

`ProtobufSerializer` builds a 4-byte big-endian length prefix and maps the generated `TransceiverProto` class to/from the domain `TransceiverMessage`.

---

## 🧩 Domain Model & Persistence

Clean separation between domain models, contracts, and infrastructure:

- **`PeerDevice`** — device identity, signal strength, connection type (Wi-Fi Direct / Bluetooth), latency, authorization state.
- **`TransceiverMessage`** (domain wrapper) — message type, text, source/destination language, sender, timestamp, confidence, sequence, plus a UI-only `direction: SENT|RECEIVED` field.
- **`DeviceProfile`** — device ID, display name, and a short human-readable callsign derived from the device fingerprint.
- **`AppResult<T>`** — a sealed `Success`/`Error`/`Loading` wrapper used throughout for honest, explicit error propagation.
- **`ModelPack`** — the full catalog of 23 downloadable model packs (STT/TTS/VAD/language-ID/AI Assistant); `coreTransceiverPacks()` returns the 17 packs that make up the compulsory Transceiver bundle.

**Persistence**: DataStore Preferences for the device profile, Room for the peer registry — peer authorization survives re-discovery and app restarts.

---

## 📦 Model Download & Integrity Pipeline

Models are fetched at runtime by `ModelDownloadManager` into `context.filesDir/models/` (aside from small always-available assets extracted on first launch). The Downloads screen tracks **23 model packs**, of which **17 form the "compulsory" core Transceiver bundle**.

**Flow:** `download(pack)` → OkHttp GET streamed in 32KB chunks to a `.part` file (resuming any already-good piece on retry) → SHA-256 verified against (1) a hash pinned in `ModelRegistry`, (2) HuggingFace's `X-Linked-ETag` header, correctly read from the redirect response, or (3) trust-on-first-download for sources with no published hash → on success, renamed to the final filename → archive bundles are extracted via `ArchiveExtractor` (tar.bz2, de-duplicating shared `espeak-ng-data/` copies) → `Downloaded`.

Concurrency is explicitly tuned (`maxRequests`/`maxRequestsPerHost` raised well above OkHttp's conservative default) so all 17 core packs can download in parallel without stalling.

**Model sources:**
- STT: AI4Bharat IndicConformer (sherpa-onnx export), Hugging Face — 9 Indic languages.
- TTS: `k2-fsa/sherpa-onnx` `tts-models` release — 5 languages (see table above).
- VAD: Silero VAD v4 ONNX, GitHub raw.
- AI Assistant: `microsoft/Phi-3-mini-4k-instruct-gguf` on Hugging Face.

---

## ⚙️ Foreground Service

`ITantraForegroundService` owns every audio/network/download module and is the real engine behind push-to-talk. It exposes a `PipelineStage` state machine (`IDLE → LISTENING → TRANSCRIBING → TRANSMITTING`, and `RECEIVING → SPEAKING` on the receive side) so the UI is never a black box about what's currently happening. `MainViewModel` starts and binds to it on `init`, so the whole pipeline is live as soon as the app launches.

---

## 🖥️ Screens

Bottom navigation, left to right: **Home → Radar → Radio (Transceiver, center hero button) → Downloads → Assistant.** `PeerSessionScreen` is a 6th route, reached by tapping a connected peer.

- **Home** — dashboard: node identity card (callsign, build ID), a "System Architecture" spec bento grid, and a profile-edit dialog. Also hosts the mandatory-callsign `OnboardingDialog`.
- **Transceiver** — the real operational screen: Host Beacon / Search Peers toggles, live peer list (Wi-Fi Direct + Bluetooth, merged), giant hold-to-talk PTT button wired to the foreground service, live pipeline-stage and connection-status indicators, language auto-detect toggle. Gated behind `ModelDownloadGate` until the core model bundle is downloaded.
- **PeerSessionScreen** — a focused per-peer session view; deeper pipeline integration is in progress (see [Roadmap](#-roadmap)).
- **Radar** — Canvas-drawn sweep visualizing peers by RSSI-derived distance.
- **AI Assistant** — chat UI for the on-device assistant described above, with an STT input-language picker and per-message speak/stop controls.
- **Downloads** — the model pack manager described above.

---

## 🌐 Language Roadmap

| Language | Speech-to-Text | Text-to-Speech |
| --- | --- | --- |
| Hindi | ✅ | ✅ |
| Gujarati | ✅ | ✅ |
| Bengali | ✅ | ✅ |
| Malayalam | ✅ | ✅ |
| English | ✅ | ✅ |
| Marathi | ✅ | 🔜 |
| Kannada | ✅ | 🔜 |
| Tamil | ✅ | 🔜 |
| Telugu | ✅ | 🔜 |
| Odia | 🔜 | 🔜 |

🔜 = actively being sourced — a free, high-quality offline voice/model for these hasn't been finalized yet.

---

## 🔐 Permissions

| Permission | Why |
| --- | --- |
| `RECORD_AUDIO` | Mic capture for STT/VAD |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` / `NEARBY_WIFI_DEVICES` (API 33+) | Required by Android for Wi-Fi Direct peer discovery |
| `BLUETOOTH*` (`CONNECT`, `SCAN`, `ADVERTISE`, legacy `BLUETOOTH`/`BLUETOOTH_ADMIN` ≤ API 30) | BT Classic RFCOMM discovery/pairing/transport |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE` | Persistent background engine |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `RECEIVE_BOOT_COMPLETED` | Keep the service alive |
| `MANAGE_NOTIFICATION_POLICY`, `WAKE_LOCK`, `VIBRATE` | ALERT playback: DND bypass attempt, screen/CPU wake |
| **`INTERNET`, `ACCESS_NETWORK_STATE`** | **One-time model downloads only** — all P2P communication is local Wi-Fi Direct / Bluetooth, no internet required post-download |

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
3. Open **Downloads**, tap "Download the Pack" to fetch the ~169 MB core bundle (VAD + STT + the available TTS voices + language-ID data). The optional 2.39 GB Phi-3 model is only needed for real LLM AI-Assistant replies.
4. Open **Transceiver** on both devices; one taps "Host Beacon", the other "Search Peers" and connects.
5. Hold the PTT button and speak — the transcribed text and synthesized reply appear/play on the other device.

---

## 🧪 Testing

Unit tests that exist today (`app/src/test/java/com/itantra/`):

| Test | Locks in |
| --- | --- |
| `CtcDecoderUnitTest` | Greedy CTC decode merges repeats, drops blanks, token-vocab parsing |
| `DomainModelUnitTest` | `IndicLanguage.fromCode()` mapping + unknown-code fallback to Hindi; `AlertTemplate` translations; `TransceiverMessage`/`PeerDevice` shapes |
| `HashUtilsUnitTest` | SHA-256 header normalization (quotes, `sha256:` prefix, case), rejects malformed/weak ETags, case-insensitive hash comparison |
| `LlmModuleUnitTest` | ABI gating — `arm64-v8a`/`x86_64` supported, `armeabi-v7a`-only devices correctly get no native LLM |

Complemented by extensive real two-device hardware testing for end-to-end integration confidence.

---

## 🎯 Engineering Highlights

The commit history documents genuine, iterative on-device engineering:

- Delivered fully real, on-device STT inference with correct CTC decoding and normalization
- Real TTS phonemization (sherpa-onnx), SHA-256-verified model integrity
- Wired real on-device Phi-3 inference into the AI Assistant (llama.cpp), with a bounded, focused generation window
- Wired the real PTT walkie-talkie transport end-to-end into the running app
- Resolved a Wi-Fi Direct discovery conflict and tuned VAD for reliable real-world performance
- Added a Bluetooth bonded-device picker and a real discovery + pairing flow
- Tuned the model download pipeline for reliable parallel downloads
- Verified a full two-device walkie-talkie session end-to-end, including live Host Beacon, connection, and pipeline-stage status in the UI

---

## 🗺️ Roadmap

- **Neural VAD upgrade** — the bundled Silero VAD model is already in the pipeline; further tuning is planned so it can take over from the current energy-based detector.
- **Full language voice coverage** — sourcing free, high-quality offline TTS voices for Marathi, Kannada, Tamil, Telugu, and Odia (STT + TTS).
- **Emergency / SOS broadcast screen** — a dedicated distress-signal UI; the underlying `ALERT` message type and alarm-volume/DND-bypass playback path already exist in the wire protocol and audio pipeline.
- **Deeper `PeerSessionScreen` integration** — connecting the per-peer session view directly to the live transceiver pipeline.
- **Persisted background downloads** — scheduling model downloads via WorkManager so they can survive process death.

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
