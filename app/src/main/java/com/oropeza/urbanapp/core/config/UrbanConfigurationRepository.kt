package com.oropeza.urbanapp.core.config

import android.content.Context
import com.google.gson.Gson

class UrbanConfigurationRepository {

    private val gson = Gson()
    
    private companion object {
        const val PREFS_NAME = "urban_remote_config"
        const val KEY_CONFIG_JSON = "current_config_json"
        const val KEY_CONFIG_SOURCE = "config_source"
    }

    fun getDefaultConfiguration(): UrbanConfiguration {
        return UrbanConfiguration(
            environment = "pilot",
            heartbeatIntervalSeconds = 60,
            syncIntervalSeconds = 300,
            trackChunkSize = 50,
            gpsProfile = "BALANCED",
            minSupportedAppVersion = "0.9.0",
            enabledModules = listOf("ASD", "FOV", "CC"),
            featureFlags = mapOf(
                UrbanFeatureFlags.ASD_ENABLED to true,
                UrbanFeatureFlags.ASD_EXPORT_ENABLED to true,
                UrbanFeatureFlags.CLOUD_SYNC_ENABLED to true,
                UrbanFeatureFlags.HEARTBEAT_ENABLED to true,
                UrbanFeatureFlags.DIAGNOSTICS_ENABLED to true,
                UrbanFeatureFlags.LICENSING_ENFORCEMENT_ENABLED to false,
                UrbanFeatureFlags.REALTIME_ENABLED to false,
                UrbanFeatureFlags.ANALYTICS_ENABLED to false,
                UrbanFeatureFlags.REMOTE_CONFIG_ENABLED to true
            ),
            updatedAt = System.currentTimeMillis()
        )
    }

    fun getLocalConfiguration(context: Context): UrbanConfiguration? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_CONFIG_JSON, null)
        return try {
            if (json != null) gson.fromJson(json, UrbanConfiguration::class.java) else null
        } catch (e: Exception) {
            null
        }
    }

    fun saveLocalConfiguration(context: Context, config: UrbanConfiguration, source: String = "LOCAL") {
        val json = gson.toJson(config)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CONFIG_JSON, json)
            .putString(KEY_CONFIG_SOURCE, source)
            .apply()
    }

    fun getConfigurationSource(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CONFIG_SOURCE, "DEFAULT") ?: "DEFAULT"
    }

    fun getEffectiveConfiguration(context: Context): UrbanConfiguration {
        return getLocalConfiguration(context) ?: getDefaultConfiguration()
    }
}
