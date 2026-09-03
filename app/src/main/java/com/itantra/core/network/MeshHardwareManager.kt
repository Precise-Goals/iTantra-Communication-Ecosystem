package com.itantra.core.network

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.util.Log
import com.itantra.domain.contracts.NetworkCallbacks
import com.itantra.domain.model.AppResult
import com.itantra.domain.model.ConnectionType
import com.itantra.domain.model.Direction
import com.itantra.domain.model.PeerDevice
import com.itantra.domain.model.TransceiverMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.lang.reflect.Method

/**
 * Direct Android Hardware P2P Network Manager.
 *
 * Requirements:
 * 1. Wire "Search Peers" & "Host Beacon" directly to WifiP2pManager and Bluetooth discovery.
 * 2. Live Node Plotting: UI nodes and animations trigger strictly on actual
 *    WIFI_P2P_PEERS_CHANGED_ACTION and Bluetooth ACTION_FOUND broadcast receivers.
 * 3. Device Identification: Sets and displays device names mapped to Build.ID and Build.MODEL.
 * 4. Data & Sound Mesh Transport: Automatically instantiates SocketTransport (TCP port 8765)
 *    and BluetoothRFCOMMManager (RFCOMM service) to stream PTT voice packets and alerts.
 */
class MeshHardwareManager(private val context: Context) {

    companion object {
        private const val TAG = "MeshHardware"
        val HARDWARE_DEVICE_NAME: String = "${Build.MODEL}_${Build.ID.takeLast(6)}"
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // ── Hardware System Services ──────────────────────────────────────
    private val wifiP2pManager: WifiP2pManager? by lazy {
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    }
    private var wifiP2pChannel: WifiP2pManager.Channel? = null

    private val bluetoothManager: BluetoothManager? by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        bluetoothManager?.adapter
    }

    // ── Mesh Transports ───────────────────────────────────────────────
    private var socketTransport: SocketTransport? = null
    private var bluetoothRfcommManager: BluetoothRFCOMMManager? = null

    var onMessageReceivedListener: ((TransceiverMessage) -> Unit)? = null

    private val networkCallbacks = object : NetworkCallbacks {
        override fun onNodeDiscovered(peer: PeerDevice) {
            updateDiscoveredPeer(peer)
        }

        override fun onNodeConnected(peer: PeerDevice) {
            updatePeerStatus(peer.deviceId, connected = true)
        }

        override fun onNodeDisconnected(deviceId: String) {
            updatePeerStatus(deviceId, connected = false)
        }

        override fun onTextReceived(message: TransceiverMessage) {
            Log.i(TAG, "Incoming PTT message over mesh: '${message.text.take(30)}' from ${message.senderId}")
            onMessageReceivedListener?.invoke(message)
        }

        override fun onNetworkError(error: AppResult.Error) {
            Log.w(TAG, "Mesh network notice: ${error.message}")
        }

        override fun onLatencyMeasured(deviceId: String, latencyMs: Long) {
            val current = _liveDiscoveredPeers.value.toMutableList()
            val idx = current.indexOfFirst { it.deviceId == deviceId }
            if (idx >= 0) {
                current[idx] = current[idx].copy(latencyMs = latencyMs)
                _liveDiscoveredPeers.value = current
            }
        }
    }

    private fun updateDiscoveredPeer(peer: PeerDevice) {
        val current = _liveDiscoveredPeers.value.toMutableList()
        val idx = current.indexOfFirst { it.deviceId == peer.deviceId }
        if (idx >= 0) {
            current[idx] = peer
        } else {
            current.add(peer)
        }
        _liveDiscoveredPeers.value = current
    }

    private fun updatePeerStatus(deviceId: String, connected: Boolean) {
        val current = _liveDiscoveredPeers.value.toMutableList()
        val idx = current.indexOfFirst { it.deviceId == deviceId }
        if (idx >= 0) {
            current[idx] = current[idx].copy(isConnected = connected)
            _liveDiscoveredPeers.value = current
        }
    }

    // ── State Flows ───────────────────────────────────────────────────
    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _isHosting = MutableStateFlow(false)
    val isHosting: StateFlow<Boolean> = _isHosting.asStateFlow()

    private val _liveDiscoveredPeers = MutableStateFlow<List<PeerDevice>>(emptyList())
    val liveDiscoveredPeers: StateFlow<List<PeerDevice>> = _liveDiscoveredPeers.asStateFlow()

    private val _connectedGroupInfo = MutableStateFlow<String?>("STANDALONE")
    val connectedGroupInfo: StateFlow<String?> = _connectedGroupInfo.asStateFlow()

    private var receiver: MeshBroadcastReceiver? = null

    init {
        initializeHardware()
    }

    private fun initializeHardware() {
        try {
            getOrInitChannel()
            registerReceivers()
        } catch (e: Exception) {
            Log.e(TAG, "Hardware init error: ${e.message}")
        }
    }

    private fun getOrInitChannel(): WifiP2pManager.Channel? {
        if (wifiP2pChannel == null) {
            wifiP2pManager?.let { mgr ->
                wifiP2pChannel = mgr.initialize(context, context.mainLooper) {
                    Log.w(TAG, "Wi-Fi Direct channel lost")
                    wifiP2pChannel = null
                }
                setHardwareDeviceName(HARDWARE_DEVICE_NAME)
            }
        }
        return wifiP2pChannel
    }

    /**
     * Sets the local device broadcast name via reflection on WifiP2pManager.
     */
    private fun setHardwareDeviceName(name: String) {
        val ch = getOrInitChannel() ?: return
        val mgr = wifiP2pManager ?: return
        try {
            val method: Method = mgr.javaClass.getMethod(
                "setDeviceName",
                WifiP2pManager.Channel::class.java,
                String::class.java,
                WifiP2pManager.ActionListener::class.java
            )
            method.invoke(mgr, ch, name, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.i(TAG, "Wi-Fi Direct device name successfully set to: $name")
                }
                override fun onFailure(reason: Int) {
                    Log.w(TAG, "setDeviceName failure code: $reason")
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "setDeviceName reflection fallback: ${e.message}")
        }
    }

    private fun registerReceivers() {
        if (receiver != null) return
        receiver = MeshBroadcastReceiver()

        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
    }

    // ── Public Hardware Triggers ──────────────────────────────────────

    @SuppressLint("MissingPermission")
    fun startPeerDiscovery() {
        val mgr = wifiP2pManager ?: return
        val ch = getOrInitChannel() ?: return

        _isDiscovering.value = true
        mgr.discoverPeers(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.i(TAG, "Hardware WifiP2p discoverPeers active")
            }
            override fun onFailure(reason: Int) {
                Log.w(TAG, "discoverPeers failed: $reason")
            }
        })

        try {
            if (bluetoothAdapter?.isEnabled == true) {
                if (bluetoothRfcommManager == null) {
                    bluetoothRfcommManager = BluetoothRFCOMMManager(context, networkCallbacks, HARDWARE_DEVICE_NAME)
                }
                bluetoothRfcommManager!!.startServer()
                if (!bluetoothAdapter!!.isDiscovering) {
                    bluetoothAdapter!!.startDiscovery()
                    Log.i(TAG, "Bluetooth startDiscovery active")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "BT discovery error: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopPeerDiscovery() {
        val mgr = wifiP2pManager
        val ch = getOrInitChannel()

        if (mgr != null && ch != null) {
            mgr.stopPeerDiscovery(ch, null)
        }

        try {
            if (bluetoothAdapter?.isDiscovering == true) {
                bluetoothAdapter!!.cancelDiscovery()
            }
        } catch (e: Exception) {
            Log.w(TAG, "BT cancel discovery error: ${e.message}")
        }
        _isDiscovering.value = false
    }

    @SuppressLint("MissingPermission")
    fun startHostBeacon() {
        val mgr = wifiP2pManager
        val ch = getOrInitChannel()

        _isHosting.value = true

        // 1. Start Bluetooth RFCOMM mesh beacon immediately
        try {
            if (bluetoothAdapter?.isEnabled == true) {
                if (bluetoothRfcommManager == null) {
                    bluetoothRfcommManager = BluetoothRFCOMMManager(context, networkCallbacks, HARDWARE_DEVICE_NAME)
                }
                bluetoothRfcommManager!!.startServer()
                Log.i(TAG, "Bluetooth RFCOMM server active as mesh beacon")
            }
        } catch (e: Exception) {
            Log.w(TAG, "BT beacon start error: ${e.message}")
        }

        // 2. Clear any stale Wi-Fi Direct group before creating new host group to avoid BUSY errors
        if (mgr != null && ch != null) {
            try {
                mgr.removeGroup(ch, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        createP2pHostGroup(mgr, ch)
                    }
                    override fun onFailure(reason: Int) {
                        // Stale group was not present, proceed directly
                        createP2pHostGroup(mgr, ch)
                    }
                })
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException in removeGroup: ${e.message}")
                createP2pHostGroup(mgr, ch)
            }
        } else {
            Log.w(TAG, "WifiP2pManager or Channel unavailable; hosting on Bluetooth only")
            _connectedGroupInfo.value = "HOSTING_BT_ONLY"
        }
    }

    @SuppressLint("MissingPermission")
    private fun createP2pHostGroup(mgr: WifiP2pManager, ch: WifiP2pManager.Channel) {
        try {
            mgr.createGroup(ch, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.i(TAG, "Wi-Fi Direct P2P Group Created as Host: $HARDWARE_DEVICE_NAME")
                    _isHosting.value = true
                    _connectedGroupInfo.value = "HOSTING_GROUP"
                    if (socketTransport == null) {
                        socketTransport = SocketTransport(networkCallbacks, HARDWARE_DEVICE_NAME)
                    }
                    socketTransport!!.startServer()
                }
                override fun onFailure(reason: Int) {
                    val reasonStr = when (reason) {
                        WifiP2pManager.P2P_UNSUPPORTED -> "P2P_UNSUPPORTED (Check Wi-Fi & Location)"
                        WifiP2pManager.BUSY -> "BUSY (Wi-Fi P2P framework is busy)"
                        WifiP2pManager.ERROR -> "INTERNAL_ERROR"
                        else -> "Code $reason"
                    }
                    Log.w(TAG, "createGroup failed: $reasonStr")
                    // If Bluetooth RFCOMM server is running, maintain hosting on Bluetooth
                    if (bluetoothRfcommManager != null && bluetoothAdapter?.isEnabled == true) {
                        Log.i(TAG, "Maintaining host beacon via Bluetooth RFCOMM fallback")
                        _isHosting.value = true
                        _connectedGroupInfo.value = "HOSTING_BT_ONLY"
                    } else {
                        _isHosting.value = false
                        _connectedGroupInfo.value = "FAILED: $reasonStr"
                    }
                }
            })
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException in createGroup: ${e.message}")
            if (bluetoothRfcommManager != null && bluetoothAdapter?.isEnabled == true) {
                _isHosting.value = true
                _connectedGroupInfo.value = "HOSTING_BT_ONLY"
            } else {
                _isHosting.value = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in createGroup: ${e.message}")
            _isHosting.value = false
        }
    }

    fun stopHostBeacon() {
        val mgr = wifiP2pManager ?: return
        val ch = getOrInitChannel() ?: return

        mgr.removeGroup(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.i(TAG, "Wi-Fi Direct P2P Group Removed")
                _isHosting.value = false
                _connectedGroupInfo.value = "STANDALONE"
                socketTransport?.stop()
                socketTransport = null
            }
            override fun onFailure(reason: Int) {
                Log.w(TAG, "removeGroup failed: $reason")
                _isHosting.value = false
            }
        })

        bluetoothRfcommManager?.stop()
        bluetoothRfcommManager = null
    }

    // ── Broadcast Receiver Handling ───────────────────────────────────

    @SuppressLint("MissingPermission")
    private fun handlePeersChanged() {
        val mgr = wifiP2pManager ?: return
        val ch = getOrInitChannel() ?: return

        mgr.requestPeers(ch) { peerList: WifiP2pDeviceList ->
            val updatedList = mutableListOf<PeerDevice>()

            peerList.deviceList.forEach { device: WifiP2pDevice ->
                val hardwareId = if (device.deviceName.isNullOrBlank()) {
                    "Node_${device.deviceAddress.replace(":", "").takeLast(6)}"
                } else {
                    device.deviceName
                }

                val peer = PeerDevice(
                    deviceId = device.deviceAddress,
                    deviceName = hardwareId,
                    rssi = when (device.status) {
                        WifiP2pDevice.CONNECTED -> -40
                        WifiP2pDevice.INVITED -> -55
                        else -> -70
                    },
                    connectionType = ConnectionType.WIFI_DIRECT,
                    isConnected = device.status == WifiP2pDevice.CONNECTED,
                    isAuthorized = device.status == WifiP2pDevice.CONNECTED
                )
                updatedList.add(peer)
            }

            _liveDiscoveredPeers.value = updatedList
            Log.d(TAG, "Live hardware peers updated: ${updatedList.size} nodes mapped")
        }
    }

    private fun handleConnectionChanged(intent: Intent) {
        val info: WifiP2pInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO, WifiP2pInfo::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO)
        }

        info?.let {
            if (it.groupFormed) {
                _connectedGroupInfo.value = if (it.isGroupOwner) "GROUP_OWNER" else "CLIENT"
                if (socketTransport == null) {
                    socketTransport = SocketTransport(networkCallbacks, HARDWARE_DEVICE_NAME)
                }
                if (it.isGroupOwner) {
                    socketTransport!!.startServer()
                    Log.i(TAG, "Wi-Fi Direct TCP Server listening on port ${SocketTransport.TCP_PORT}")
                } else {
                    val host = it.groupOwnerAddress?.hostAddress
                    if (host != null) {
                        socketTransport!!.connectToServer(host)
                        Log.i(TAG, "Connecting to Wi-Fi Direct Group Owner at $host:${SocketTransport.TCP_PORT}")
                    }
                }
            } else {
                _connectedGroupInfo.value = "STANDALONE"
                socketTransport?.stop()
                socketTransport = null
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleBluetoothDeviceFound(intent: Intent) {
        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

        val rssi: Short = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, (-75).toShort())

        device?.let { dev ->
            val address = dev.address ?: return
            val name = dev.name ?: "BT_${Build.ID.takeLast(4)}"

            val current = _liveDiscoveredPeers.value.toMutableList()
            val existingIdx = current.indexOfFirst { it.deviceId == address }

            val peer = PeerDevice(
                deviceId = address,
                deviceName = name,
                rssi = rssi.toInt(),
                connectionType = ConnectionType.BLUETOOTH,
                isConnected = false
            )

            if (existingIdx >= 0) {
                current[existingIdx] = peer
            } else {
                current.add(peer)
            }
            _liveDiscoveredPeers.value = current
        }
    }

    inner class MeshBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> handlePeersChanged()
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> handleConnectionChanged(intent)
                BluetoothDevice.ACTION_FOUND -> handleBluetoothDeviceFound(intent)
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Log.d(TAG, "Bluetooth discovery cycle finished")
                }
            }
        }
    }

    /**
     * Connect to a specific peer device manually from the Radar UI.
     */
    @SuppressLint("MissingPermission")
    fun connectToPeer(peer: PeerDevice) {
        when (peer.connectionType) {
            ConnectionType.WIFI_DIRECT -> {
                val mgr = wifiP2pManager ?: return
                val ch = getOrInitChannel() ?: return
                val config = WifiP2pConfig().apply {
                    deviceAddress = peer.deviceId
                }
                mgr.connect(ch, config, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        Log.i(TAG, "Wi-Fi Direct connect initiated to: ${peer.deviceName} (${peer.deviceId})")
                    }
                    override fun onFailure(reason: Int) {
                        Log.w(TAG, "Wi-Fi Direct connect failed: reason=$reason")
                    }
                })
            }
            ConnectionType.BLUETOOTH -> {
                try {
                    val dev = bluetoothAdapter?.getRemoteDevice(peer.deviceId)
                    if (dev != null) {
                        if (bluetoothRfcommManager == null) {
                            bluetoothRfcommManager = BluetoothRFCOMMManager(context, networkCallbacks, HARDWARE_DEVICE_NAME)
                        }
                        bluetoothRfcommManager!!.connectToDevice(dev)
                        Log.i(TAG, "Bluetooth RFCOMM connect initiated to: ${peer.deviceName} (${peer.deviceId})")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Bluetooth connect error: ${e.message}")
                }
            }
        }
    }

    /**
     * Broadcast a TransceiverMessage to all connected Wi-Fi Direct and Bluetooth peers.
     * Streams PTT voice data and sound packets simultaneously across both hardware interfaces.
     */
    fun broadcastMessage(message: TransceiverMessage) {
        scope.launch(Dispatchers.IO) {
            var sentOverWifi = false
            var sentOverBt = false

            try {
                if (socketTransport != null) {
                    socketTransport!!.broadcast(message)
                    sentOverWifi = true
                    Log.d(TAG, "PTT broadcast over Wi-Fi Direct TCP: '${message.text.take(40)}'")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Wi-Fi Direct broadcast error: ${e.message}")
            }

            try {
                if (bluetoothRfcommManager != null) {
                    bluetoothRfcommManager!!.send(message)
                    sentOverBt = true
                    Log.d(TAG, "PTT broadcast over Bluetooth RFCOMM: '${message.text.take(40)}'")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Bluetooth RFCOMM broadcast error: ${e.message}")
            }

            if (!sentOverWifi && !sentOverBt) {
                Log.d(TAG, "Standalone PTT transmission (no active peer sockets): '${message.text.take(40)}'")
            }
        }
    }

    fun isWifiEnabled(): Boolean {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
        return wm?.isWifiEnabled == true
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    fun release() {
        runCatching { receiver?.let { context.unregisterReceiver(it) } }
        receiver = null
        socketTransport?.stop()
        socketTransport = null
        bluetoothRfcommManager?.stop()
        bluetoothRfcommManager = null
    }
}
