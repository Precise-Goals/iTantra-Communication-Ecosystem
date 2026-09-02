# 🛰️ iTantra — Universal Communication Ecosystem

> **ISRO Smart India Hackathon | Problem Statement PS-26173**
> Indian Multilingual TTS & STT Aided Neural Transceiver Radio Access for Low Bitrate Links

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Platform: Android](https://img.shields.io/badge/Platform-Android%208.0+-green.svg)](https://developer.android.com)
[![Offline: 100%](https://img.shields.io/badge/Offline-100%25%20No%20Internet-critical.svg)]()
[![Languages: 10 Indic](https://img.shields.io/badge/Languages-10%20Indic-orange.svg)]()

---

## 📖 What is iTantra?

**iTantra** is a fully offline, AI-powered multilingual voice communication Android application designed for use in zero-connectivity environments — disaster zones, tactical field operations, rural areas without GSM infrastructure, and ISRO mission support.

Instead of streaming heavy audio (16kB/s+), iTantra converts speech to text locally using on-device Indic AI models, transmits just ~200 bytes of structured Protobuf data over ad-hoc Wi-Fi Direct or Bluetooth, and reconstructs intelligible natural-language speech at the receiver — in the listener's preferred language.

### Key Capabilities

| Feature                   | Details                                                                             |
| ------------------------- | ----------------------------------------------------------------------------------- |
| 🗣️ **10 Indic Languages** | Hindi, Gujarati, Marathi, Kannada, Malayalam, Tamil, Telugu, Odia, Bengali, English |
| 🧠 **On-Device STT**      | AI4Bharat IndicConformer (multilingual, ONNX INT8, ~150MB)                          |
| 🔊 **On-Device TTS**      | AI4Bharat IndicTTS VITS (ONNX INT8, ~12–15MB per language)                          |
| 🎙️ **Smart VAD**          | Silero VAD (ONNX, ~2MB) — real-time pause detection                                 |
| 📡 **Ad-Hoc Networking**  | Wi-Fi Direct (primary) + Bluetooth Classic RFCOMM (fallback)                        |
| 🚨 **Emergency Alerts**   | Non-interruptible SOS broadcast with DND override                                   |
| 🔋 **Low Power Design**   | < 5% CPU idle; < 80MB RAM when idle                                                 |
| 🔒 **100% Offline**       | No `INTERNET` permission. No cloud APIs. No proprietary SDKs.                       |

---

## 🏗️ Architecture Overview

```
┌──────────────────────────────────────────────────────────────┐
│                      SENDER DEVICE                            │
│  Microphone → AudioRecord (16kHz, PCM_16BIT)                 │
│            → Silero VAD (ONNX, 100ms chunks)                  │
│            → IndicConformer STT (ONNX INT8)                   │
│            → Protobuf v3 Frame Encoder                        │
│            → TCP Socket (Wi-Fi Direct) / RFCOMM (Bluetooth)  │
└──────────────────────────────────────────────────────────────┘
                          │  ~200 bytes
                          ▼
┌──────────────────────────────────────────────────────────────┐
│                    RECEIVER DEVICE                            │
│  Socket Listener (Foreground Service)                         │
│            → Protobuf v3 Frame Decoder                        │
│            → IndicTTS VITS (ONNX INT8) / Piper TTS            │
│            → AudioTrack (16kHz playback)                      │
│     [ALERT]→ STREAM_ALARM + Max Volume + DND Bypass           │
└──────────────────────────────────────────────────────────────┘
```

### Technology Stack

| Layer            | Technology                                        | Justification                                      |
| ---------------- | ------------------------------------------------- | -------------------------------------------------- |
| **Language**     | Kotlin 100%                                       | Modern, coroutines-native, Compose-compatible      |
| **UI**           | Jetpack Compose + XML Canvas                      | Declarative UI + custom radar visualization        |
| **Architecture** | MVVM + Clean Architecture                         | Domain A/B separation per team structure           |
| **ML Runtime**   | ONNX Runtime Mobile                               | Single runtime for all models; NNAPI/GPU delegates |
| **STT Model**    | AI4Bharat IndicConformer (multilingual ONNX INT8) | Best Indic WER, one model for all 10 languages     |
| **TTS Model**    | AI4Bharat IndicTTS VITS (ONNX INT8)               | Natural Indic voice, ONNX-exportable               |
| **VAD**          | Silero VAD v4 (ONNX FP16)                         | ~2MB, real-time, accurate in noisy environments    |
| **Network**      | WifiP2pManager + BluetoothRFCOMM                  | Ad-hoc, no router, aligns with ISRO spec           |
| **Wire Format**  | Protocol Buffers v3 (protobuf-javalite)           | ~3× smaller than JSON, efficient on BT links       |
| **Async**        | Kotlin Coroutines + StateFlow/SharedFlow          | Reactive, Compose-native, Domain A↔B contract      |
| **Background**   | Android Foreground Service                        | OS-resistant persistent audio capture              |

---

## 📁 Project Structure

```
iTantra/
├── app/
│   ├── src/main/
│   │   ├── java/com/itantra/
│   │   │   ├── core/                          # Domain A — Gaurav
│   │   │   │   ├── audio/
│   │   │   │   │   ├── AudioCaptureModule.kt   # AudioRecord wrapper
│   │   │   │   │   ├── VADModule.kt            # Silero VAD ONNX inference
│   │   │   │   │   ├── STTModule.kt            # IndicConformer ONNX inference
│   │   │   │   │   └── TTSModule.kt            # IndicTTS ONNX synthesis
│   │   │   │   ├── network/
│   │   │   │   │   ├── WifiDirectManager.kt    # WifiP2pManager wrapper
│   │   │   │   │   ├── BluetoothRFCOMMManager.kt
│   │   │   │   │   └── SocketTransport.kt      # TCP/RFCOMM send/receive
│   │   │   │   ├── proto/
│   │   │   │   │   └── itantra.proto           # Protobuf v3 schema
│   │   │   │   └── service/
│   │   │   │       └── iTantraForegroundService.kt
│   │   │   ├── domain/
│   │   │   │   ├── model/
│   │   │   │   │   ├── PeerDevice.kt
│   │   │   │   │   ├── TransceiverMessage.kt   # Protobuf wrapper data class
│   │   │   │   │   └── AppResult.kt            # Sealed Result<T> type
│   │   │   │   └── contracts/
│   │   │   │       ├── NetworkCallbacks.kt     # Interface: Domain A → B
│   │   │   │       └── AudioCallbacks.kt       # Interface: Domain A → B
│   │   │   └── ui/                            # Domain B — Sarthak
│   │   │       ├── MainViewModel.kt            # StateFlow hub
│   │   │       ├── navigation/
│   │   │       │   └── NavGraph.kt             # 5-screen navigation graph
│   │   │       ├── screen/
│   │   │       │   ├── DashboardScreen.kt      # Screen 1
│   │   │       │   ├── TransceiverScreen.kt    # Screen 2 (PTT + Phone mode)
│   │   │       │   ├── SOSScreen.kt            # Screen 3
│   │   │       │   ├── SettingsScreen.kt       # Screen 4
│   │   │       │   └── RadarScreen.kt          # Screen 5
│   │   │       └── component/
│   │   │           ├── PTTButton.kt            # Animated PTT hold button
│   │   │           ├── WaveformVisualizer.kt   # Audio waveform animation
│   │   │           ├── RadarCanvas.kt          # Compose Canvas radar sweep
│   │   │           ├── PeerNodeDot.kt
│   │   │           └── AlertOverlay.kt         # Non-dismissible SOS overlay
│   │   ├── assets/
│   │   │   └── models/
│   │   │       ├── silero_vad.onnx             # Always bundled (~2MB)
│   │   │       ├── indicconformer_int8.onnx    # STT multilingual (~150MB)
│   │   │       └── tts/
│   │   │           ├── hi_vits_int8.onnx       # Hindi TTS (~15MB)
│   │   │           └── en_vits_int8.onnx       # English TTS (~15MB)
│   │   ├── res/xml/
│   │   │   └── network_security_config.xml    # Blocks all cleartext internet
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── model-export/                              # Python scripts (Gaurav)
│   ├── export_indicconformer.py               # PT → ONNX export
│   ├── quantize_models.py                     # INT8 PTQ quantization
│   ├── validate_wer.py                        # WER regression test
│   └── requirements.txt
├── PRD.md                                     # This document
├── team.md                                    # Sprint plan
└── README.md                                  # This file
```

---

## 🚀 Getting Started

### Prerequisites

| Requirement               | Version                        |
| ------------------------- | ------------------------------ |
| Android Studio            | Hedgehog 2023.1.1+             |
| Android SDK               | API 35 (compile), API 26 (min) |
| Kotlin                    | 1.9.x                          |
| Gradle                    | 8.x                            |
| Python (for model export) | 3.10+                          |
| PyTorch                   | 2.1+                           |
| ONNX Runtime              | 1.17+                          |

### 1. Clone the Repository

```bash
git clone https://github.com/yourteam/itantra.git
cd itantra
```

### 2. Export and Quantize AI Models

Run the model export pipeline (requires ~8GB RAM and GPU recommended):

```bash
cd model-export
pip install -r requirements.txt

# Export IndicConformer STT to ONNX
python export_indicconformer.py --lang all --output ../app/src/main/assets/models/

# Quantize all models to INT8
python quantize_models.py --input ../app/src/main/assets/models/

# Validate WER (requires test audio samples)
python validate_wer.py --lang hi --audio-dir ./test_audio/
```

### 3. Build the Android App

```bash
# Open in Android Studio OR build from CLI:
./gradlew assembleDebug

# Install on connected device:
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 4. Two-Device Testing (Walkie-Talkie Demo)

1. Install the APK on **two Android devices** (API 26+)
2. On **Device A**: Open app → Dashboard → "Start as Sender" → Select language (e.g., Hindi)
3. On **Device B**: Open app → Dashboard → "Start as Receiver" → Select output language
4. **Device A** initiates Wi-Fi Direct discovery → **Device B** accepts connection
5. On **Device A**: Hold PTT button and speak in Hindi
6. Observe: text transcription appears on both devices; **Device B** plays synthesized speech

---

## 🧠 AI Model Details

### STT: AI4Bharat IndicConformer Multilingual

```
Architecture: Conformer (Convolution-Augmented Transformer)
Training Data: IndicSUPERB + MUCS + AI4Bharat internal corpus (17,000+ hours)
Languages: 10 Indic + English in a single model
ONNX Export: torch.onnx.export() with opset 17
Quantization: INT8 Post-Training (quantize_static)
Inference: ONNX Runtime Mobile with NNAPI delegate
Input: 16kHz mono PCM audio, mel-spectrogram features
Output: Token sequence → decoded text string
License: Apache 2.0
Repository: https://github.com/AI4Bharat/IndicConformer
```

**Performance Benchmarks (INT8 quantized):**

| Language | WER (target) | Inference Time (Snapdragon 778G) |
| -------- | ------------ | -------------------------------- |
| Hindi    | < 8%         | ~600ms/sentence                  |
| Bengali  | < 10%        | ~650ms/sentence                  |
| Tamil    | < 12%        | ~700ms/sentence                  |
| Gujarati | < 15%        | ~700ms/sentence                  |
| Others   | < 20%        | ~800ms/sentence                  |

### TTS: AI4Bharat IndicTTS (VITS-based)

```
Architecture: VITS (Variational Inference with adversarial learning for Text-to-Speech)
Languages: 13 Indian languages (10 required all covered)
ONNX Export: Custom export from AI4Bharat VITS checkpoint
Quantization: INT8 Post-Training
Output: 22kHz waveform → resampled to 16kHz for AudioTrack
License: MIT
Repository: https://github.com/AI4Bharat/indic-tts
```

### VAD: Silero VAD v4

```
Architecture: Custom LSTM/RNN trained on diverse noise profiles
Input: 16kHz audio, 100ms chunks (1600 samples)
Output: Speech probability [0.0–1.0] per chunk
Threshold: 0.5 (configurable)
Size: ~2MB (FP16 ONNX)
License: MIT
Repository: https://github.com/snakers4/silero-vad
```

---

## 📡 Network Protocol

### Wi-Fi Direct (Primary)

- Group Owner: TCP Server on port **8765**
- Discovery: `WifiP2pManager.discoverPeers()` with broadcast receiver
- Auto-reconnect: Exponential backoff (500ms → 30s), 3 retries before BT fallback

### Bluetooth RFCOMM (Fallback)

- UUID: `6E400001-B5A3-F393-E0A9-E50E24DCCA9E`
- Auto-promoted after 3 failed Wi-Fi Direct attempts

### Wire Format: Protocol Buffers v3

Every message transmitted is a serialized `TransceiverMessage` protobuf (~50–300 bytes):

```protobuf
message TransceiverMessage {
  MessageType type       = 1;  // SPEECH | ALERT | ACK | PING
  string      text       = 2;  // Transcribed text
  string      src_lang   = 3;  // Source language (BCP-47)
  string      dst_lang   = 4;  // Destination language (BCP-47)
  string      sender_id  = 5;  // Device UUID
  int64       timestamp  = 6;  // Unix epoch ms
  float       confidence = 7;  // STT confidence (0.0–1.0)
}
```

---

## ⚡ Performance Targets

| Metric                         | Target        | ISRO Weight      |
| ------------------------------ | ------------- | ---------------- |
| App APK Size                   | < 250 MB      | Efficiency (20%) |
| RAM (idle)                     | < 80 MB       | Efficiency (20%) |
| RAM (peak inference)           | < 400 MB      | Efficiency (20%) |
| CPU (idle VAD)                 | < 5%          | Efficiency (20%) |
| STT WER (Hindi)                | < 8%          | Accuracy (40%)   |
| STT WER (all 10 languages avg) | < 18%         | Accuracy (40%)   |
| TTS MOS Score                  | > 3.8 / 5.0   | Accuracy (40%)   |
| End-to-End RTF                 | < 2.5 seconds | Latency (20%)    |
| VAD detection                  | < 100 ms      | Latency (20%)    |

---

## 🛡️ Open-Source Compliance

**All components are strictly open-source. No internet permission is declared.**

| Component                   | License      |
| --------------------------- | ------------ |
| AI4Bharat IndicConformer    | Apache 2.0   |
| AI4Bharat IndicTTS          | MIT          |
| Silero VAD                  | MIT          |
| ONNX Runtime Mobile         | MIT          |
| Protocol Buffers (javalite) | BSD-3-Clause |
| Jetpack Compose             | Apache 2.0   |
| Kotlin Coroutines           | Apache 2.0   |

---

## 👥 Team Structure

| Developer   | Domain                | Responsibility                                                            |
| ----------- | --------------------- | ------------------------------------------------------------------------- |
| **Gaurav**  | Domain A — The Engine | Audio capture, VAD, STT/TTS inference, networking, background service     |
| **Sarthak** | Domain B — The Shell  | Jetpack Compose UI, navigation, ViewModels, state management, integration |

**Integration Protocol:**

- Domain A exposes only `SharedFlow<AppState>` and callback interfaces — never Android `Context`
- Domain B consumes flows via ViewModels — never calls ONNX or socket APIs directly
- Shared interfaces (`NetworkCallbacks`, `AudioCallbacks`, `AppResult<T>`) agreed upon in Sprint 1

---

## 📅 Development Sprints

| Sprint       | Duration | Deliverable                                                                           |
| ------------ | -------- | ------------------------------------------------------------------------------------- |
| **Sprint 1** | Week 1   | Foreground Service, Wi-Fi Direct discovery, BT RFCOMM, App skeleton, Navigation       |
| **Sprint 2** | Week 2   | Silero VAD + IndicConformer STT integration, socket text transmission, Transceiver UI |
| **Sprint 3** | Week 3   | IndicTTS synthesis, AudioTrack playback, ALERT override, SOS UI                       |
| **Sprint 4** | Week 4   | INT8 quantization, all 10 languages, RTF profiling, full integration testing          |

See [team.md](team.md) for detailed sprint breakdown per developer.

---

## 🔧 Build Configuration (Key Dependencies)

```kotlin
// app/build.gradle.kts
dependencies {
    // ONNX Runtime Mobile
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.17.0")

    // Protocol Buffers (lightweight Java lite)
    implementation("com.google.protobuf:protobuf-javalite:3.25.0")

    // Jetpack Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.6")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Lifecycle + ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
}
```

---

## 📋 Android Manifest Permissions

```xml
<!-- Audio -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />

<!-- Wi-Fi Direct -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />

<!-- Bluetooth -->
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />

<!-- Background & Battery -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />

<!-- Alert Mode -->
<uses-permission android:name="android.permission.MANAGE_NOTIFICATION_POLICY" />
<uses-permission android:name="android.permission.WAKE_LOCK" />

<!-- NOTE: No INTERNET permission — all communication is local P2P only -->
```

---

## 🧪 Testing Strategy

### Unit Tests

- `STTModuleTest.kt` — VAD trigger → STT output verification with known audio fixtures
- `ProtobufSerializerTest.kt` — encode/decode round-trip verification
- `NetworkReconnectTest.kt` — Wi-Fi Direct → BT fallback logic

### Integration Tests

- Two-device walkie-talkie loop test (physical hardware, 2 Android devices)
- RTF measurement: timestamp delta from `TransceiverMessage.timestamp` to AudioTrack play time
- ALERT override: verify DND bypass and max volume on receiver

### Benchmarks

- WER measured using standard IndicSUPERB test splits per language
- TTS MOS scored via UTMOS automatic MOS predictor
- CPU/RAM profiled with Android Profiler during idle VAD and active inference

---

## 📜 License

This project is built exclusively with open-source components. See individual component licenses above.
Application code: **Apache 2.0**

---

_Built for ISRO SIH PS-26173 | iTantra Universal Communication Ecosystem_
