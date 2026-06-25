package com.oropeza.urbanapp.core.config

class UrbanConfigurationRepository {
    
    // For now, only provides local defaults as per mission requirements.
    // Architecture is prepared for future Firestore/RemoteConfig integration.

    fun getRemoteConfiguration(): UrbanRemoteConfiguration {
        return UrbanRemoteConfiguration()
    }

    fun getFeatureFlag(key: String, defaultValue: Boolean = false): Boolean {
        return getRemoteConfiguration().featureFlags[key] ?: defaultValue
    }

    fun getString(key: String, defaultValue: String = ""): String {
        return when (key) {
            "gps_profile" -> getRemoteConfiguration().gpsProfile
            else -> defaultValue
        }
    }

    fun getInt(key: String, defaultValue: Int = 0): Int {
        return when (key) {
            "upload_chunk_size" -> getRemoteConfiguration().uploadChunkSize
            else -> defaultValue
        }
    }

    fun getLong(key: String, defaultValue: Long = 0L): Long {
        return when (key) {
            "heartbeat_interval" -> getRemoteConfiguration().heartbeatIntervalMs
            "sync_interval" -> getRemoteConfiguration().syncIntervalMs
            else -> defaultValue
        }
    }
    
    fun getDouble(key: String, defaultValue: Double = 0.0): Double {
        return defaultValue
    }
}
