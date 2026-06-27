package com.oropeza.urbanapp.core.config

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.oropeza.urbanapp.core.runtime.UrbanRuntime
import kotlinx.coroutines.tasks.await

class UrbanConfigurationRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private val gson = Gson()
    
    private companion object {
        const val PREFS_NAME = "urban_remote_config"
        const val KEY_CONFIG_JSON = "current_config_json"
        const val KEY_CONFIG_SOURCE = "config_source"
        const val KEY_LAST_FETCH_AT = "last_fetch_at"
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

    suspend fun fetchRemoteConfiguration(context: Context): UrbanConfiguration {
        val workspace = UrbanRuntime.workspace(context)
        val projectId = workspace.project.projectId
        val snapshot = firestore.collection("app_config")
            .document(projectId)
            .get()
            .await()

        if (!snapshot.exists()) {
            val fallback = getEffectiveConfiguration(context)
            saveLocalConfiguration(context, fallback, source = "REMOTE_MISSING")
            return fallback
        }

        val data = snapshot.data.orEmpty()
        val default = getDefaultConfiguration()
        val featureFlags = data["featureFlags"] as? Map<*, *> ?: emptyMap<String, Any>()
        val enabledModules = data["enabledModules"] as? List<*> ?: default.enabledModules

        val config = UrbanConfiguration(
            environment = data["environment"] as? String ?: default.environment,
            heartbeatIntervalSeconds = readInt(data["heartbeatIntervalSeconds"]) ?: default.heartbeatIntervalSeconds,
            syncIntervalSeconds = readInt(data["syncIntervalSeconds"]) ?: default.syncIntervalSeconds,
            trackChunkSize = readInt(data["trackChunkSize"]) ?: default.trackChunkSize,
            gpsProfile = data["gpsProfile"] as? String ?: default.gpsProfile,
            minSupportedAppVersion = data["minSupportedAppVersion"] as? String ?: default.minSupportedAppVersion,
            enabledModules = enabledModules.mapNotNull { it as? String }.ifEmpty { default.enabledModules },
            featureFlags = default.featureFlags + featureFlags.mapNotNull { (key, value) ->
                val k = key as? String ?: return@mapNotNull null
                val v = value as? Boolean ?: return@mapNotNull null
                k to v
            }.toMap(),
            updatedAt = readLong(data["updatedAt"]) ?: System.currentTimeMillis()
        )

        saveLocalConfiguration(context, config, source = "REMOTE")
        return config
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
            .putLong(KEY_LAST_FETCH_AT, System.currentTimeMillis())
            .apply()
    }

    fun getConfigurationSource(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CONFIG_SOURCE, "DEFAULT") ?: "DEFAULT"
    }

    fun getLastFetchAt(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_FETCH_AT, 0L)
    }

    fun getEffectiveConfiguration(context: Context): UrbanConfiguration {
        return getLocalConfiguration(context) ?: getDefaultConfiguration()
    }

    private fun readInt(value: Any?): Int? = when (value) {
        is Int -> value
        is Long -> value.toInt()
        is Double -> value.toInt()
        is Number -> value.toInt()
        else -> null
    }

    private fun readLong(value: Any?): Long? = when (value) {
        is Long -> value
        is Int -> value.toLong()
        is Double -> value.toLong()
        is Number -> value.toLong()
        else -> null
    }
}
