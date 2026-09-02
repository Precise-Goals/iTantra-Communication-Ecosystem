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
import com.itantra.domain.model.ConnectionType
import com.itantra.domain.model.PeerDevice
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
            wifiP2pManager?.let { mgr ->
                wifiP2pChannel = mgr.initialize(context, context.mainLooper, null)
                setHardwareDeviceName(HARDWARE_DEVICE_NAME)
            }

            registerReceivers()
        } catch (e: Exception) {
            Log.e(TAG, "Hardware init error: ${e.message}")
        }
    }

    /**
     * Sets the local device broadcast name via reflection on WifiP2pManager.
     */
    private fun setHardwareDeviceName(name: String) {
        val ch = wifiP2pChannel ?: return
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
        val ch = wifiP2pChannel ?: return

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
            if (bluetoothAdapter?.isEnabled == true && !bluetoothAdapter!!.isDiscovering) {
                bluetoothAdapter!!.startDiscovery()
                Log.i(TAG, "Bluetooth startDiscovery active")
            }
        } catch (e: Exception) {
            Log.w(TAG, "BT discovery error: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopPeerDiscovery() {
        val mgr = wifiP2pManager
        val ch = wifiP2pChannel

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

    fun startHostBeacon() {
        val mgr = wifiP2pManager ?: return
        val ch = wifiP2pChannel ?: return

        _isHosting.value = true
        mgr.createGroup(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.i(TAG, "Wi-Fi Direct P2P Group Created as Host: $HARDWARE_DEVICE_NAME")
                _connectedGroupInfo.value = "HOSTING_GROUP"
            }
            override fun onFailure(reason: Int) {
                Log.w(TAG, "createGroup failed: $reason")
                _isHosting.value = false
            }
        })
    }

    fun stopHostBeacon() {
        val mgr = wifiP2pManager ?: return
        val ch = wifiP2pChannel ?: return

        mgr.removeGroup(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.i(TAG, "Wi-Fi Direct P2P Group Removed")
                _isHosting.value = false
                _connectedGroupInfo.value = "STANDALONE"
            }
            override fun onFailure(reason: Int) {
                Log.w(TAG, "removeGroup failed: $reason")
                _isHosting.value = false
            }
        })
    }

    // ── Broadcast Receiver Handling ───────────────────────────────────

    @SuppressLint("MissingPermission")
    private fun handlePeersChanged() {
        val mgr = wifiP2pManager ?: return
        val ch = wifiP2pChannel ?: return

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
            } else {
                _connectedGroupInfo.value = "STANDALONE"
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

    fun release() {
        runCatching { receiver?.let { context.unregisterReceiver(it) } }
        receiver = null
    }
}
