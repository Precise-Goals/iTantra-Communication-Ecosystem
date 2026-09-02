package com.itantra.data

import android.content.Context
import androidx.room.Room
import com.itantra.data.db.PeerDatabase
import com.itantra.data.db.PeerEntity
import com.itantra.domain.model.ConnectionType
import com.itantra.domain.model.PeerDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Local peer registry backed by Room database.
 * Persists discovered and authorized peer devices across sessions.
 */
class PeerRegistryRepository(context: Context) {

    private val db: PeerDatabase = Room.databaseBuilder(
        context.applicationContext,
        PeerDatabase::class.java,
        "itantra_peers.db"
    ).build()

    private val dao = db.peerDao()

    /** Observe all known peers as domain objects */
    val peers: Flow<List<PeerDevice>> = dao.observeAll().map { entities ->
        entities.map { it.toDomain() }
    }

    /** Upsert a discovered peer — updates RSSI/lastSeen, preserves authorization */
    suspend fun upsertPeer(peer: PeerDevice) {
        val existing = dao.findById(peer.deviceId)
        dao.upsert(
            PeerEntity(
                deviceId = peer.deviceId,
                deviceName = peer.deviceName,
                rssi = peer.rssi,
                connectionType = peer.connectionType.name,
                latencyMs = peer.latencyMs,
                lastSeenAt = peer.lastSeenAt,
                isConnected = peer.isConnected,
                // Preserve existing authorization — don't override with incoming value
                isAuthorized = existing?.isAuthorized ?: peer.isAuthorized
            )
        )
    }

    suspend fun authorizePeer(deviceId: String) {
        dao.setAuthorized(deviceId, true)
    }

    suspend fun revokePeer(deviceId: String) {
        dao.setAuthorized(deviceId, false)
    }

    suspend fun setPeerConnected(deviceId: String, connected: Boolean) {
        dao.setConnected(deviceId, connected)
    }

    suspend fun disconnectAll() {
        dao.disconnectAll()
    }

    suspend fun forgetPeer(deviceId: String) {
        dao.delete(deviceId)
    }

    suspend fun isAuthorized(deviceId: String): Boolean {
        return dao.findById(deviceId)?.isAuthorized == true
    }

    private fun PeerEntity.toDomain() = PeerDevice(
        deviceId = deviceId,
        deviceName = deviceName,
        rssi = rssi,
        connectionType = try {
            ConnectionType.valueOf(connectionType)
        } catch (e: IllegalArgumentException) {
            ConnectionType.WIFI_DIRECT
        },
        latencyMs = latencyMs,
        lastSeenAt = lastSeenAt,
        isConnected = isConnected,
        isAuthorized = isAuthorized
    )
}
