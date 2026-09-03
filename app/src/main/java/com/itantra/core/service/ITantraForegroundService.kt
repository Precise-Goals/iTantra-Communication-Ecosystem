package com.itantra.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.itantra.R
import com.itantra.core.audio.AudioCaptureManager
import com.itantra.core.audio.AudioPlaybackManager
import com.itantra.core.audio.STTModule
import com.itantra.core.audio.TTSModule
import com.itantra.core.audio.VADModule
import com.itantra.core.network.BluetoothRFCOMMManager
import com.itantra.core.network.NetworkTransceiver
import com.itantra.core.network.SocketTransport
import com.itantra.core.network.WifiDirectManager
import com.itantra.core.proto.ProtobufSerializer
import com.itantra.domain.contracts.AudioCallbacks
import com.itantra.domain.contracts.NetworkCallbacks
import com.itantra.domain.model.AlertEvent
import com.itantra.domain.model.AppResult
import com.itantra.domain.model.ConnectionMode
import com.itantra.domain.model.ConnectionType
import com.itantra.domain.model.Direction
import com.itantra.domain.model.ErrorCode
import com.itantra.domain.model.MessageType
import com.itantra.domain.model.PeerDevice
import com.itantra.domain.model.TransceiverMessage
import com.itantra.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * iTantra Foreground Service — the persistent background engine (Domain A).
 *
 * This service is the central hub for ALL background operations:
 * - Audio capture and VAD
 * - STT inference (IndicConformer)
 * - TTS synthesis (IndicTTS VITS)
 * - Network management (Wi-Fi Direct + BT RFCOMM fallback)
 * - Protobuf message encode/decode
 *
 * All state is exposed via StateFlow/SharedFlow to the UI layer (Domain B) through Binder.
 * CRITICAL: This service NEVER references UI elements or posts to the main thread directly.
 *
 * Started with startForeground() using FOREGROUND_SERVICE_TYPE_MICROPHONE.
 * Requests battery optimization whitelist to prevent OS termination.
 */
class ITantraForegroundService : Service() {

    companion object {
        private const val TAG = "iTantraService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "itantra_engine"
        private const val CHANNEL_NAME = "iTantra Communication Engine"
        const val ACTION_START = "com.itantra.START"
        const val ACTION_STOP = "com.itantra.STOP"
    }

    // Service binder for Activity binding
    inner class ITantraBinder : Binder() {
        fun getService(): ITantraForegroundService = this@ITantraForegroundService
    }

    private val binder = ITantraBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val deviceId = UUID.randomUUID().toString()

    // ==================== STATE FLOWS (Domain A → Domain B) ====================
    private val _peersFlow = MutableStateFlow<List<PeerDevice>>(emptyList())
    val peersFlow: StateFlow<List<PeerDevice>> = _peersFlow.asStateFlow()

    private val _messageLogFlow = MutableStateFlow<List<TransceiverMessage>>(emptyList())
    val messageLogFlow: StateFlow<List<TransceiverMessage>> = _messageLogFlow.asStateFlow()

    private val _errorFlow = MutableSharedFlow<AppResult.Error>(replay = 0, extraBufferCapacity = 10)
    val errorFlow: SharedFlow<AppResult.Error> = _errorFlow.asSharedFlow()

    private val _alertFlow = MutableSharedFlow<AlertEvent>(replay = 0, extraBufferCapacity = 5)
    val alertFlow: SharedFlow<AlertEvent> = _alertFlow.asSharedFlow()

    private val _vadProbabilityFlow = MutableStateFlow(0f)
    val vadProbabilityFlow: StateFlow<Float> = _vadProbabilityFlow.asStateFlow()

    private val _networkStateFlow = MutableStateFlow<String>("DISCONNECTED")
    val networkStateFlow: StateFlow<String> = _networkStateFlow.asStateFlow()

    private val _ramUsageMbFlow = MutableStateFlow(0f)
    val ramUsageMbFlow: StateFlow<Float> = _ramUsageMbFlow.asStateFlow()

    // ==================== CONFIGURATION STATE ====================
    var connectionMode: ConnectionMode = ConnectionMode.PUSH_TO_TALK
        private set
    var sttLanguage: String = "hi"
    var ttsLanguage: String = "hi"
    private var sequenceCounter = 0

    // ==================== MODULES ====================
    private lateinit var vadModule: VADModule
    private lateinit var sttModule: STTModule
    private lateinit var ttsModule: TTSModule
    private lateinit var audioPlayback: AudioPlaybackManager
    private lateinit var audioCaptureManager: AudioCaptureManager
    private lateinit var socketTransport: SocketTransport
    private lateinit var networkTransceiver: NetworkTransceiver
    private lateinit var wifiDirectManager: WifiDirectManager
    private lateinit var bluetoothManager: BluetoothRFCOMMManager
    private var wakeLock: PowerManager.WakeLock? = null
    private var isBluetoothFallbackActive = false

    // ==================== CALLBACKS ====================

    private val networkCallbacks = object : NetworkCallbacks {
        override fun onNodeDiscovered(device: PeerDevice) {
            val current = _peersFlow.value.toMutableList()
            if (current.none { it.deviceId == device.deviceId }) {
                current.add(device)
                _peersFlow.value = current
            }
            Log.d(TAG, "Node discovered: ${device.deviceName}")
        }

        override fun onNodeConnected(device: PeerDevice) {
            val current = _peersFlow.value.toMutableList()
            val idx = current.indexOfFirst { it.deviceId == device.deviceId }
            if (idx >= 0) current[idx] = device.copy(isConnected = true)
            else current.add(device.copy(isConnected = true))
            _peersFlow.value = current
            _networkStateFlow.value = if (device.connectionType == ConnectionType.WIFI_DIRECT)
                "CONNECTED_WIFI" else "CONNECTED_BLUETOOTH"
            updateNotification("Connected to ${device.deviceName}")
            Log.d(TAG, "Node connected: ${device.deviceName}")
        }

        override fun onNodeDisconnected(deviceId: String) {
            val current = _peersFlow.value.toMutableList()
            val idx = current.indexOfFirst { it.deviceId == deviceId }
            if (idx >= 0) current[idx] = current[idx].copy(isConnected = false)
            _peersFlow.value = current
            _networkStateFlow.value = "DISCONNECTED"
            updateNotification("Searching for peers...")
        }

        override fun onTextReceived(message: TransceiverMessage) {
            appendMessage(message.copy(direction = Direction.RECEIVED))
            // Handle ALERT messages specially
            if (message.type == MessageType.ALERT) {
                serviceScope.launch {
                    _alertFlow.emit(AlertEvent(message))
                }
            }
            // Synthesize and play incoming text via AudioPlaybackManager:
            // Strictly trusts the sender's stamped language ID, normalizes numbers/currency,
            // and overrides volume with AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE if ALERT
            audioPlayback.playIncomingMessage(message, ttsModule)
        }

        override fun onNetworkError(error: AppResult.Error) {
            serviceScope.launch { _errorFlow.emit(error) }
            // If Wi-Fi Direct repeatedly fails, activate BT fallback
            if (error.code == ErrorCode.WIFI_DIRECT_UNAVAILABLE && !isBluetoothFallbackActive) {
                Log.w(TAG, "Activating Bluetooth RFCOMM fallback")
                isBluetoothFallbackActive = true
                bluetoothManager.startServer()
                _networkStateFlow.value = "DISCOVERING"
            }
        }

        override fun onLatencyMeasured(deviceId: String, latencyMs: Long) {
            val current = _peersFlow.value.toMutableList()
            val idx = current.indexOfFirst { it.deviceId == deviceId }
            if (idx >= 0) current[idx] = current[idx].copy(latencyMs = latencyMs)
            _peersFlow.value = current
        }
    }

    private val audioCallbacks = object : AudioCallbacks {
        override fun onVADTriggered(isSpeech: Boolean, probability: Float) {
            _vadProbabilityFlow.value = probability
        }

        override fun onSTTResult(result: AppResult<String>, confidence: Float, inferenceMs: Long) {
            if (result is AppResult.Success) {
                val message = TransceiverMessage(
                    type = MessageType.SPEECH,
                    text = result.data,
                    srcLang = sttLanguage,
                    dstLang = ttsLanguage,
                    senderId = deviceId,
                    timestamp = System.currentTimeMillis(),
                    confidence = confidence,
                    sequence = ++sequenceCounter,
                    direction = Direction.SENT
                )
                appendMessage(message)
                // Transmit over network
                if (isBluetoothFallbackActive) {
                    bluetoothManager.send(message)
                } else {
                    socketTransport.broadcast(message)
                }
            }
        }

        override fun onTTSSynthesisComplete(durationMs: Long) {
            Log.d(TAG, "TTS synthesis complete: ${durationMs}ms audio")
        }

        override fun onAudioError(error: AppResult.Error) {
            serviceScope.launch { _errorFlow.emit(error) }
        }

        override fun onAudioFocusChanged(gained: Boolean) {
            Log.d(TAG, "Audio focus: ${if (gained) "gained" else "lost"}")
        }
    }

    // ==================== SERVICE LIFECYCLE ====================

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Starting iTantra engine..."))
        initializeModules()
        acquireWakeLock()
        startRamMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        wifiDirectManager.register()
        _networkStateFlow.value = "DISCOVERING"
        updateNotification("Searching for peers...")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        audioCaptureManager.stopCapture()
        vadModule.release()
        sttModule.release()
        ttsModule.release()
        wifiDirectManager.unregister()
        socketTransport.stop()
        networkTransceiver.stop()
        bluetoothManager.stop()
        wakeLock?.release()
        Log.d(TAG, "Service destroyed")
    }

    // ==================== INITIALIZATION ====================

    private fun initializeModules() {
        vadModule = VADModule(this, audioCallbacks)
        sttModule = STTModule(this, audioCallbacks)
        ttsModule = TTSModule(this, audioCallbacks)
        audioPlayback = AudioPlaybackManager(this, audioCallbacks)
        socketTransport = SocketTransport(networkCallbacks, deviceId)
        networkTransceiver = NetworkTransceiver(networkCallbacks, deviceId)
        wifiDirectManager = WifiDirectManager(this, networkCallbacks, socketTransport, deviceId)
        bluetoothManager = BluetoothRFCOMMManager(this, networkCallbacks, deviceId)

        audioCaptureManager = AudioCaptureManager(
            vadModule = vadModule,
            sttModule = sttModule,
            callbacks = audioCallbacks,
            onSpeechReady = { audioBuffer, lang ->
                sttModule.ensureLoaded()
                sttModule.transcribe(audioBuffer, lang)
            }
        ).apply {
            currentMode = if (connectionMode == ConnectionMode.PHONE_MODE) {
                AudioCaptureManager.Mode.PHONE_MODE
            } else {
                AudioCaptureManager.Mode.PUSH_TO_TALK
            }
            currentLanguage = sttLanguage
        }

        // Initialize VAD on startup (always resident)
        serviceScope.launch {
            val vadOk = vadModule.initialize()
            if (vadOk && connectionMode == ConnectionMode.PHONE_MODE) {
                audioCaptureManager.startCapture()
            }
        }
    }

    // ==================== PUBLIC API (called from Activity via Binder) ====================

    /** Start PTT capture (hold) */
    fun startPTT() {
        audioCaptureManager.currentMode = AudioCaptureManager.Mode.PUSH_TO_TALK
        audioCaptureManager.currentLanguage = sttLanguage
        if (!audioCaptureManager.isRunning) {
            audioCaptureManager.startCapture()
        }
    }

    /** Stop PTT capture (release) — flushes buffer to STT */
    fun stopPTT() {
        serviceScope.launch {
            val buffer = audioCaptureManager.flushAndTranscribe()
            if (buffer != null && buffer.isNotEmpty()) {
                sttModule.ensureLoaded()
                sttModule.transcribe(buffer, sttLanguage)
            }
            if (connectionMode != ConnectionMode.PHONE_MODE) {
                audioCaptureManager.stopCapture()
            }
        }
    }

    /** Switch between PTT and Phone modes */
    fun setConnectionMode(mode: ConnectionMode) {
        connectionMode = mode
        when (mode) {
            ConnectionMode.PHONE_MODE -> {
                audioCaptureManager.currentMode = AudioCaptureManager.Mode.PHONE_MODE
                audioCaptureManager.currentLanguage = sttLanguage
                if (!audioCaptureManager.isRunning) audioCaptureManager.startCapture()
            }
            ConnectionMode.PUSH_TO_TALK -> {
                audioCaptureManager.currentMode = AudioCaptureManager.Mode.PUSH_TO_TALK
                audioCaptureManager.stopCapture()
            }
        }
    }

    /** Broadcast an SOS alert to all connected peers */
    fun broadcastAlert(text: String) {
        val alert = TransceiverMessage(
            type = MessageType.ALERT,
            text = text,
            srcLang = sttLanguage,
            dstLang = ttsLanguage,
            senderId = deviceId,
            timestamp = System.currentTimeMillis(),
            sequence = ++sequenceCounter,
            direction = Direction.SENT
        )
        appendMessage(alert)
        if (isBluetoothFallbackActive) {
            bluetoothManager.send(alert)
        } else {
            socketTransport.broadcast(alert)
        }
    }

    /** Connect to a discovered Wi-Fi Direct peer */
    fun connectToPeer(deviceAddress: String) {
        // WifiDirectManager handles connection via WifiP2pDevice
        _networkStateFlow.value = "CONNECTING"
    }

    fun setSTTLanguage(lang: String) { sttLanguage = lang; audioCaptureManager.currentLanguage = lang }
    fun setTTSLanguage(lang: String) { ttsLanguage = lang }
    fun getLoadedTTSLanguages(): Set<String> = ttsModule.getLoadedLanguages()
    fun unloadTTSLanguage(lang: String) = ttsModule.unloadLanguage(lang)

    // ==================== INTERNALS ====================

    private fun appendMessage(message: TransceiverMessage) {
        val current = _messageLogFlow.value.toMutableList()
        current.add(0, message) // newest first
        if (current.size > 200) current.removeAt(current.size - 1) // cap at 200
        _messageLogFlow.value = current
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "iTantra:Engine")
        wakeLock?.acquire(10 * 60 * 1000L) // 10 min, auto-released
    }

    private fun startRamMonitoring() {
        serviceScope.launch {
            while (true) {
                kotlinx.coroutines.delay(5000)
                val runtime = Runtime.getRuntime()
                val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024f * 1024f)
                _ramUsageMbFlow.value = usedMb
            }
        }
    }

    // ==================== NOTIFICATION ====================

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "iTantra runs in the background to maintain mesh communication"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("iTantra Active")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(statusText: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(statusText))
    }
}
