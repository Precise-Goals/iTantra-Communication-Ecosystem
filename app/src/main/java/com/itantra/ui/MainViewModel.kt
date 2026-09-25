package com.itantra.ui

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.itantra.core.download.ModelDownloadManager
import com.itantra.core.network.MeshHardwareManager
import com.itantra.core.service.ITantraForegroundService
import com.itantra.data.DeviceProfileRepository
import com.itantra.data.PeerRegistryRepository
import com.itantra.domain.model.ConnectionMode
import com.itantra.domain.model.DeviceProfile
import com.itantra.domain.model.DownloadState
import com.itantra.domain.model.ModelPack
import com.itantra.domain.model.PeerDevice
import com.itantra.domain.model.TransceiverMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val profileRepo = DeviceProfileRepository(application)
    private val peerRegistry = PeerRegistryRepository(application)
    val downloadManager = ModelDownloadManager(application)
    private val meshHardwareManager = MeshHardwareManager(application)

    // ── Real PTT transport (ITantraForegroundService) ───────────────────
    // The service holds the real audio-capture→STT→transmit pipeline (WifiDirectManager,
    // SocketTransport, BluetoothRFCOMMManager) — previously declared in the manifest but never
    // started or bound anywhere, so PTT had nothing to call. Bound here since AndroidViewModel
    // already has an Application context; peer discovery/hosting stays on meshHardwareManager
    // above (already real and working) — this only wires up the transmit half.
    private var foregroundService: ITantraForegroundService? = null

    // Real service state, previously tracked internally by ITantraForegroundService but never
    // bridged to the UI at all (confirmed: zero references to networkStateFlow anywhere in this
    // file before this) — bridged here from the service's own flows.
    private val _networkState = MutableStateFlow("DISCONNECTED")
    val networkState: StateFlow<String> = _networkState.asStateFlow()

    private val _pipelineStage = MutableStateFlow(ITantraForegroundService.PipelineStage.IDLE)
    val pipelineStage: StateFlow<ITantraForegroundService.PipelineStage> = _pipelineStage.asStateFlow()

    // ── SOS / alerts (T66) ──
    private val _alertArmed = MutableStateFlow(false)
    /** True while the next spoken PTT message will be sent as an ALERT. */
    val alertArmed: StateFlow<Boolean> = _alertArmed.asStateFlow()

    /** Arm or disarm "send my next spoken message as an ALERT". */
    fun setAlertArmed(armed: Boolean) {
        _alertArmed.value = armed
        foregroundService?.sendNextAsAlert = armed
    }

    private val _messageLog = MutableStateFlow<List<TransceiverMessage>>(emptyList())
    val messageLog: StateFlow<List<TransceiverMessage>> = _messageLog.asStateFlow()

    private val _isBluetoothListening = MutableStateFlow(false)

    /** What "Host Beacon" should actually reflect — true if listenable over Wi-Fi Direct OR
     * Bluetooth, not just Wi-Fi Direct's own createGroup() success (confirmed on-device: the
     * toggle could show off while the real Bluetooth server was genuinely listening). */
    val isHostingEffective: StateFlow<Boolean> = combine(meshHardwareManager.isHosting, _isBluetoothListening) { wifi, bt -> wifi || bt }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = (binder as? ITantraForegroundService.ITantraBinder)?.getService()
            foregroundService = service
            service?.let {
                viewModelScope.launch { it.networkStateFlow.collect { s -> _networkState.value = s } }
                viewModelScope.launch { it.pipelineStage.collect { s -> _pipelineStage.value = s } }
                viewModelScope.launch { it.isBluetoothListening.collect { b -> _isBluetoothListening.value = b } }
                viewModelScope.launch { it.messageLogFlow.collect { list -> _messageLog.value = list } }
                // Push the current selection before warming, so the right model is loaded (T72).
                it.setSTTLanguage(_selectedLanguage.value)
                it.setTTSLanguage(_selectedLanguage.value)
                it.warmUp()
                // Re-initialise the VAD when its model finishes downloading (T73). The first
                // emission may already be Downloaded; reinitVadIfNeeded() is then a no-op.
                val svc = it
                viewModelScope.launch {
                    var vadReady = false
                    downloadStates.collect { states ->
                        val nowReady = states[ModelPack.VAD_MODEL] is DownloadState.Downloaded
                        if (nowReady && !vadReady) svc.reinitVadIfNeeded()
                        vadReady = nowReady
                    }
                }
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            foregroundService = null
        }
    }

    init {
        val intent = Intent(application, ITantraForegroundService::class.java)
            .setAction(ITantraForegroundService.ACTION_START)
        ContextCompat.startForegroundService(application, intent)
        application.bindService(intent, serviceConnection, 0)
    }

    /** Start real PTT capture (hold) — no-op if the service hasn't finished binding yet. */
    fun startTransceiverPtt() {
        foregroundService?.startPTT()
    }

    private val _isPhoneMode = MutableStateFlow(false)
    val isPhoneMode: StateFlow<Boolean> = _isPhoneMode.asStateFlow()

    /**
     * PTT off = phone mode: continuous VAD-gated capture instead of hold-to-talk.
     * Required by the problem statement ("if turned off it should work like a phone").
     */
    fun setPhoneMode(enabled: Boolean) {
        _isPhoneMode.value = enabled
        foregroundService?.setConnectionMode(
            if (enabled) ConnectionMode.PHONE_MODE else ConnectionMode.PUSH_TO_TALK
        )
    }

    /** Stop PTT capture (release) — flushes to STT and attempts to transmit. */
    fun stopTransceiverPtt() {
        foregroundService?.stopPTT()
        _alertArmed.value = false
    }

    /** Replay a received message's voice note (T67). Returns false if it is not stored yet. */
    fun replayVoiceNote(message: TransceiverMessage): Boolean =
        foregroundService?.replayVoiceNote(message.senderId, message.sequence) ?: false

    /** Initiate a real Wi-Fi Direct connection to a peer discovered via [meshHardwareManager]. */
    fun connectToPeer(deviceAddress: String) {
        foregroundService?.connectToPeer(deviceAddress)
    }

    /** Already-paired Bluetooth devices — a plain on-demand read (bonded devices rarely change
     * mid-session), not a StateFlow. */
    fun bondedBluetoothDevices(): List<PeerDevice> = foregroundService?.getBondedBluetoothDevices() ?: emptyList()

    /** Connect to an already-paired Bluetooth device by MAC address. */
    fun connectToBluetoothPeer(deviceAddress: String) {
        foregroundService?.connectToBluetoothPeer(deviceAddress)
    }

    /** Pair with (if needed) and connect to a Bluetooth peer discovered via [meshHardwareManager]'s
     * scan, which may not be bonded yet. */
    fun pairAndConnectBluetoothPeer(deviceAddress: String) {
        foregroundService?.pairAndConnectBluetoothPeer(deviceAddress)
    }

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

    // ── Walkie-talkie language ────────────────────────────────────────
    // Text-based language auto-detection was only used by the removed AI Assistant; the
    // transceiver must know the language before STT runs, so it uses this explicit selection.
    private val _selectedLanguage = MutableStateFlow("hi") // BCP-47 code
    val selectedLanguage: StateFlow<String> = _selectedLanguage.asStateFlow()

    fun setManualLanguage(bcp47Code: String) {
        _selectedLanguage.value = bcp47Code
        // The walkie-talkie previously ignored this and always used Hindi (T72).
        foregroundService?.let {
            it.setSTTLanguage(bcp47Code)
            it.setTTSLanguage(bcp47Code)
            it.warmUp(bcp47Code, bcp47Code)
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
        if (enabled) {
            meshHardwareManager.startHostBeacon()
            // "Host Beacon" now also means "listenable over Bluetooth" — previously the RFCOMM
            // server only started automatically after repeated Wi-Fi Direct failures, so two
            // devices that both only ever dial out could never actually connect to each other.
            foregroundService?.startBluetoothServer()
        } else {
            meshHardwareManager.stopHostBeacon()
            foregroundService?.stopBluetoothServer()
        }
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

    override fun onCleared() {
        super.onCleared()
        runCatching { getApplication<Application>().unbindService(serviceConnection) }
        meshHardwareManager.release()
        downloadManager.refreshStates()
    }
}
