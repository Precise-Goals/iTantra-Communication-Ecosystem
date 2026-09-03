package com.itantra.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.itantra.core.ai.LanguageDetector
import com.itantra.core.ai.LlmModule
import com.itantra.core.ai.TacticalAiEngine
import com.itantra.core.audio.AudioCaptureModule
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
import com.itantra.domain.model.DownloadState
import com.itantra.domain.model.ModelPack
import com.itantra.domain.model.PeerDevice
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
    private val llmModule = LlmModule(application)
    private val audioPlayback = AudioPlaybackManager(application, audioCallbacks)
    private val audioCapture = AudioCaptureModule(
        vadModule = vadModule,
        sttModule = sttModule,
        callbacks = audioCallbacks,
        onSpeechReady = { pcm, lang ->
            sttModule.ensureLoaded(lang)
            val result = sttModule.transcribe(pcm, lang)
            if (result is AppResult.Success && result.data.isNotBlank()) {
                viewModelScope.launch(Dispatchers.Main) {
                    sendAiMessage(result.data)
                }
            }
        }
    )

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    /**
     * Set when [speakAiResponse] can't produce audio (language has no voice model, or the pack
     * isn't downloaded) — previously this failed silently (logged only), which read as "TTS is
     * broken" rather than "this language has no offline voice yet". Cleared on the next
     * successful synthesis attempt.
     */
    private val _voiceUnavailableNotice = MutableStateFlow<String?>(null)
    val voiceUnavailableNotice: StateFlow<String?> = _voiceUnavailableNotice.asStateFlow()

    private val _isVoiceMuted = MutableStateFlow(false)
    val isVoiceMuted: StateFlow<Boolean> = _isVoiceMuted.asStateFlow()

    private val _isRecordingVoice = MutableStateFlow(false)
    val isRecordingVoice: StateFlow<Boolean> = _isRecordingVoice.asStateFlow()

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
                _voiceUnavailableNotice.value = null
                audioPlayback.play(waveform)
            } else {
                _isSpeaking.value = false
                _voiceUnavailableNotice.value = "Voice not available offline for '$lang' — showing text only"
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
                    sttModule.ensureLoaded(_selectedLanguage.value)
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

    /**
     * True when the most recent AI reply came from real Phi-3 inference ([LlmModule]); false
     * when it came from [TacticalAiEngine]'s hardcoded (but still real, correct) safety
     * responses — e.g. because the model isn't downloaded, the device's ABI isn't supported,
     * or generation failed. The UI should show which one actually answered rather than silently
     * implying generated text when it's a lookup table.
     */
    private val _isUsingRealLlm = MutableStateFlow(false)
    val isUsingRealLlm: StateFlow<Boolean> = _isUsingRealLlm.asStateFlow()

    /** Phi-3-mini-4k-instruct's documented chat template — improves generation quality over a plain prefix. */
    private fun buildLlmPrompt(userText: String): String =
        "<|user|>\nYou are iTantra, an offline disaster-response and mesh-radio assistant. Answer briefly and practically.\n$userText<|end|>\n<|assistant|>\n"

    private suspend fun generateRealLlmReply(prompt: String): String? {
        val modelPath = downloadManager.modelPath(ModelPack.AI_ASSISTANT) ?: return null
        if (!llmModule.isDeviceSupported()) return null
        if (!llmModule.ensureLoaded(modelPath)) return null
        val reply = llmModule.generate(buildLlmPrompt(prompt))
        return reply?.trim()?.takeIf { it.isNotBlank() }
    }

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
            val prompt = text.trim()
            val realReply = generateRealLlmReply(prompt)
            val fullReply: String
            if (realReply != null) {
                fullReply = realReply
                _isUsingRealLlm.value = true
            } else {
                kotlinx.coroutines.delay(180) // mimic thinking latency for the instant lookup-table fallback
                fullReply = TacticalAiEngine.generateResponse(prompt)
                _isUsingRealLlm.value = false
            }
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

            // Immediately pipe generation output into local ONNX TTS engine
            speakAiResponse(fullReply, detection.languageCode)
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
        meshHardwareManager.release()
        ttsModule.release()
        llmModule.release()
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
