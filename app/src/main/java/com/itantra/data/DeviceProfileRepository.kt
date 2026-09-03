package com.itantra.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.itantra.domain.model.DeviceProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.util.UUID

private val Context.profileDataStore by preferencesDataStore("device_profile")

/**
 * Manages the local device identity stored in DataStore preferences.
 * Generates a unique device ID on first launch from UUID + build fingerprint.
 */
class DeviceProfileRepository(private val context: Context) {

    private object Keys {
        val DEVICE_ID = stringPreferencesKey("device_id")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val BUILD_ALIAS = stringPreferencesKey("build_alias")
    }

    val profileFlow: Flow<DeviceProfile> = context.profileDataStore.data.map { prefs ->
        val deviceId = prefs[Keys.DEVICE_ID] ?: generateAndSaveDeviceId()
        val buildAlias = prefs[Keys.BUILD_ALIAS] ?: buildAlias(deviceId)
        DeviceProfile(
            deviceId = deviceId,
            displayName = prefs[Keys.DISPLAY_NAME],
            buildAlias = buildAlias
        )
    }

    suspend fun saveDisplayName(name: String) {
        context.profileDataStore.edit { prefs ->
            prefs[Keys.DISPLAY_NAME] = name.trim()
        }
    }

    private suspend fun generateAndSaveDeviceId(): String {
        val id = UUID.randomUUID().toString()
        val alias = buildAlias(id)
        context.profileDataStore.edit { prefs ->
            prefs[Keys.DEVICE_ID] = id
            prefs[Keys.BUILD_ALIAS] = alias
        }
        return id
    }

    /** Generates a short readable alias like "ITantra-A3F7" from UUID */
    private fun buildAlias(uuid: String): String {
        val fingerprint = android.os.Build.FINGERPRINT + uuid
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(fingerprint.toByteArray())
        val hex = digest.take(2).joinToString("") { "%02X".format(it) }
        return "ITantra-$hex"
    }
}
