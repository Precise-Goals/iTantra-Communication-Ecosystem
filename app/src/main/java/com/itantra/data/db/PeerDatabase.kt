package com.itantra.data.db

import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.RoomDatabase
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

// ==================== ENTITY ====================

@Entity(tableName = "peers")
data class PeerEntity(
    @PrimaryKey val deviceId: String,
    val deviceName: String,
    val rssi: Int = -70,
    val connectionType: String = "WIFI_DIRECT",
    val latencyMs: Long = 0L,
    val lastSeenAt: Long = System.currentTimeMillis(),
    val isConnected: Boolean = false,
    val isAuthorized: Boolean = false
)

// ==================== DAO ====================

@Dao
interface PeerDao {

    @Query("SELECT * FROM peers ORDER BY lastSeenAt DESC")
    fun observeAll(): Flow<List<PeerEntity>>

    @Query("SELECT * FROM peers WHERE deviceId = :deviceId LIMIT 1")
    suspend fun findById(deviceId: String): PeerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(peer: PeerEntity)

    @Update
    suspend fun update(peer: PeerEntity)

    @Query("UPDATE peers SET isAuthorized = :authorized WHERE deviceId = :deviceId")
    suspend fun setAuthorized(deviceId: String, authorized: Boolean)

    @Query("UPDATE peers SET isConnected = 0")
    suspend fun disconnectAll()

    @Query("UPDATE peers SET isConnected = :connected WHERE deviceId = :deviceId")
    suspend fun setConnected(deviceId: String, connected: Boolean)

    @Query("DELETE FROM peers WHERE deviceId = :deviceId")
    suspend fun delete(deviceId: String)

    @Query("SELECT COUNT(*) FROM peers WHERE isAuthorized = 1")
    suspend fun countAuthorized(): Int
}

// ==================== DATABASE ====================

@Database(
    entities = [PeerEntity::class],
    version = 1,
    exportSchema = false
)
abstract class PeerDatabase : RoomDatabase() {
    abstract fun peerDao(): PeerDao
}
