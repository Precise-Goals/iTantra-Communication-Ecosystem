package com.itantra.domain.model

/**
 * Represents the local device's user profile stored in DataStore.
 */
data class DeviceProfile(
    /** Unique device identifier: UUID-v4 + build fingerprint hash (first 8 chars) */
    val deviceId: String,
    /** User-set display name or callsign. Null if not yet configured. */
    val displayName: String?,
    /** Generated readable build-number alias e.g. "ITantra-A3F7" */
    val buildAlias: String,
    /** Whether the user has completed the initial profile setup */
    val isProfileComplete: Boolean = displayName != null
)
