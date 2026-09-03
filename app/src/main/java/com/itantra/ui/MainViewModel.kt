package com.itantra.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.itantra.core.ai.LanguageDetector
import com.itantra.core.ai.TacticalAiEngine
import com.itantra.core.audio.AudioCaptureManager
import com.itantra.core.audio.AudioPlaybackManager
import com.itantra.core.audio.STTModule
import com.itantra.core.audio.TTSModule
import com.itantra.core.audio.VADModule
import com.itantra.core.download.ModelDownloadManager
import com.itantra.core.network.MeshHardwareManager
import com.itantra.data.DeviceProfileRepository
import com.itantra.data.PeerRegistryRepository
import com.itantra.domain.contracts.AudioCallbacks
import com.itantra.domain.model.AppResult
import com.itantra.domain.model.DeviceProfile
import com.itantra.domain.model.Direction
import com.itantra.domain.model.DownloadState
import com.itantra.domain.model.MessageType
import com.itantra.domain.model.ModelPack
import com.itantra.domain.model.PeerDevice
import com.itantra.domain.model.TransceiverMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val profileRepo = DeviceProfileRepository(application)
    private val peerRegistry = PeerRegistryRepository(application)
    val downloadManager = ModelDownloadManager(application)
    private val meshHardwareManager = MeshHardwareManager(application)

    // ── Device Profile State ──────────────────────────────────────────
    val deviceProfile: StateFlow<DeviceProfile?> = profileRepo.profileFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun saveDisplayName(name: String) {
        viewModelScope.launch { profileRepo.saveDisplayName(name) }
    }

    // ── Download States ───────────────────────────────────────────────
    val downloadStates: StateFlow<Map<ModelPack, DownloadState>> =
        downloadManager.downloadStates

    fun downloadPack(pack: ModelPack) = downloadManager.download(pack)
    fun downloadModel(pack: ModelPack) = downloadManager.download(pack)
    fun downloadAll(packs: List<ModelPack>) = downloadManager.downloadAll(packs)
    fun downloadCorePacks() = downloadManager.downloadAll(ModelPack.coreTransceiverPacks())
    fun cancelDownload(pack: ModelPack) = downloadManager.cancel(pack)
    fun deleteModel(pack: ModelPack) = downloadManager.delete(pack)
    fun modelPath(pack: ModelPack) = downloadManager.modelPath(pack)

    // ── Language Detection ────────────────────────────────────────────
    private val _detectedLanguage = MutableStateFlow<String?>(null)
    val detectedLanguage: StateFlow<String?> = _detectedLanguage.asStateFlow()

    private val _isAutoDetectEnabled = MutableStateFlow(true)
    val isAutoDetectEnabled: StateFlow<Boolean> = _isAutoDetectEnabled.asStateFlow()

    private val _selectedLanguage = MutableStateFlow("hi") // BCP-47 code
    val selectedLanguage: StateFlow<String> = _selectedLanguage.asStateFlow()

    fun setAutoDetect(enabled: Boolean) { _isAutoDetectEnabled.value = enabled }

    fun setManualLanguage(bcp47Code: String) {
        _selectedLanguage.value = bcp47Code
        if (!_isAutoDetectEnabled.value) {
            _detectedLanguage.value = bcp47Code
        }
    }

    // ── Hardware Mesh Networking (Wi-Fi Direct & BLE) ────────────────
    val isHosting: StateFlow<Boolean> = meshHardwareManager.isHosting
    val isDiscovering: StateFlow<Boolean> = meshHardwareManager.isDiscovering

    // Known peers combined with live hardware broadcast receiver discovered nodes
    val knownPeers: StateFlow<List<PeerDevice>> = combine(
        meshHardwareManager.liveDiscoveredPeers,
        peerRegistry.peers
    ) { live, saved ->
        val merged = (live + saved).distinctBy { it.deviceId }
        merged
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setHosting(enabled: Boolean) {
        if (enabled) meshHardwareManager.startHostBeacon()
        else meshHardwareManager.stopHostBeacon()
    }

    fun isWifiEnabled(): Boolean = meshHardwareManager.isWifiEnabled()
    fun isBluetoothEnabled(): Boolean = meshHardwareManager.isBluetoothEnabled()

    fun setDiscovering(enabled: Boolean) {
        if (enabled) meshHardwareManager.startPeerDiscovery()
        else meshHardwareManager.stopPeerDiscovery()
    }

    fun authorizePeer(deviceId: String) {
        viewModelScope.launch { peerRegistry.authorizePeer(deviceId) }
    }

    fun revokePeer(deviceId: String) {
        viewModelScope.launch { peerRegistry.revokePeer(deviceId) }
    }

    fun connectToPeer(peer: PeerDevice) {
        meshHardwareManager.connectToPeer(peer)
    }

    // ── 100% OFFLINE STT & TTS PIPELINE (Zero Google APIs) ───────────
    private val audioCallbacks = object : AudioCallbacks {
        override fun onVADTriggered(isSpeech: Boolean, probability: Float) {}
        override fun onSTTResult(result: AppResult<String>, confidence: Float, inferenceMs: Long) {}
        override fun onTTSSynthesisComplete(durationMs: Long) { _isSpeaking.value = false }
        override fun onAudioError(error: AppResult.Error) {
            Log.w("MainViewModel", "Audio module notice: ${error.message}")
            _isSpeaking.value = false
        }
        override fun onAudioFocusChanged(gained: Boolean) {}
    }

    private val vadModule = VADModule(application, audioCallbacks)
    private val sttModule = STTModule(application, audioCallbacks)
    private val ttsModule = TTSModule(application, audioCallbacks)
    private val audioPlayback = AudioPlaybackManager(application, audioCallbacks)
    private val audioCapture = AudioCaptureManager(
        vadModule = vadModule,
        sttModule = sttModule,
        callbacks = audioCallbacks,
        onSpeechReady = { pcm, lang ->
            // Callback runs on audio thread — launch coroutine for suspend call
            viewModelScope.launch(Dispatchers.Default) {
                sttModule.ensureLoaded()
                val result = sttModule.transcribe(pcm, lang)
                if (result is AppResult.Success && result.data.isNotBlank()) {
                    // If in Phone mode or PTT mode, broadcast over mesh; else feed to AI assistant
                    if (_isPhoneMode.value || _isPttTransmitting.value) {
                        broadcastPttMessage(result.data, lang)
                    } else {
                        launch(Dispatchers.Main) { sendAiMessage(result.data) }
                    }
                }
            }
        }
    )

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _isVoiceMuted = MutableStateFlow(false)
    val isVoiceMuted: StateFlow<Boolean> = _isVoiceMuted.asStateFlow()

    private val _isRecordingVoice = MutableStateFlow(false)
    val isRecordingVoice: StateFlow<Boolean> = _isRecordingVoice.asStateFlow()

    // ── Phone Mode (Hands-Free Full Duplex VAD) ──────────────────────────
    private val _isPhoneMode = MutableStateFlow(false)
    val isPhoneMode: StateFlow<Boolean> = _isPhoneMode.asStateFlow()

    fun setPhoneMode(enabled: Boolean) {
        _isPhoneMode.value = enabled
        if (enabled) {
            audioCapture.currentMode = AudioCaptureManager.Mode.PHONE_MODE
            audioCapture.currentLanguage = _selectedLanguage.value
            viewModelScope.launch(Dispatchers.Default) {
                vadModule.initialize()
                if (!audioCapture.isRunning) {
                    audioCapture.startCapture()
                }
            }
            Log.i("MainViewModel", "Phone Mode (Hands-Free VAD) activated")
        } else {
            audioCapture.currentMode = AudioCaptureManager.Mode.PUSH_TO_TALK
            if (!_isPttTransmitting.value) {
                audioCapture.stopCapture()
            }
            Log.i("MainViewModel", "Switched to Push-to-Talk (PTT) Mode")
        }
    }

    // ── PTT Radio Transmission State ─────────────────────────────────────
    private val _isPttTransmitting = MutableStateFlow(false)
    val isPttTransmitting: StateFlow<Boolean> = _isPttTransmitting.asStateFlow()

    private val _pttMessageLog = MutableStateFlow<List<TransceiverMessage>>(emptyList())
    val pttMessageLog: StateFlow<List<TransceiverMessage>> = _pttMessageLog.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            TacticalAiEngine.ensureLoaded(application)
            sttModule.ensureLoaded()
        }

        // Connect live mesh transport callbacks for peer data and sound streaming
        meshHardwareManager.onMessageReceivedListener = { incomingMsg ->
            viewModelScope.launch(Dispatchers.Main) {
                _pttMessageLog.value = (_pttMessageLog.value + incomingMsg.copy(direction = Direction.RECEIVED)).takeLast(50)
                // Speak received text aloud directly in sender's stamped language via AudioPlaybackManager
                audioPlayback.playIncomingMessage(incomingMsg, ttsModule)
            }
        }
    }

    /**
     * Start PTT radio transmission.
     * Begins audio capture → VAD → STT pipeline.
     * When speech ends, STT result is broadcast over the mesh network.
     */
    fun startPttTransmit() {
        if (_isPttTransmitting.value) return
        _isPttTransmitting.value = true
        _isRecordingVoice.value = true
        audioCapture.currentMode = AudioCaptureManager.Mode.PUSH_TO_TALK
        audioCapture.currentLanguage = _selectedLanguage.value
        viewModelScope.launch(Dispatchers.Default) {
            vadModule.initialize()
            if (!audioCapture.isRunning) {
                audioCapture.startCapture()
            }
        }
        Log.d("MainViewModel", "PTT transmit started")
    }

    /**
     * Stop PTT radio transmission.
     * Flushes remaining audio buffer → STT → broadcasts final frame.
     */
    fun stopPttTransmit() {
        if (!_isPttTransmitting.value) return
        _isRecordingVoice.value = false
        viewModelScope.launch(Dispatchers.Default) {
            val pcm = audioCapture.flushAndTranscribe()
            if (!_isPhoneMode.value) {
                audioCapture.stopCapture()
            }
            _isPttTransmitting.value = false

            if (pcm != null && pcm.isNotEmpty()) {
                sttModule.ensureLoaded()
                val result = sttModule.transcribe(pcm, _selectedLanguage.value)
                if (result is AppResult.Success && result.data.isNotBlank()) {
                    broadcastPttMessage(result.data, _selectedLanguage.value)
                }
            }
        }
        Log.d("MainViewModel", "PTT transmit stopped")
    }

    /**
     * Broadcast an emergency SOS alert to all connected peers.
     * Forces ALERT message type with maximum alarm volume override on receiver.
     */
    fun broadcastAlert(text: String = "EMERGENCY DISTRESS SOS ALERT") {
        val msg = TransceiverMessage(
            type = MessageType.ALERT,
            text = text,
            srcLang = _selectedLanguage.value,
            dstLang = _selectedLanguage.value,
            senderId = MeshHardwareManager.HARDWARE_DEVICE_NAME,
            timestamp = System.currentTimeMillis(),
            confidence = 1.0f,
            direction = Direction.SENT
        )
        _pttMessageLog.value = (_pttMessageLog.value + msg).takeLast(50)
        meshHardwareManager.broadcastMessage(msg)
        Log.i("MainViewModel", "Broadcasted high-priority ALERT: '$text'")
    }

    /**
     * Broadcast a transcribed message over the mesh network.
     * Encodes as TransceiverMessage and hands off to MeshHardwareManager socket transport.
     */
    private fun broadcastPttMessage(text: String, langCode: String) {
        val msg = TransceiverMessage(
            type = MessageType.SPEECH,
            text = text,
            srcLang = langCode,
            dstLang = langCode,
            senderId = MeshHardwareManager.HARDWARE_DEVICE_NAME,
            timestamp = System.currentTimeMillis(),
            confidence = 0.85f,
            direction = Direction.SENT
        )
        // Log to PTT message log for UI display
        _pttMessageLog.value = (_pttMessageLog.value + msg).takeLast(50)
        // Broadcast over Wi-Fi Direct / BT mesh
        meshHardwareManager.broadcastMessage(msg)
        Log.d("MainViewModel", "PTT broadcast: '$text' [$langCode]")
    }

    fun toggleVoiceMute() {
        _isVoiceMuted.value = !_isVoiceMuted.value
    }

    fun stopSpeaking() {
        _isSpeaking.value = false
    }

    /**
     * Synthesizes and plays speech using the on-device IndicTTS/Piper ONNX model + AudioTrack.
     * Strictly 100% offline — zero Google Speech / Cloud services.
     */
    fun speakAiResponse(text: String, preferredLang: String? = null) {
        if (_isVoiceMuted.value) return
        val detection = LanguageDetector.detect(text)
        val lang = preferredLang ?: detection.languageCode

        viewModelScope.launch(Dispatchers.Default) {
            _isSpeaking.value = true
            val waveform = ttsModule.synthesize(text, lang)
            if (waveform != null && waveform.isNotEmpty()) {
                audioPlayback.play(waveform)
            } else {
                _isSpeaking.value = false
            }
        }
    }

    /**
     * Offline Microphone Recording -> VAD -> IndicConformer STT.
     * Tapping mic starts capture; releasing flushes buffer to STT and feeds AI.
     */
    fun startAssistantRecording() {
        if (!audioCapture.isRunning) {
            _isRecordingVoice.value = true
            audioCapture.startCapture()
        }
    }

    fun stopAssistantRecording() {
        if (audioCapture.isRunning) {
            _isRecordingVoice.value = false
            viewModelScope.launch(Dispatchers.Default) {
                val pcm = audioCapture.flushAndTranscribe()
                audioCapture.stopCapture()

                if (pcm != null && pcm.isNotEmpty()) {
                    sttModule.ensureLoaded()
                    val result = sttModule.transcribe(pcm, _selectedLanguage.value)
                    val transcribed = if (result is AppResult.Success) result.data else ""
                    if (transcribed.isNotBlank()) {
                        launch(Dispatchers.Main) {
                            sendAiMessage(transcribed)
                        }
                    }
                }
            }
        }
    }

    // ── Real Intelligent AI Assistant ─────────────────────────────────
    private val _aiMessages = MutableStateFlow<List<AiMessage>>(listOf(
        AiMessage(
            text = "Hey, how may I support you?",
            isUser = false
        )
    ))
    val aiMessages: StateFlow<List<AiMessage>> = _aiMessages.asStateFlow()

    private val _isAiThinking = MutableStateFlow(false)
    val isAiThinking: StateFlow<Boolean> = _isAiThinking.asStateFlow()

    fun sendAiMessage(text: String) {
        if (text.isBlank()) return
        val userMsg = AiMessage(text = text.trim(), isUser = true)
        _aiMessages.value = _aiMessages.value + userMsg

        // Auto-detect language & dialect (including Hinglish)
        val detection = LanguageDetector.detect(text)
        if (_isAutoDetectEnabled.value) {
            _detectedLanguage.value = detection.languageCode
        }

        _isAiThinking.value = true
        viewModelScope.launch {
            kotlinx.coroutines.delay(180)
            val fullReply = TacticalAiEngine.generateResponse(text.trim(), getApplication())
            _isAiThinking.value = false

            val assistantMsgId = UUID.randomUUID().toString()
            val initialAssistantMsg = AiMessage(id = assistantMsgId, text = "", isUser = false)
            _aiMessages.value = _aiMessages.value + initialAssistantMsg

            // Fluid word-by-word streaming
            val words = fullReply.split(" ")
            val accumulated = StringBuilder()

            for (i in words.indices) {
                accumulated.append(words[i])
                if (i < words.size - 1) accumulated.append(" ")
                val currentChunk = accumulated.toString()

                _aiMessages.value = _aiMessages.value.map { msg ->
                    if (msg.id == assistantMsgId) msg.copy(text = currentChunk) else msg
                }
                kotlinx.coroutines.delay(18)
            }

            // Immediately pipe generation output into local ONNX TTS engine in its actual output language
            speakAiResponse(fullReply)
        }
    }

    fun sendAiQuery(text: String) = sendAiMessage(text)

    fun clearAiChat() {
        stopSpeaking()
        _aiMessages.value = listOf(
            AiMessage(
                text = "Hey, how may I support you?",
                isUser = false
            )
        )
    }

    override fun onCleared() {
        super.onCleared()
        _isPttTransmitting.value = false
        meshHardwareManager.release()
        ttsModule.release()
        sttModule.release()
        audioCapture.stopCapture()
        downloadManager.refreshStates()
    }
}

data class AiMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
