package com.itantra.core.network

import android.util.Log
import com.itantra.core.proto.ProtobufSerializer
import com.itantra.domain.contracts.NetworkCallbacks
import com.itantra.domain.model.AppResult
import com.itantra.domain.model.Direction
import com.itantra.domain.model.ErrorCode
import com.itantra.domain.model.MessageType
import com.itantra.domain.model.TransceiverMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * TCP socket transport layer for Wi-Fi Direct peer-to-peer communication.
 *
 * Protocol:
 * - Group Owner acts as TCP server on port 8765.
 * - All messages are length-prefixed Protobuf binary frames.
 * - Frame format: [4-byte big-endian Int][protobuf payload]
 *
 * This class handles both server (Group Owner) and client (peer) roles.
 */
class SocketTransport(
    private val callbacks: NetworkCallbacks,
    private val deviceId: String = UUID.randomUUID().toString()
) {
    companion object {
        private const val TAG = "SocketTransport"
        const val TCP_PORT = 8765
        private const val PING_INTERVAL_MS = 5000L
        private const val MAX_MESSAGE_SIZE = 64 * 1024 // 64KB max per message
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val connectedSockets = ConcurrentHashMap<String, Socket>()
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private var pingJob: Job? = null

    /**
     * Start TCP server (Group Owner role).
     * Accepts incoming connections from peer devices.
     */
    fun startServer() {
        serverJob = scope.launch {
            try {
                serverSocket = ServerSocket(TCP_PORT)
                Log.d(TAG, "TCP server listening on port $TCP_PORT")

                while (isActive) {
                    val clientSocket = serverSocket?.accept() ?: break
                    val peerId = clientSocket.inetAddress.hostAddress ?: "unknown"
                    connectedSockets[peerId] = clientSocket
                    Log.d(TAG, "Client connected: $peerId")
                    launch { handleIncomingSocket(clientSocket, peerId) }
                }
            } catch (e: IOException) {
                if (isActive) {
                    Log.e(TAG, "Server socket error: ${e.message}")
                    callbacks.onNetworkError(
                        AppResult.Error(ErrorCode.SOCKET_ERROR, "Server error: ${e.message}")
                    )
                }
            }
        }
        startPingLoop()
    }

    /**
     * Connect to Group Owner as a TCP client.
     * @param groupOwnerAddress IP address of the Wi-Fi Direct Group Owner.
     */
    fun connectToServer(groupOwnerAddress: String) {
        scope.launch {
            try {
                val socket = Socket(groupOwnerAddress, TCP_PORT)
                connectedSockets[groupOwnerAddress] = socket
                Log.d(TAG, "Connected to server: $groupOwnerAddress")
                handleIncomingSocket(socket, groupOwnerAddress)
            } catch (e: IOException) {
                Log.e(TAG, "Client connection error: ${e.message}")
                callbacks.onNetworkError(
                    AppResult.Error(ErrorCode.NETWORK_TIMEOUT, "Connection failed: ${e.message}")
                )
            }
        }
    }

    /**
     * Send a [TransceiverMessage] to a specific peer by device ID.
     */
    fun send(message: TransceiverMessage, targetDeviceId: String? = null) {
        val encoded = ProtobufSerializer.encode(message)
        scope.launch {
            val targets = if (targetDeviceId != null) {
                listOf(connectedSockets[targetDeviceId])
            } else {
                connectedSockets.values.toList()
            }

            targets.forEach { socket ->
                try {
                    socket?.let {
                        val out = DataOutputStream(it.getOutputStream())
                        out.write(encoded)
                        out.flush()
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Send error: ${e.message}")
                }
            }
        }
    }

    /**
     * Broadcast a [TransceiverMessage] to all connected peers.
     */
    fun broadcast(message: TransceiverMessage) = send(message, null)

    /** Handle an accepted/connected socket: read length-prefixed Protobuf frames in a loop. */
    private suspend fun handleIncomingSocket(socket: Socket, peerId: String) {
        withContext(Dispatchers.IO) {
            try {
                val input = DataInputStream(socket.getInputStream())
                val lengthBuffer = ByteArray(4)

                while (isActive && !socket.isClosed) {
                    // Read 4-byte length prefix
                    val bytesRead = input.read(lengthBuffer)
                    if (bytesRead < 4) break

                    val payloadLength = ProtobufSerializer.readLengthPrefix(lengthBuffer)
                    if (payloadLength <= 0 || payloadLength > MAX_MESSAGE_SIZE) {
                        Log.w(TAG, "Invalid message length: $payloadLength")
                        continue
                    }

                    // Read exact payload
                    val payload = ByteArray(payloadLength)
                    input.readFully(payload)

                    // Decode Protobuf
                    val message = ProtobufSerializer.decode(payload, Direction.RECEIVED)

                    when (message.type) {
                        MessageType.PING -> {
                            // Respond with ACK and measure latency
                            val ack = message.copy(
                                type = MessageType.ACK,
                                senderId = deviceId,
                                timestamp = System.currentTimeMillis()
                            )
                            send(ack, peerId)
                        }
                        MessageType.ACK -> {
                            val latency = System.currentTimeMillis() - message.timestamp
                            callbacks.onLatencyMeasured(peerId, latency)
                        }
                        else -> callbacks.onTextReceived(message)
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Socket read error for $peerId: ${e.message}")
                connectedSockets.remove(peerId)
                callbacks.onNodeDisconnected(peerId)
                callbacks.onNetworkError(
                    AppResult.Error(ErrorCode.NETWORK_DROPPED, "Peer $peerId disconnected")
                )
            } finally {
                runCatching { socket.close() }
                connectedSockets.remove(peerId)
            }
        }
    }

    /** Periodic PING to all connected peers for latency measurement and keepalive. */
    private fun startPingLoop() {
        pingJob = scope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(PING_INTERVAL_MS)
                val ping = TransceiverMessage(
                    type = MessageType.PING,
                    text = "",
                    srcLang = "",
                    dstLang = "",
                    senderId = deviceId,
                    timestamp = System.currentTimeMillis()
                )
                broadcast(ping)
            }
        }
    }

    fun stop() {
        serverJob?.cancel()
        pingJob?.cancel()
        connectedSockets.values.forEach { runCatching { it.close() } }
        connectedSockets.clear()
        runCatching { serverSocket?.close() }
        Log.d(TAG, "SocketTransport stopped")
    }

    fun getConnectedPeerCount(): Int = connectedSockets.size
}
