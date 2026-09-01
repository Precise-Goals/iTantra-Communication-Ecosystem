# Product Requirements Document: iTantra — Indian Multilingual TTS & STT Aided Neural Transceiver Radio Access

> **Problem Statement ID:** PS-26173 | **Organization:** ISRO / Department of Space
> **Category:** Software | **Theme:** Smart Automation
> **Version:** 2.0 | **Last Updated:** 2026-09-02

---

## 1. Executive Summary

**iTantra** is a fully offline, ultra-low-bandwidth Universal Communication Ecosystem for Android. It converts voice to text at the source using state-of-the-art Indic AI models, transmits a few bytes of structured data over ad-hoc Wi-Fi Direct or Bluetooth Classic, and reconstructs intelligible multilingual speech at the receiver — all without any internet connection, proprietary SDK, or audio streaming.

The system is purpose-built to serve ISRO's PS-26173 mandate: enabling inclusive, multilingual voice communication in alert and distress scenarios across low-bitrate links, catering to populations regardless of literacy level.

---

## 2. Product Vision & Goals

| Dimension | Goal |
|---|---|
| **Offline-First** | 100% pipeline runs on-device. Zero HTTP calls, zero cloud dependencies. |
| **Indic-Native** | AI models sourced from AI4Bharat — trained specifically on Indian speech corpora. |
| **Ultra-Efficient** | Text transmission (~200 bytes/message) replaces audio streaming (~16kB/s). |
| **Inclusive** | Audio output ensures access for non-literate users in their native language. |
| **Open-Source Compliant** | Strictly adheres to ISRO's open-source-only framework mandate. |

---

## 3. Target Users

1. **First Responders & Military Personnel** — Tactical, low-infrastructure communication in disaster zones and conflict areas.
2. **Emergency Broadcast Operators** — Distress alert dissemination across heterogeneous language groups.
3. **Rural Citizens** — Inclusive communication for non-literate populations via native-language audio output.
4. **Field Teams (e.g., ISRO ground crews)** — Mesh-network voice relay without GSM infrastructure.

---

## 4. Success Metrics (ISRO Evaluation Criteria)

### 4.1 Efficiency (20%)
| Metric | Target |
|---|---|
| Total APK Size (base + 2 default language packs) | < 250 MB |
| RAM Usage (active inference peak) | < 400 MB |
| RAM Usage (idle background service) | < 80 MB |
| CPU Usage (idle VAD listening) | < 5% on mid-range SoC |
| Battery drain per hour (idle) | < 2% |

### 4.2 Accuracy (40%)
| Metric | Target |
|---|---|
| STT Word Error Rate (WER) — Hindi/Bengali/Tamil | < 10% in quiet environment |
| STT WER — all 10 languages average | < 18% |
| TTS Mean Opinion Score (MOS) | > 3.8 / 5.0 |
| TTS Intelligibility (STOI score) | > 0.85 |

### 4.3 Latency (20%)
| Metric | Target |
|---|---|
| VAD pause detection latency | < 100 ms |
| STT inference time (per sentence) | < 800 ms on mid-range, < 1500 ms on low-end |
| Network text transmission latency (Wi-Fi Direct) | < 50 ms |
| TTS synthesis + playback initiation delay | < 500 ms |
| **End-to-end RTF** (word spoken → audio playing on receiver) | **< 2.5 seconds total** |

---

## 5. Technical Architecture

### 5.1 Core Pipeline (STT → Net → TTS)

```
[SENDER NODE]
  Microphone → AudioRecord (16kHz PCM)
             → Silero VAD (ONNX, ~2MB)                        ← pause/silence detection
             → IndicConformer STT (ONNX INT8, ~150–200MB)     ← AI4Bharat multilingual
             → Protobuf Frame Serializer
             → Wi-Fi Direct TCP Socket / BT RFCOMM

[TRANSPORT LAYER]
  Wire format: Protocol Buffers v3 (binary, ~200 bytes per message)
  Primary:     Wi-Fi Direct (WifiP2pManager) — TCP socket, ~50ms latency
  Fallback:    Bluetooth Classic RFCOMM — reliable, no router needed

[RECEIVER NODE]
  Socket Listener (Android Foreground Service)
  → Protobuf Frame Deserializer
  → IndicTTS / Piper TTS (AI4Bharat VITS-based, ONNX INT8)   ← multilingual synthesis
  → AudioTrack (16kHz, stereo playback)
  [If ALERT tag]: AudioManager override → MAX_VOLUME + DND bypass
```

### 5.2 AI Model Stack

| Component | Model | Source | Format | Quantization | Est. Size |
|---|---|---|---|---|---|
| **STT** | IndicConformer-Multilingual | AI4Bharat | ONNX | INT8 PTQ | ~150 MB |
| **TTS** | IndicTTS VITS (10 lang) | AI4Bharat | ONNX | INT8 PTQ | ~15–30 MB/lang |
| **VAD** | Silero VAD v4 | snakers4 | ONNX | FP16 | ~2 MB |
| **Runtime** | ONNX Runtime Mobile | Microsoft | AAR | — | ~8 MB |

#### Why AI4Bharat IndicConformer for STT?
- Trained on 17,000+ hours of verified Indic speech (IndicSUPERB, MUCS datasets)
- State-of-the-art WER for all 10 required Indian languages in a single multilingual model
- Language identity provided as audio-prefix token — no separate classifier model needed
- Official ONNX export pipeline available via AI4Bharat GitHub (Apache 2.0 License)
- Eliminates per-language model management complexity

#### Why AI4Bharat IndicTTS (VITS) for TTS?
- Purpose-built for Indian languages with natural prosody and intonation
- Official pre-trained VITS checkpoints for 13 Indian languages (MIT License)
- ONNX export enables ONNX Runtime Mobile acceleration (NNAPI/GPU delegates)
- INT8 quantization reduces per-language model from ~45MB → ~12–15MB

#### Why Silero VAD?
- ~2MB ONNX model; negligible memory footprint
- Real-time 100ms chunk processing at < 5% CPU on mid-range SoC
- Accurate in noisy field environments (trained on diverse noise profiles)
- MIT License; fully offline

### 5.3 ML Optimization Pipeline

All models undergo the following steps before inclusion in the APK:

```
1. AI4Bharat Checkpoint (.pt PyTorch)
        ↓
2. torch.onnx.export()  →  model.onnx
        ↓
3. onnxruntime.quantization.quantize_static()  →  model_int8.onnx
        ↓
4. ONNX Runtime Mobile serialization  →  model.with_runtime_opt.ort (optional)
        ↓
5. Validation on Android emulator (Pixel 5 API 31)
        ↓
6. WER / MOS regression benchmark gate (must pass before APK inclusion)
```

**Inference Delegate Priority:**
1. NNAPI (Android 8.1+ with DSP/NPU — fastest, lowest power)
2. GPU Delegate (OpenCL/Vulkan — mid-range devices)
3. XNNPACK CPU SIMD (universal fallback — always available)

### 5.4 Network Layer

#### Wi-Fi Direct (Primary)
- `WifiP2pManager` with `WifiP2pManager.ActionListener`
- Group Owner acts as TCP server on port **8765**
- Clients connect via group owner IP from `WifiP2pInfo`
- Peer discovery via `discoverPeers()` + `WIFI_P2P_PEERS_CHANGED_ACTION` broadcast
- Reconnection: exponential backoff (500ms → 30s max), auto-promotes to BT after 3 failures

#### Bluetooth Classic RFCOMM (Fallback)
- `BluetoothServerSocket` on UUID: `6E400001-B5A3-F393-E0A9-E50E24DCCA9E`
- `BluetoothSocket` for client connections
- Auto-activated after Wi-Fi Direct fails 3 consecutive retries

#### Wire Protocol — Protocol Buffers v3
```protobuf
syntax = "proto3";
package itantra;

message TransceiverMessage {
  enum MessageType {
    SPEECH = 0;
    ALERT  = 1;
    ACK    = 2;
    PING   = 3;
  }
  MessageType type       = 1;
  string      text       = 2;   // transcribed/transmitted text
  string      src_lang   = 3;   // BCP-47: hi, gu, mr, kn, ml, ta, te, or, bn, en
  string      dst_lang   = 4;   // receiver TTS synthesis language
  string      sender_id  = 5;   // device UUID (for auth traceability)
  int64       timestamp  = 6;   // epoch ms (used for RTF measurement)
  float       confidence = 7;   // STT confidence score (0.0–1.0)
}
```
**Rationale:** Protobuf binary is ~3× smaller than JSON, enabling efficient use of low-bitrate Bluetooth links. `protobuf-javalite` AAR adds < 200KB to APK.

### 5.5 Background Services Architecture

```
iTantraForegroundService  (Android Foreground Service)
├── NetworkModule          (Dispatchers.IO)
│   ├── WifiDirectManager
│   └── BluetoothRFCOMMManager
├── AudioCaptureModule     (Dedicated AudioRecord thread, 16kHz mono PCM_16BIT)
├── VADModule              (Silero ONNX — always resident, ~2MB)
├── STTModule              (IndicConformer ONNX — lazy-loaded on first use)
├── TTSModule              (IndicTTS ONNX — lazy-loaded, active session only)
└── StatePublisher         (SharedFlow<AppState> → ViewModel → Compose UI)
```

**Key Android APIs:**
- `startForeground()` with `FOREGROUND_SERVICE_TYPE_MICROPHONE`
- `AudioFocusRequest` (`AUDIOFOCUS_GAIN` for alerts, `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` for normal)
- `NotificationManager.setInterruptionFilter(INTERRUPTION_FILTER_ALL)` for DND override
- `AudioManager.setStreamVolume(STREAM_ALARM, MAX, 0)` for alert mode
- `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` for persistent background

---

## 6. Operating Modes

### 6.1 Push-to-Talk (Walkie-Talkie Mode)
- Hold PTT button → `AudioRecord.startRecording()` + VAD activated
- Release PTT → VAD flush → STT inference → Protobuf encode → TCP/BT send
- Half-duplex: sender/receiver roles mutually exclusive per button press
- Visual: microphone waveform animation during hold; confidence badge on release

### 6.2 Continuous Call (Phone Mode)
- VAD continuously monitors microphone for speech onset at 100ms intervals
- Speech detected → stream chunks into STT input buffer
- Pause > 800ms → STT inference triggered on accumulated audio buffer
- Full-duplex: both devices send/receive simultaneously via independent coroutine scopes

### 6.3 Alert / Distress Broadcast
- Dedicated SOS trigger (Page 3, hold 3 seconds to prevent accidental trigger)
- Message tagged `MessageType.ALERT` in Protobuf frame
- Receiver ALERT handling:
  1. `AudioManager` → `STREAM_ALARM` at MAX volume
  2. DND policy override via `MANAGE_NOTIFICATION_POLICY`
  3. `WakeLock` → screen forced on
  4. Non-dismissible full-screen `AlertDialog` overlay
  5. TTS plays non-interruptibly on `AudioTrack`
- All mesh nodes receive alert simultaneously via broadcast loop

---

## 7. Application Structure: 5-Screen Ecosystem

### Screen 1 — Central Command Dashboard
**Role:** System overview and mesh connection management hub.

| UI Element | Behaviour |
|---|---|
| Connection status card | Wi-Fi Direct active/inactive; BT fallback status |
| Discovered peers list | Animated list with RSSI signal strength and device name |
| Connect / Disconnect CTA | Triggers `WifiP2pManager.connect()` or BT pair flow |
| Active node count badge | Live count of connected mesh devices |
| Mode toggle pill | Switch between PTT / Phone mode globally |
| Battery optimization banner | Blocks app usage if battery optimization is still enabled |

### Screen 2 — Transceiver Interface (Primary Communication)
**Role:** Main send/receive communication hub for all modes.

| UI Element | Behaviour |
|---|---|
| Giant PTT button | Hold to talk (haptic + waveform animation); release triggers STT pipeline |
| PTT ↔ Phone Mode toggle | Slide toggle to switch between half-duplex and full-duplex |
| Live transcription log | Scrolling `[SENT]` / `[RECV]` text list with timestamps and confidence |
| Language indicator chips | Currently active STT input and TTS output languages |
| Connection health bar | Real-time RTT display (ms) |
| STT confidence bar | Per-message subtle color indicator of recognition confidence |

### Screen 3 — Emergency SOS Broadcast
**Role:** High-urgency, non-interruptible alert dissemination.

| UI Element | Behaviour |
|---|---|
| Red SOS button (hold 3s) | Anti-accidental-trigger; broadcasts `ALERT` Protobuf frame to all nodes |
| Predefined alert templates | "Medical Emergency", "Fire", "Structural Failure", "Evacuation" |
| Custom alert text field | Free-form distress message in selected language |
| Broadcast confirmation | Animated "Broadcasting to N nodes" with node count |
| Incoming alert overlay | Full-screen red overlay with TTS playback; non-dismissible until complete |

### Screen 4 — Language & Model Settings
**Role:** AI engine configuration and model management.

| UI Element | Behaviour |
|---|---|
| STT language picker | Select input language (10 Indic options) |
| TTS output language picker | Select synthesis language (can differ from STT language) |
| Language pack manager | Installed vs. available packs with file sizes; load/unload per language |
| Model status indicators | "Loaded" / "Loading" / "Error" state per active model |
| Delegate info card | Shows active inference delegate: NNAPI / GPU / XNNPACK |
| Live RAM usage bar | Real-time memory consumption of loaded ML models |

### Screen 5 — Mesh Radar
**Role:** Real-time network topology visualization.

| UI Element | Behaviour |
|---|---|
| Animated radar sweep (Compose Canvas) | Custom rotating sweep line with peer node ping dots |
| Peer nodes as radial dots | Distance from center proportional to RSSI signal strength |
| Connection type icon | Wi-Fi Direct (blue) or Bluetooth (amber) indicator per node |
| Tap on node | Expands detail: device name, connection latency, last message timestamp |
| Center node | Always represents "This Device" |

---

## 8. Language Coverage Matrix

| Language | BCP-47 | STT Model | TTS Model | Script |
|---|---|---|---|---|
| Hindi | `hi` | IndicConformer ✅ | IndicTTS VITS ✅ | Devanagari |
| Gujarati | `gu` | IndicConformer ✅ | IndicTTS VITS ✅ | Gujarati |
| Marathi | `mr` | IndicConformer ✅ | IndicTTS VITS ✅ | Devanagari |
| Kannada | `kn` | IndicConformer ✅ | IndicTTS VITS ✅ | Kannada |
| Malayalam | `ml` | IndicConformer ✅ | IndicTTS VITS ✅ | Malayalam |
| Tamil | `ta` | IndicConformer ✅ | IndicTTS VITS ✅ | Tamil |
| Telugu | `te` | IndicConformer ✅ | IndicTTS VITS ✅ | Telugu |
| Odia | `or` | IndicConformer ✅ | IndicTTS VITS ✅ | Odia |
| Bengali | `bn` | IndicConformer ✅ | IndicTTS VITS ✅ | Bengali |
| English | `en` | IndicConformer ✅ | Piper TTS fallback ✅ | Latin |

---

## 9. Inter-Module Interface Contract (Domain A ↔ Domain B)

All communication between the backend engine (Domain A — Gaurav) and UI shell (Domain B — Sarthak) occurs exclusively through Kotlin StateFlow / SharedFlow via shared ViewModels. **No direct Android Context references cross domain boundaries.**

```kotlin
sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    data class Error(val code: ErrorCode, val message: String) : AppResult<Nothing>()
    object Loading : AppResult<Nothing>()
}

enum class ErrorCode {
    MODEL_LOAD_FAILED, NETWORK_TIMEOUT, NETWORK_DROPPED,
    AUDIO_FOCUS_LOST, VAD_ERROR, STT_INFERENCE_FAILED,
    TTS_SYNTHESIS_FAILED, SOCKET_ERROR, PERMISSION_DENIED
}

interface NetworkCallbacks {
    fun onNodeDiscovered(device: PeerDevice)
    fun onNodeConnected(device: PeerDevice)
    fun onNodeDisconnected(deviceId: String)
    fun onTextReceived(message: TransceiverMessage)
    fun onNetworkError(error: AppResult.Error)
}

interface AudioCallbacks {
    fun onVADTriggered(isSpeech: Boolean)
    fun onSTTResult(result: AppResult<String>, confidence: Float)
    fun onTTSSynthesisComplete(durationMs: Long)
    fun onAudioError(error: AppResult.Error)
}
```

---

## 10. Performance Constraints & Thread Management

| Thread / Dispatcher | Responsibility |
|---|---|
| `Main Thread` | Compose UI rendering only |
| `Dispatchers.IO` | Network sockets, file I/O, model loading |
| `Dispatchers.Default` | ONNX inference (CPU-bound) |
| `AudioRecord thread` | Dedicated low-latency audio capture |
| `AudioTrack thread` | Dedicated audio playback |

**Memory Management:**
- STT model: lazy-loaded on first use, released after 30s idle
- TTS model: resident during active session, released on session close
- VAD model: always resident (~2MB)
- Only 1 active STT + 1 active TTS language context in memory simultaneously

---

## 11. Permissions Required

| Permission | Justification |
|---|---|
| `RECORD_AUDIO` | AudioRecord for STT capture |
| `ACCESS_FINE_LOCATION` | Required for Wi-Fi Direct peer discovery (Android 10+) |
| `ACCESS_WIFI_STATE` / `CHANGE_WIFI_STATE` | WifiP2pManager operations |
| `BLUETOOTH` / `BLUETOOTH_ADMIN` / `BLUETOOTH_CONNECT` | BT RFCOMM pairing and connection |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MICROPHONE` | Persistent background audio capture |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Prevent OS from killing background service |
| `MANAGE_NOTIFICATION_POLICY` | DND override for ALERT message playback |
| `WAKE_LOCK` | Screen-on during alert TTS playback |

**Security:** No `INTERNET` permission declared. All traffic is local peer-to-peer. Sender UUID included in every Protobuf frame for traceability.

---

## 12. Risk Register

| Risk | Severity | Mitigation |
|---|---|---|
| IndicConformer ONNX export not production-ready | High | Fallback to Whisper-multilingual-tiny ONNX if validation fails |
| IndicTTS ONNX export gaps for some languages | Medium | Piper TTS community voices; eSpeak-NG as last resort |
| INT8 quantization WER degradation > 5% | Medium | Use FP16 dynamic quantization for affected languages |
| Wi-Fi Direct group formation latency > 5s | Low | Pre-cache group owner role; auto-retry with BT prompt |
| NNAPI unavailable on low-end target device | Medium | XNNPACK CPU delegate always available as fallback |
| Foreground Service killed by battery optimization | Low | `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` + persistent notification |
| Protobuf AAR increases APK size | Low | `protobuf-javalite` (< 200KB) — acceptable trade-off |

---

## 13. Technical Constraints Summary

| Constraint | Requirement |
|---|---|
| Internet Usage | **Strictly prohibited** — no `INTERNET` permission |
| Proprietary SDKs | **Prohibited** — Apache 2.0 / MIT / GPL only |
| Cloud APIs | **Prohibited** — STT/TTS fully on-device |
| Minimum Android | **API 26 (Android 8.0 Oreo)** |
| Target Android | **API 35 (Android 15)** |
| Minimum Device RAM | **2 GB** |
| ML Runtime | **ONNX Runtime Mobile** |
| UI Framework | **Jetpack Compose** + XML Canvas (Mesh Radar) |
| Language | **Kotlin 100%** |
| Architecture | **MVVM + Clean Architecture** |

---

## 14. Open-Source Compliance

| Component | License | Source |
|---|---|---|
| IndicConformer STT | Apache 2.0 | github.com/AI4Bharat/IndicConformer |
| IndicTTS (VITS) | MIT | github.com/AI4Bharat/indic-tts |
| Silero VAD | MIT | github.com/snakers4/silero-vad |
| ONNX Runtime Mobile | MIT | github.com/microsoft/onnxruntime |
| Protobuf (javalite) | BSD-3 | github.com/protocolbuffers/protobuf |
| Jetpack Compose | Apache 2.0 | Android Open Source Project |
| Kotlin Coroutines | Apache 2.0 | JetBrains |

**No proprietary SDKs. No closed-source components. No internet-dependent APIs.**