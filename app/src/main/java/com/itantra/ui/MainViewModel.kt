package com.itantra.ui

import android.app.Application
import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.itantra.core.ai.LanguageDetector
import com.itantra.core.ai.TacticalAiEngine
import com.itantra.core.download.ModelDownloadManager
import com.itantra.data.DeviceProfileRepository
import com.itantra.data.PeerRegistryRepository
import com.itantra.domain.model.DeviceProfile
import com.itantra.domain.model.DownloadState
import com.itantra.domain.model.ModelPack
import com.itantra.domain.model.PeerDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val profileRepo = DeviceProfileRepository(application)
    private val peerRegistry = PeerRegistryRepository(application)
    val downloadManager = ModelDownloadManager(application)

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

    fun onLanguageDetected(bcp47Code: String, confidence: Float) {
        if (_isAutoDetectEnabled.value && confidence >= 0.6f) {
            _detectedLanguage.value = bcp47Code
            _selectedLanguage.value = bcp47Code
        }
    }

    // ── Transceiver / P2P State ───────────────────────────────────────
    private val _isHosting = MutableStateFlow(false)
    val isHosting: StateFlow<Boolean> = _isHosting.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    val knownPeers: StateFlow<List<PeerDevice>> = peerRegistry.peers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setHosting(enabled: Boolean) { _isHosting.value = enabled }
    fun setDiscovering(enabled: Boolean) { _isDiscovering.value = enabled }

    fun authorizePeer(deviceId: String) {
        viewModelScope.launch { peerRegistry.authorizePeer(deviceId) }
    }

    fun revokePeer(deviceId: String) {
        viewModelScope.launch { peerRegistry.revokePeer(deviceId) }
    }

    // ── Native Voice Talking (TTS) Pipeline ───────────────────────────
    private var textToSpeech: TextToSpeech? = null
    private val _isTtsReady = MutableStateFlow(false)
    val isTtsReady: StateFlow<Boolean> = _isTtsReady.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _isVoiceMuted = MutableStateFlow(false)
    val isVoiceMuted: StateFlow<Boolean> = _isVoiceMuted.asStateFlow()

    init {
        initTts(application)
    }

    private fun initTts(context: Context) {
        try {
            textToSpeech = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    textToSpeech?.language = Locale.ENGLISH
                    _isTtsReady.value = true
                    textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) { _isSpeaking.value = true }
                        override fun onDone(utteranceId: String?) { _isSpeaking.value = false }
                        override fun onError(utteranceId: String?) { _isSpeaking.value = false }
                    })
                    Log.i("MainViewModel", "TextToSpeech speech synthesis pipeline initialized successfully")
                }
            }
        } catch (e: Exception) {
            Log.w("MainViewModel", "TTS init warning: ${e.message}")
        }
    }

    fun toggleVoiceMute() {
        _isVoiceMuted.value = !_isVoiceMuted.value
        if (_isVoiceMuted.value) {
            stopSpeaking()
        }
    }

    fun stopSpeaking() {
        textToSpeech?.stop()
        _isSpeaking.value = false
    }

    fun speakAiResponse(text: String, preferredLang: String? = null) {
        if (_isVoiceMuted.value) return
        val tts = textToSpeech ?: return

        try {
            // Clean markdown syntax for natural voice pronunciation
            val clean = text
                .replace(Regex("""[*#_`~>•]"""), " ")
                .replace(Regex("""https?://\S+"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim()

            val detected = LanguageDetector.detect(clean)
            val langCode = preferredLang ?: detected.languageCode

            val locale = when (langCode) {
                "hi" -> Locale("hi", "IN")
                "mr" -> Locale("mr", "IN")
                "bn" -> Locale("bn", "IN")
                "ta" -> Locale("ta", "IN")
                "te" -> Locale("te", "IN")
                "kn" -> Locale("kn", "IN")
                "gu" -> Locale("gu", "IN")
                "ml" -> Locale("ml", "IN")
                else -> Locale.ENGLISH
            }
            tts.language = locale
            tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "ai_resp_${System.currentTimeMillis()}")
            _isSpeaking.value = true
        } catch (e: Exception) {
            Log.e("MainViewModel", "speakAiResponse error: ${e.message}")
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

        // Auto-detect language of user's query
        val detection = LanguageDetector.detect(text)
        if (_isAutoDetectEnabled.value) {
            _detectedLanguage.value = detection.languageCode
        }

        _isAiThinking.value = true
        viewModelScope.launch {
            kotlinx.coroutines.delay(200) // Initial neural latency
            val fullReply = generateAiResponse(text.trim())
            _isAiThinking.value = false

            val assistantMsgId = UUID.randomUUID().toString()
            val initialAssistantMsg = AiMessage(id = assistantMsgId, text = "", isUser = false)
            _aiMessages.value = _aiMessages.value + initialAssistantMsg

            // Word-by-word streaming generation
            val words = fullReply.split(" ")
            val accumulated = StringBuilder()

            for (i in words.indices) {
                accumulated.append(words[i])
                if (i < words.size - 1) accumulated.append(" ")
                val currentChunk = accumulated.toString()

                _aiMessages.value = _aiMessages.value.map { msg ->
                    if (msg.id == assistantMsgId) msg.copy(text = currentChunk) else msg
                }
                kotlinx.coroutines.delay(20) // 20ms per word
            }

            // Audibly speak the AI answer through device speaker (actual talking model pipeline!)
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

    private fun generateAiResponse(query: String): String {
        return TacticalAiEngine.generateResponse(query)
    }

    override fun onCleared() {
        super.onCleared()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        downloadManager.refreshStates()
    }
}

data class AiMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
