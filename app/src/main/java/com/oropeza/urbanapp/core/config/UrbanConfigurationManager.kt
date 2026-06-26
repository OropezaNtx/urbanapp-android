package com.oropeza.urbanapp.core.config

import android.content.Context
import com.oropeza.urbanapp.core.runtime.UrbanRuntime

object UrbanConfigurationManager {
    
    private val repository = UrbanConfigurationRepository()

    fun configuration(context: Context): UrbanConfiguration {
        // In the future, this can prioritize Workspace memory state.
        // For now, delegates to repository's effective calculation.
        return repository.getEffectiveConfiguration(context)
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
    
    // Internal API for Repository access
    fun getRepository(): UrbanConfigurationRepository = repository
}
