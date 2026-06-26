package com.oropeza.urbanapp.core.config

import com.oropeza.urbanapp.core.runtime.UrbanRuntime
import android.content.Context

object UrbanConfigurationManager {
    
    private val repository = UrbanConfigurationRepository()

    fun getRemoteConfig(context: Context): UrbanRemoteConfiguration {
        return UrbanRuntime.workspace(context).configuration
    }

    fun getFeatureFlag(context: Context, key: String, defaultValue: Boolean = false): Boolean {
        return getRemoteConfig(context).featureFlags[key] ?: defaultValue
    }

    fun getConfiguration(): UrbanConfigurationRepository {
        return repository
    }
}
