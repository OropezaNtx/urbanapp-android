package com.oropeza.urbanapp.core.config

import android.content.Context

object UrbanConfigurationManager {
    
    private val repository = UrbanConfigurationRepository()

    fun configuration(context: Context): UrbanConfiguration {
        return repository.getEffectiveConfiguration(context)
    }

    suspend fun refreshRemote(context: Context): UrbanConfiguration {
        return repository.fetchRemoteConfiguration(context)
    }

    fun isFeatureEnabled(context: Context, flag: String): Boolean {
        return configuration(context).featureFlags[flag] ?: false
    }

    fun enabledModules(context: Context): List<String> {
        return configuration(context).enabledModules
    }

    fun heartbeatInterval(context: Context): Int {
        return configuration(context).heartbeatIntervalSeconds
    }

    fun syncInterval(context: Context): Int {
        return configuration(context).syncIntervalSeconds
    }

    fun trackChunkSize(context: Context): Int {
        return configuration(context).trackChunkSize
    }

    fun gpsProfile(context: Context): String {
        return configuration(context).gpsProfile
    }

    fun configurationSource(context: Context): String {
        return repository.getConfigurationSource(context)
    }

    fun lastFetchAt(context: Context): Long {
        return repository.getLastFetchAt(context)
    }
    
    // Internal API for Repository access
    fun getRepository(): UrbanConfigurationRepository = repository
}
