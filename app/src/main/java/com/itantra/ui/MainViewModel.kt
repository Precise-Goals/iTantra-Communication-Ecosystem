package com.itantra.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.itantra.core.service.ITantraForegroundService
import com.itantra.domain.model.AlertEvent
import com.itantra.domain.model.AppResult
import com.itantra.domain.model.ConnectionMode
import com.itantra.domain.model.IndicLanguage
import com.itantra.domain.model.PeerDevice
import com.itantra.domain.model.TransceiverMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Central ViewModel connecting Domain B (Shell) to Domain A (Engine).
 *
 * RULE: This ViewModel ONLY reads from ITantraForegroundService flows.
 * It NEVER instantiates OrtEnvironment, AudioRecord, or sockets directly.
 *
 * All UI state is derived from the Binder-exposed StateFlows of the service.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    // ==================== SERVICE BINDING ====================
    private var service: ITantraForegroundService? = null
    private var isBound = false

    private val _isServiceBound = MutableStateFlow(false)
    val isServiceBound: StateFlow<Boolean> = _isServiceBound.asStateFlow()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as ITantraForegroundService.ITantraBinder).getService()
            isBound = true
            _isServiceBound.value = true
            observeServiceFlows()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            _isServiceBound.value = false
            service = null
        }
    }

    // ==================== UI STATE FLOWS ====================

    private val _peersFlow = MutableStateFlow<List<PeerDevice>>(emptyList())
    val peersFlow: StateFlow<List<PeerDevice>> = _peersFlow.asStateFlow()

    private val _messageLogFlow = MutableStateFlow<List<TransceiverMessage>>(emptyList())
    val messageLogFlow: StateFlow<List<TransceiverMessage>> = _messageLogFlow.asStateFlow()

    private val _networkStateFlow = MutableStateFlow("DISCONNECTED")
    val networkStateFlow: StateFlow<String> = _networkStateFlow.asStateFlow()

    private val _errorFlow = MutableSharedFlow<AppResult.Error>(extraBufferCapacity = 10)
    val errorFlow: SharedFlow<AppResult.Error> = _errorFlow.asSharedFlow()

    private val _alertFlow = MutableSharedFlow<AlertEvent>(extraBufferCapacity = 5)
    val alertFlow: SharedFlow<AlertEvent> = _alertFlow.asSharedFlow()

    private val _vadProbabilityFlow = MutableStateFlow(0f)
    val vadProbabilityFlow: StateFlow<Float> = _vadProbabilityFlow.asStateFlow()

    private val _ramUsageMbFlow = MutableStateFlow(0f)
    val ramUsageMbFlow: StateFlow<Float> = _ramUsageMbFlow.asStateFlow()

    // ==================== CONFIGURATION STATE ====================

    private val _connectionMode = MutableStateFlow(ConnectionMode.PUSH_TO_TALK)
    val connectionMode: StateFlow<ConnectionMode> = _connectionMode.asStateFlow()

    private val _sttLanguage = MutableStateFlow(IndicLanguage.HINDI)
    val sttLanguage: StateFlow<IndicLanguage> = _sttLanguage.asStateFlow()

    private val _ttsLanguage = MutableStateFlow(IndicLanguage.HINDI)
    val ttsLanguage: StateFlow<IndicLanguage> = _ttsLanguage.asStateFlow()

    private val _isPTTActive = MutableStateFlow(false)
    val isPTTActive: StateFlow<Boolean> = _isPTTActive.asStateFlow()

    // ==================== SERVICE BINDING ====================

    fun bindService() {
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, ITantraForegroundService::class.java)
        ctx.startForegroundService(intent)
        ctx.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun unbindService() {
        if (isBound) {
            getApplication<Application>().unbindService(serviceConnection)
            isBound = false
        }
    }

    private fun observeServiceFlows() {
        val svc = service ?: return
        viewModelScope.launch {
            launch { svc.peersFlow.collect { _peersFlow.value = it } }
            launch { svc.messageLogFlow.collect { _messageLogFlow.value = it } }
            launch { svc.networkStateFlow.collect { _networkStateFlow.value = it } }
            launch { svc.errorFlow.collect { _errorFlow.emit(it) } }
            launch { svc.alertFlow.collect { _alertFlow.emit(it) } }
            launch { svc.vadProbabilityFlow.collect { _vadProbabilityFlow.value = it } }
            launch { svc.ramUsageMbFlow.collect { _ramUsageMbFlow.value = it } }
        }
    }

    // ==================== ACTIONS ====================

    fun startPTT() {
        _isPTTActive.value = true
        service?.startPTT()
    }

    fun stopPTT() {
        _isPTTActive.value = false
        service?.stopPTT()
    }

    fun setConnectionMode(mode: ConnectionMode) {
        _connectionMode.value = mode
        service?.setConnectionMode(mode)
    }

    fun setSTTLanguage(lang: IndicLanguage) {
        _sttLanguage.value = lang
        service?.setSTTLanguage(lang.code)
    }

    fun setTTSLanguage(lang: IndicLanguage) {
        _ttsLanguage.value = lang
        service?.setTTSLanguage(lang.code)
    }

    fun broadcastAlert(text: String) {
        service?.broadcastAlert(text)
    }

    fun connectToPeer(deviceId: String) {
        service?.connectToPeer(deviceId)
    }

    override fun onCleared() {
        super.onCleared()
        unbindService()
    }
}
