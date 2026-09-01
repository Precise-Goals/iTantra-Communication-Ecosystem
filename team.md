# iTantra Development Sprint Plan (PS-26173)

> **Team:** Gaurav (Domain A — Engine) + Sarthak (Domain B — Shell)
> **Stack:** Kotlin · ONNX Runtime Mobile · IndicConformer STT · IndicTTS VITS · Silero VAD · Protobuf v3 · Jetpack Compose + XML Canvas · Wi-Fi Direct + BT RFCOMM

---

## Development Philosophy: Parallel Architecture

The project is strictly divided into two operational domains to guarantee zero development interference and maintain separation of concerns. Both domains develop concurrently across four targeted sprints.

| Domain | Nickname | Developer | Responsibility |
|---|---|---|---|
| **Domain A** | The Engine | **Gaurav** | Headless background services, ONNX ML inference (STT/TTS/VAD), networking (Wi-Fi Direct + BT RFCOMM), Protobuf serialization |
| **Domain B** | The Shell | **Sarthak** | Jetpack Compose UI, navigation graph, ViewModels, StateFlow consumption, Domain A integration |

**Iron Rules:**
1. Domain A **never** references Android `Context` for UI updates. All state flows upward via `SharedFlow<AppState>`.
2. Domain B **never** calls ONNX Runtime, socket APIs, or `AudioRecord` directly.
3. The shared interface contract (`NetworkCallbacks`, `AudioCallbacks`, `AppResult<T>`) is frozen after Sprint 1 and not changed unilaterally.

---

## Sprint 1 — System Foundation & Infrastructure
**Objective:** Core project skeleton, OS permissions, Foreground Service, and initial network discovery.
**Duration:** Week 1

### Domain A Tasks (Gaurav)

#### Task 1.1 — Persistent Foreground Service
- Create `iTantraForegroundService` extending `Service` with `startForeground()`
- Set `FOREGROUND_SERVICE_TYPE_MICROPHONE` in manifest
- Programmatically detect battery optimization status
- Request `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` on first launch
- Create persistent notification channel (`IMPORTANCE_LOW`) with status updates

#### Task 1.2 — Wi-Fi Direct Network Discovery
- Initialize `WifiP2pManager` via `getSystemService(WIFI_P2P_SERVICE)`
- Register `WifiDirectBroadcastReceiver` for:
  - `WIFI_P2P_STATE_CHANGED_ACTION`
  - `WIFI_P2P_PEERS_CHANGED_ACTION`
  - `WIFI_P2P_CONNECTION_CHANGED_ACTION`
- Implement `discoverPeers()` with `ActionListener` callbacks
- Expose `onNodeDiscovered(device: PeerDevice)` via `NetworkCallbacks` interface

#### Task 1.3 — Socket Architecture
- Group Owner: TCP `ServerSocket` on port **8765** (`Dispatchers.IO` coroutine)
- Client: `Socket(groupOwnerAddress, 8765)` post-connection
- Bluetooth RFCOMM fallback: `BluetoothServerSocket` on UUID `6E400001-B5A3-F393-E0A9-E50E24DCCA9E`
- Send/receive raw `ByteArray` (Protobuf-serialized); length-prefixed framing (4-byte Int header)
- Auto-promote to BT RFCOMM after 3 consecutive Wi-Fi Direct failures

#### Task 1.4 — Protobuf Schema Definition
- Define `itantra.proto` with `TransceiverMessage` (type, text, src_lang, dst_lang, sender_id, timestamp, confidence)
- Configure `protobuf-javalite` Gradle plugin
- Implement `ProtobufSerializer.kt` with `encode(message)` and `decode(bytes)` functions

### Domain B Tasks (Sarthak)

#### Task 1.1 — Android Project Skeleton
- Initialize Android Studio project: `com.itantra`, `minSdk 26`, `targetSdk 35`
- Configure Jetpack Compose BOM 2024.02+, Material 3, Navigation Compose
- Set up MVVM + Clean Architecture package structure (see README.md)
- Create shared `AppResult<T>` sealed class and `ErrorCode` enum

#### Task 1.2 — Onboarding & Permission UI
- Build multi-step onboarding `PermissionScreen` (Compose)
- Block app entry until all required permissions granted:
  - `RECORD_AUDIO`, `ACCESS_FINE_LOCATION`, `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`
- Show dedicated card for battery optimization with direct Settings deep-link
- Persist permission state in `DataStore<Preferences>`

#### Task 1.3 — Dashboard (Screen 1) & Mesh Radar (Screen 5) Scaffold
- Build `DashboardScreen` with:
  - Connection status card (Wi-Fi Direct / BT status chips)
  - `LazyColumn` peer list (dummy data initially)
  - Mode toggle pill (PTT / Phone Mode)
- Build `RadarScreen` scaffold with `RadarCanvas` Compose Canvas component:
  - Rotating sweep line animation (60fps, `infiniteTransition`)
  - Center dot = "This Device"
  - Placeholder peer dots at fixed positions
- Build `NavGraph.kt` with `NavHost` for all 5 screens

---

## Sprint 2 — STT Pipeline & Core Interfaces
**Objective:** Integrate offline STT, VAD, and build primary communication UI.
**Duration:** Week 2

### Domain A Tasks (Gaurav)

#### Task 2.1 — Audio Capture & Silero VAD
- Implement `AudioCaptureModule.kt`:
  - `AudioRecord(MIC, 16000, MONO, PCM_16BIT, bufferSize)` on a dedicated thread
  - Stream 100ms chunks (1600 samples) to `VADModule`
- Implement `VADModule.kt` (Silero VAD ONNX):
  - Load `silero_vad.onnx` from `assets/models/` via OrtEnvironment
  - Run inference per 100ms chunk; emit `onVADTriggered(isSpeech: Boolean)`
  - Accumulate speech audio; flush buffer on silence > 800ms
  - Configure NNAPI delegate → GPU delegate → XNNPACK fallback

#### Task 2.2 — IndicConformer STT Integration
- Implement `STTModule.kt`:
  - Lazy-load `indicconformer_int8.onnx` from assets on first STT request
  - Pre-process audio buffer: mel-spectrogram extraction (80-bin, 25ms window, 10ms shift)
  - Feed features into `OrtSession.run()` with input map
  - Post-process: CTC decode / greedy decode → UTF-8 text string
  - Emit `onSTTResult(result: AppResult<String>, confidence: Float)` via `AudioCallbacks`
  - Release session after 30s idle via coroutine `withTimeout`

#### Task 2.3 — Transmission Binding
- Wire `STTModule` output to `ProtobufSerializer.encode()` → socket send
- Construct `TransceiverMessage(type=SPEECH, text=..., src_lang=..., timestamp=System.currentTimeMillis())`
- Measure and log RTF delta: `System.currentTimeMillis() - TransceiverMessage.timestamp`

### Domain B Tasks (Sarthak)

#### Task 2.1 — Transceiver Interface (Screen 2)
- Build `TransceiverScreen.kt`:
  - `PTTButton` composable: `pointerInput` with `detectTapGestures(onPress)` for hold-to-talk
  - `WaveformVisualizer` composable: animated sine wave using `Canvas` API during PTT hold
  - `LazyColumn` transcription log: `[SENT]` / `[RECV]` items with color-coded backgrounds
  - Slide toggle: PTT ↔ Phone Mode with `AnimatedContent` transition
  - Language indicator chips (source → target language display)

#### Task 2.2 — Settings Module (Screen 4)
- Build `SettingsScreen.kt`:
  - Horizontal language picker wheels for STT input and TTS output (10 languages each)
  - Language pack list with installed/available status and file sizes
  - Model status card: "Loaded" / "Loading" / "Error" with animated status dot
  - Delegate info row: active inference backend (NNAPI / GPU / XNNPACK)
  - Live RAM usage `LinearProgressIndicator` (from `MainViewModel.ramUsageFlow`)

#### Task 2.3 — Mock State Management
- Create `MainViewModel.kt` with:
  - `StateFlow<List<PeerDevice>> peersFlow`
  - `StateFlow<List<TransceiverMessage>> messageLogFlow`
  - `StateFlow<ConnectionMode> modeFlow` (PTT / Phone)
  - `SharedFlow<AppResult.Error> errorFlow`
  - Mock implementations emitting fake data for UI development in parallel

---

## Sprint 3 — TTS Pipeline & Emergency Protocols
**Objective:** Implement Text-to-Speech and distress alerting.
**Duration:** Week 3

### Domain A Tasks (Gaurav)

#### Task 3.1 — IndicTTS VITS Integration
- Implement `TTSModule.kt`:
  - Lazy-load per-language ONNX model (e.g., `hi_vits_int8.onnx`) from assets
  - Pre-process input text: phonemizer / grapheme-to-phoneme (G2P) for target language
  - Run VITS ONNX inference: text features → waveform `FloatArray` (22kHz)
  - Resample 22kHz → 16kHz via linear interpolation for `AudioTrack` playback
  - Emit `onTTSSynthesisComplete(durationMs)` on completion

#### Task 3.2 — AudioTrack Playback
- Implement `AudioPlaybackManager.kt`:
  - `AudioTrack(STREAM_MUSIC, 16000, MONO, PCM_16BIT, bufferSize, STREAM_MODE)` for normal
  - `AudioTrack(STREAM_ALARM, ...)` for ALERT messages
  - `AudioFocusRequest` before playback; release on completion
  - Queue incoming synthesized audio chunks for gapless playback

#### Task 3.3 — Emergency Override Protocol
- Detect `MessageType.ALERT` in received `TransceiverMessage`
- Acquire `WakeLock` → turn screen on
- `AudioManager.setStreamVolume(STREAM_ALARM, MAX_VOLUME, 0)`
- `NotificationManager.setInterruptionFilter(INTERRUPTION_FILTER_ALL)` to bypass DND
- Emit alert state via `SharedFlow<AlertEvent>` to trigger UI overlay in Domain B

### Domain B Tasks (Sarthak)

#### Task 3.1 — Emergency SOS Screen (Screen 3)
- Build `SOSScreen.kt`:
  - Large circular red SOS button with `LongPressTimeout(3000ms)` gesture
  - `AnimatedVisibility` countdown ring during hold
  - Predefined alert template cards: "Medical Emergency", "Fire", "Structural Failure", "Evacuation"
  - Custom text field for free-form message in selected language
  - Animated broadcast status: pulsing "Broadcasting to N nodes" text

- Build `AlertOverlay.kt`:
  - Full-screen composable overlay triggered by `AlertEvent` from ViewModel
  - Bright red background with animated warning icon
  - Displays received alert text prominently
  - Non-dismissible until `onTTSSynthesisComplete` fires; then 2s auto-dismiss delay

#### Task 3.2 — UI Polish & Animations
- Implement `MaterialTheme` with iTantra color system:
  - Primary: Deep Space Blue (#0D1B2A)
  - Accent: Signal Orange (#FF6B35)
  - Alert: Distress Red (#D62828)
  - Success: Active Green (#06A77D)
- Add `AnimatedVisibility`, `animateColorAsState`, and `springSpec` transitions throughout
- Ensure 60fps on low-end devices (no overdraw, no main-thread blocking)

#### Task 3.3 — Integration Preparation
- Finalize all `data class` and `sealed class` shared types
- Publish `MainViewModel` interface that Domain A will bind to in Sprint 4
- Replace mock VAD trigger with actual `AudioCallbacks.onVADTriggered` hookup
- End-to-end text path test: manual text input → Protobuf → socket → receive → TTS (bypass STT)

---

## Sprint 4 — Integration, Optimization & End-to-End Testing
**Objective:** Unify Domain A + B, optimize for ISRO evaluation metrics, verify on physical hardware.
**Duration:** Week 4

### Domain A Tasks (Gaurav)

#### Task 4.1 — INT8 Quantization & Model Optimization
- Run `quantize_models.py` for IndicConformer + all 10 IndicTTS language models
- Benchmark quantization accuracy: ensure WER degradation < 5% vs. FP32 baseline
- Use FP16 dynamic quantization for any language where INT8 degrades WER > 5%
- Run ONNX Model Optimization Tool (`ort_optimizer.py`) for mobile graph optimizations
- Validate model size: STT ≤ 200MB, each TTS language ≤ 20MB

#### Task 4.2 — Multilingual Expansion (All 10 Languages)
- Export and quantize IndicTTS VITS models for: gu, mr, kn, ml, ta, te, or, bn
- Verify G2P phonemizer works for all 8 Indic scripts in `TTSModule`
- Run WER validation via `validate_wer.py` on IndicSUPERB test splits for all 10 languages
- Document per-language WER and RTF in `latency_report.md`

#### Task 4.3 — Latency Profiling & RTF Documentation
- Instrument pipeline with `System.nanoTime()` at each stage:
  - `T1`: VAD speech onset detected
  - `T2`: STT inference complete
  - `T3`: Protobuf encoded + socket send complete
  - `T4`: Socket received on peer device
  - `T5`: TTS synthesis complete
  - `T6`: AudioTrack playback started
- Compute RTF = `(T6 - T1) / audio_duration_ms`
- Target: RTF < 2.5s end-to-end
- Profile CPU/RAM with Android Profiler; capture idle VAD CPU% and peak inference RAM

### Domain B Tasks (Sarthak)

#### Task 4.1 — Module Binding (Domain A → B Integration)
- Replace all mock ViewModels with live StateFlow/SharedFlow connections
- Bind `iTantraForegroundService` to Activity via `ServiceConnection` + `Binder`
- Connect `NetworkCallbacks` → `MainViewModel.peersFlow` and `messageLogFlow`
- Connect `AudioCallbacks` → `MainViewModel.transcriptionFlow` and `alertFlow`
- Update `RadarScreen` radar dots from real `peersFlow` data (RSSI-based radial positioning)

#### Task 4.2 — Error Handling & Edge Cases
- Implement `AppResult.Error` → Snackbar/Dialog for all `ErrorCode` values:
  - `MODEL_LOAD_FAILED` → "Model unavailable. Check storage." + reload CTA
  - `NETWORK_DROPPED` → "Connection lost. Reconnecting..." + animated spinner
  - `AUDIO_FOCUS_LOST` → "Audio focus lost. Pausing." + auto-resume on regain
  - `PERMISSION_DENIED` → redirect to onboarding permission screen
- Implement graceful degradation: if NNAPI fails → UI shows "CPU mode active" badge

#### Task 4.3 — End-to-End Hardware Testing
- **Walkie-Talkie Loop Test:** 2 physical Android devices (API 26+), different manufacturers
  - Speak in Hindi on Device A → verify text on both → verify Hindi TTS on Device B
  - Measure actual RTF delta from `TransceiverMessage.timestamp` to `AudioTrack.play()` call
- **Alert Override Test:**
  - Set Device B to silent mode + DND
  - Trigger SOS from Device A
  - Verify Device B plays at max volume with screen on and non-dismissible overlay
- **Bluetooth Fallback Test:**
  - Disable Wi-Fi Direct manually mid-session
  - Verify auto-promotion to BT RFCOMM within 3 retries
- **Low-End Device Test:**
  - Run on API 26, 2GB RAM device
  - Verify STT inference completes < 1500ms; TTS synthesis < 800ms
  - Verify CPU idle < 5% in VAD-only mode

---

## Integration Protocol (Sprint 1 Contract — Non-Negotiable)

```kotlin
// Shared interface — Domain A implements, Domain B consumes via ViewModel

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

**Rules:**
1. **No Web Dependencies:** Zero HTTP calls, web sockets, or cloud SDK usage in any domain.
2. **No UI in the Engine:** Domain A must never reference `Context` for UI operations.
3. **No ML in the Shell:** Domain B must never instantiate `OrtEnvironment` or `OrtSession`.
4. **Frozen Contract:** `NetworkCallbacks`, `AudioCallbacks`, and `AppResult<T>` signatures are frozen post-Sprint 1. Changes require both developers' sign-off.
5. **Thread Safety:** Domain A emits on `Dispatchers.IO` / `Dispatchers.Default`. Domain B collects on `Dispatchers.Main` via `flowOn(Dispatchers.Main)`.

---

## Shared Data Models

```kotlin
// domain/model/PeerDevice.kt
data class PeerDevice(
    val deviceId: String,        // MAC address or UUID
    val deviceName: String,
    val rssi: Int,               // Signal strength (dBm)
    val connectionType: ConnectionType,  // WIFI_DIRECT | BLUETOOTH
    val latencyMs: Long,         // Last measured RTT
    val lastMessageAt: Long      // Epoch ms of last received message
)

enum class ConnectionType { WIFI_DIRECT, BLUETOOTH }

// domain/model/TransceiverMessage.kt  (wraps Protobuf generated class)
data class TransceiverMessage(
    val type: MessageType,
    val text: String,
    val srcLang: String,
    val dstLang: String,
    val senderId: String,
    val timestamp: Long,
    val confidence: Float,
    val direction: Direction      // SENT | RECEIVED (UI only, not in proto)
)

enum class MessageType { SPEECH, ALERT, ACK, PING }
enum class Direction { SENT, RECEIVED }

// domain/model/AppResult.kt
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
```

---

## Milestone Checklist

### Sprint 1 Exit Criteria
- [ ] App installs on API 26 device without crash
- [ ] Foreground Service runs persistently with visible notification
- [ ] Wi-Fi Direct peer discovery shows at least 1 discovered device in Dashboard UI
- [ ] TCP socket sends/receives raw string between two devices
- [ ] Battery optimization permission granted and persisted

### Sprint 2 Exit Criteria
- [ ] Silero VAD detects speech/silence on 100ms audio chunks
- [ ] IndicConformer STT produces Hindi text from recorded speech (offline)
- [ ] STT output text transmitted via Protobuf over TCP to peer device
- [ ] Transceiver Screen PTT button triggers STT pipeline end-to-end
- [ ] Text log shows `[SENT]` item on sender and `[RECV]` item on receiver

### Sprint 3 Exit Criteria
- [ ] IndicTTS synthesizes Hindi + English speech from received text
- [ ] AudioTrack plays synthesized audio on receiver device
- [ ] ALERT message triggers max-volume playback and non-dismissible overlay
- [ ] DND override confirmed functional on receiver in silent mode
- [ ] SOS Screen broadcasts alert to both connected devices

### Sprint 4 Exit Criteria
- [ ] All 10 languages (STT + TTS) functional and validated
- [ ] INT8 quantized models pass WER regression gate (< 5% accuracy drop)
- [ ] End-to-end RTF < 2.5 seconds measured on physical hardware
- [ ] Wi-Fi Direct → BT fallback verified in hardware test
- [ ] CPU idle (VAD only) < 5% on mid-range Snapdragon device
- [ ] RAM peak (active inference) < 400 MB on 2GB device
- [ ] `latency_report.md` completed with per-language benchmark data
