package com.itantra.core.network

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import com.itantra.core.proto.ProtobufSerializer
import com.itantra.domain.contracts.NetworkCallbacks
import com.itantra.domain.model.AppResult
import com.itantra.domain.model.ConnectionType
import com.itantra.domain.model.Direction
import com.itantra.domain.model.ErrorCode
import com.itantra.domain.model.MessageType
import com.itantra.domain.model.PeerDevice
import com.itantra.domain.model.TransceiverMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID

/**
 * Bluetooth Classic RFCOMM transport — fallback when Wi-Fi Direct is unavailable.
 *
 * Automatically activated by [NetworkOrchestrator] after 3 consecutive Wi-Fi Direct failures.
 * Uses a well-known UUID for iTantra service discovery and pairing.
 */
class BluetoothRFCOMMManager(
    private val context: Context,
    private val callbacks: NetworkCallbacks,
    private val deviceId: String
) {
    companion object {
        private const val TAG = "BluetoothRFCOMM"
        /** Well-known UUID for iTantra BT service */
        private val SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        private const val SERVICE_NAME = "iTantra"
        private const val MAX_MESSAGE_SIZE = 64 * 1024
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var serverSocket: BluetoothServerSocket? = null
    private val connectedSockets = mutableMapOf<String, BluetoothSocket>()

    val isAvailable: Boolean get() = bluetoothAdapter?.isEnabled == true

    /**
     * Start listening as RFCOMM server.
     */
    fun startServer() {
        if (!isAvailable) {
            callbacks.onNetworkError(
                AppResult.Error(ErrorCode.BLUETOOTH_UNAVAILABLE, "Bluetooth not available")
            )
            return
        }

        scope.launch {
            try {
                @Suppress("MissingPermission")
                serverSocket = bluetoothAdapter!!.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID)
                Log.d(TAG, "RFCOMM server listening")

                while (isActive) {
                    val socket = serverSocket?.accept() ?: break
                    val peerId = socket.remoteDevice.address
                    connectedSockets[peerId] = socket
                    Log.d(TAG, "BT client connected: $peerId")

                    val peer = PeerDevice(
                        deviceId = peerId,
                        deviceName = socket.remoteDevice.name ?: "BT Device",
                        connectionType = ConnectionType.BLUETOOTH,
                        isConnected = true
                    )
                    callbacks.onNodeConnected(peer)
                    launch { handleSocket(socket, peerId) }
                }
            } catch (e: SecurityException) {
                callbacks.onNetworkError(AppResult.Error(ErrorCode.PERMISSION_DENIED, e.message ?: "BT permission denied"))
            } catch (e: IOException) {
                Log.e(TAG, "RFCOMM server error: ${e.message}")
            }
        }
    }

    /**
     * Connect to a known paired Bluetooth device.
     */
    fun connectToDevice(device: BluetoothDevice) {
        scope.launch {
            try {
                @Suppress("MissingPermission")
                val socket = device.createRfcommSocketToServiceRecord(SERVICE_UUID)
                socket.connect()
                val peerId = device.address
                connectedSockets[peerId] = socket

                val peer = PeerDevice(
                    deviceId = peerId,
                    deviceName = device.name ?: "BT Device",
                    connectionType = ConnectionType.BLUETOOTH,
                    isConnected = true
                )
                callbacks.onNodeConnected(peer)
                handleSocket(socket, peerId)
            } catch (e: SecurityException) {
                callbacks.onNetworkError(AppResult.Error(ErrorCode.PERMISSION_DENIED, e.message ?: "BT permission denied"))
            } catch (e: IOException) {
                Log.e(TAG, "BT connect error: ${e.message}")
                callbacks.onNetworkError(
                    AppResult.Error(ErrorCode.NETWORK_TIMEOUT, "BT connection failed: ${e.message}")
                )
            }
        }
    }

    fun send(message: TransceiverMessage, targetDeviceId: String? = null) {
        val encoded = ProtobufSerializer.encode(message)
        scope.launch {
            val targets = if (targetDeviceId != null) {
                listOf(connectedSockets[targetDeviceId])
            } else {
                connectedSockets.values.toList()
            }
            targets.filterNotNull().forEach { socket ->
                try {
                    socket.outputStream.write(encoded)
                    socket.outputStream.flush()
                } catch (e: IOException) {
                    Log.e(TAG, "BT send error: ${e.message}")
                }
            }
        }
    }

    private suspend fun handleSocket(socket: BluetoothSocket, peerId: String) {
        val lengthBuffer = ByteArray(4)
        try {
            val input = socket.inputStream
            while (isActive && socket.isConnected) {
                val read = input.read(lengthBuffer)
                if (read < 4) break

                val payloadLength = ProtobufSerializer.readLengthPrefix(lengthBuffer)
                if (payloadLength <= 0 || payloadLength > MAX_MESSAGE_SIZE) continue

                val payload = ByteArray(payloadLength)
                var offset = 0
                while (offset < payloadLength) {
                    val n = input.read(payload, offset, payloadLength - offset)
                    if (n < 0) break
                    offset += n
                }

                val message = ProtobufSerializer.decode(payload, Direction.RECEIVED)
                if (message.type != MessageType.PING && message.type != MessageType.ACK) {
                    callbacks.onTextReceived(message)
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "BT socket read error for $peerId: ${e.message}")
        } finally {
            connectedSockets.remove(peerId)
            callbacks.onNodeDisconnected(peerId)
            runCatching { socket.close() }
        }
    }

    fun stop() {
        connectedSockets.values.forEach { runCatching { it.close() } }
        connectedSockets.clear()
        runCatching { serverSocket?.close() }
        Log.d(TAG, "BluetoothRFCOMMManager stopped")
    }
}
