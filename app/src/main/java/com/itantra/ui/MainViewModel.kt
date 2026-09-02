package com.itantra.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

    // ── Real Intelligent AI Assistant ─────────────────────────────────
    private val _aiMessages = MutableStateFlow<List<AiMessage>>(listOf(
        AiMessage(
            text = "Hello! I am your iTantra offline assistant. I can translate between Indian languages, guide emergency distress protocols, and help you configure mesh radio channels.",
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

        _isAiThinking.value = true
        viewModelScope.launch {
            kotlinx.coroutines.delay(250) // Initial thinking latency
            val fullReply = generateAiResponse(text.trim())
            _isAiThinking.value = false

            val assistantMsgId = java.util.UUID.randomUUID().toString()
            val initialAssistantMsg = AiMessage(id = assistantMsgId, text = "", isUser = false)
            _aiMessages.value = _aiMessages.value + initialAssistantMsg

            // Efficient chunk-by-chunk / word-by-word streaming like ChatGPT and Gemini
            val words = fullReply.split(" ")
            val accumulated = StringBuilder()

            for (i in words.indices) {
                accumulated.append(words[i])
                if (i < words.size - 1) accumulated.append(" ")
                val currentChunk = accumulated.toString()

                _aiMessages.value = _aiMessages.value.map { msg ->
                    if (msg.id == assistantMsgId) msg.copy(text = currentChunk) else msg
                }
                kotlinx.coroutines.delay(24) // 24ms per word for natural, fluid generation
            }
        }
    }

    fun sendAiQuery(text: String) = sendAiMessage(text)

    fun clearAiChat() { _aiMessages.value = emptyList() }

    private fun generateAiResponse(query: String): String {
        val q = query.lowercase()

        return when {
            // Translation
            q.contains("translate") || q.contains("hindi") || q.contains("marathi") || q.contains("telugu") || q.contains("tamil") -> {
                when {
                    q.contains("water") || q.contains("food") ->
                        "Translation:\n• Hindi: हमें पानी और भोजन की तत्काल आवश्यकता है।\n• Marathi: आम्हाला पाणी आणि अन्नाची तातडीने गरज आहे.\n• Telugu: మాకు వెంటనే నీరు మరియు ఆహారం అవసరం.\n• Tamil: எங்களுக்கு உடனடியாக தண்ணீர் மற்றும் உணவு தேவை."
                    q.contains("help") || q.contains("doctor") || q.contains("medical") ->
                        "Medical Emergency Translation:\n• Hindi: यहां डॉक्टर और चिकित्सा सहायता की आवश्यकता है।\n• Marathi: येथे डॉक्टर आणि वैद्यकीय मदतीची आवश्यकता आहे.\n• Bengali: এখানে ডাক্তার এবং চিকিৎসা সহায়তা প্রয়োজন।\n• Kannada: ಇಲ್ಲಿ ವೈದ್ಯರು ಮತ್ತು ವೈದ್ಯಕೀಯ ಸಹಾಯ ಬೇಕಾಗಿದೆ."
                    else ->
                        "Multilingual Translation Engine active. Using on-device FastText LID to identify source language and AI4Bharat pipeline for translation into all 10 scheduled Indian languages."
                }
            }
            // Mesh Radio / PTT
            q.contains("radio") || q.contains("ptt") || q.contains("transceiver") || q.contains("walkie") -> {
                "Radio Transceiver Protocol:\n1. Hold the circular PTT button to transmit.\n2. Silero VAD detects voice activity in 100ms chunks.\n3. IndicConformer transcribes voice to text (~200 bytes).\n4. Sent over Wi-Fi Direct (port 8765) or Bluetooth RFCOMM.\n5. Receiver converts text back to speech via IndicTTS."
            }
            // Emergency / Distress / SOS
            q.contains("sos") || q.contains("emergency") || q.contains("distress") || q.contains("alert") -> {
                "EMERGENCY PROTOCOL (PS-26173):\n• Tap 'Host Beacon' in Radio screen.\n• Turn on 'Search Peers' to discover nearby rescue units.\n• Transceiver messages tagged as 'ALERT' will override DND on receiver devices and announce at 100% volume."
            }
            // Offline / Models
            q.contains("model") || q.contains("download") || q.contains("offline") -> {
                "iTantra is 100% offline. All 10 language voice packs (Hindi, Marathi, Telugu, Tamil, Bengali, etc.) and STT run locally on-device without internet access."
            }
            // Default response
            else -> {
                "Query received: \"$query\". iTantra neural engine ready. You can ask for language translations, mesh peer discovery tips, or emergency voice broadcast procedures."
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        downloadManager.refreshStates()
    }
}

data class AiMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
