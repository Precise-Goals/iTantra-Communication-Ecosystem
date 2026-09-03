package com.itantra.core.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.util.Log
import com.itantra.domain.contracts.NetworkCallbacks
import com.itantra.domain.model.AppResult
import com.itantra.domain.model.ConnectionType
import com.itantra.domain.model.ErrorCode
import com.itantra.domain.model.PeerDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Manages Wi-Fi Direct peer discovery and connection via [WifiP2pManager].
 *
 * This is the primary transport for iTantra. Text data flows over TCP sockets
 * established after a successful Wi-Fi Direct group formation.
 *
 * Lifecycle: Create in Foreground Service. Call [register] on service start,
 * [unregister] on service stop.
 */
class WifiDirectManager(
    private val context: Context,
    private val callbacks: NetworkCallbacks,
    private val socketTransport: SocketTransport,
    private val deviceId: String
) {
    companion object {
        private const val TAG = "WifiDirectManager"
        private const val DISCOVERY_INTERVAL_MS = 15_000L
        private const val MAX_RECONNECT_ATTEMPTS = 3
    }

    private val manager: WifiP2pManager by lazy {
        context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    }
    private var channel: WifiP2pManager.Channel? = null
    private var receiver: WifiP2pBroadcastReceiver? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var reconnectAttempts = 0
    private var isGroupOwner = false

    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    fun register() {
        channel = manager.initialize(context, context.mainLooper, null)
        receiver = WifiP2pBroadcastReceiver()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, intentFilter)
        }
        startDiscovery()
    }

    fun unregister() {
        runCatching { context.unregisterReceiver(receiver) }
        channel?.close()
        channel = null
        Log.d(TAG, "WifiDirectManager unregistered")
    }

    fun startDiscovery() {
        val ch = channel ?: return
        manager.discoverPeers(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Peer discovery started")
                // Schedule re-discovery to keep the peer list fresh
                scope.launch {
                    delay(DISCOVERY_INTERVAL_MS)
                    startDiscovery()
                }
            }
            override fun onFailure(reason: Int) {
                Log.w(TAG, "Discovery failed: reason=$reason")
                callbacks.onNetworkError(
                    AppResult.Error(ErrorCode.WIFI_DIRECT_UNAVAILABLE, "Discovery failed: $reason")
                )
            }
        })
    }

    fun connectToPeer(device: WifiP2pDevice) {
        connectToPeerAddress(device.deviceAddress, device.deviceName)
    }

    /**
     * Connect by MAC address alone — used when the caller only has a [com.itantra.domain.model.PeerDevice]
     * DTO (e.g. from [MeshHardwareManager]'s discovery), not a raw [WifiP2pDevice].
     */
    fun connectToPeerAddress(address: String, label: String = address) {
        val ch = channel ?: return
        val config = WifiP2pConfig().apply { deviceAddress = address }
        manager.connect(ch, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Connection initiated to $label")
                reconnectAttempts = 0
            }
            override fun onFailure(reason: Int) {
                Log.w(TAG, "Connect failed: reason=$reason")
                handleConnectionFailure()
            }
        })
    }

    fun disconnect() {
        val ch = channel ?: return
        manager.removeGroup(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { Log.d(TAG, "Group removed") }
            override fun onFailure(reason: Int) { Log.w(TAG, "Remove group failed: $reason") }
        })
    }

    private fun handleConnectionFailure() {
        reconnectAttempts++
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.w(TAG, "Max reconnect attempts reached. Signaling BT fallback.")
            callbacks.onNetworkError(
                AppResult.Error(
                    ErrorCode.WIFI_DIRECT_UNAVAILABLE,
                    "Wi-Fi Direct unavailable after $MAX_RECONNECT_ATTEMPTS attempts. Switching to Bluetooth."
                )
            )
        } else {
            val backoffMs = (500L * (1L shl reconnectAttempts)).coerceAtMost(30_000L)
            scope.launch {
                delay(backoffMs)
                startDiscovery()
            }
        }
    }

    private fun onPeersChanged() {
        val ch = channel ?: return
        manager.requestPeers(ch) { peers ->
            peers.deviceList.forEach { device ->
                val peer = PeerDevice(
                    deviceId = device.deviceAddress,
                    deviceName = device.deviceName.ifEmpty { "iTantra Device" },
                    connectionType = ConnectionType.WIFI_DIRECT,
                    isConnected = device.status == WifiP2pDevice.CONNECTED
                )
                callbacks.onNodeDiscovered(peer)
            }
        }
    }

    private fun onConnectionChanged(info: WifiP2pInfo) {
        if (info.groupFormed) {
            isGroupOwner = info.isGroupOwner
            Log.d(TAG, "Group formed. isGroupOwner=$isGroupOwner, groupOwnerAddress=${info.groupOwnerAddress}")

            if (isGroupOwner) {
                socketTransport.startServer()
            } else {
                val ownerAddress = info.groupOwnerAddress?.hostAddress ?: return
                socketTransport.connectToServer(ownerAddress)
            }
        } else {
            Log.d(TAG, "Group dissolved")
        }
    }

    inner class WifiP2pBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    Log.d(TAG, "Wi-Fi P2P state: $state")
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> onPeersChanged()
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    val networkInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO, WifiP2pInfo::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO)
                    }
                    networkInfo?.let { onConnectionChanged(it) }
                }
                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                    Log.d(TAG, "This device changed")
                }
            }
        }
    }
}
