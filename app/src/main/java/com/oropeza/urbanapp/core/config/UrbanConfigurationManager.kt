package com.oropeza.urbanapp.core.config

object UrbanConfigurationManager {
    
    private val repository = UrbanConfigurationRepository()

    fun getRemoteConfig(): UrbanRemoteConfiguration {
        return repository.getRemoteConfiguration()
    }

    fun getFeatureFlag(key: String, defaultValue: Boolean = false): Boolean {
        return repository.getFeatureFlag(key, defaultValue)
    }

    fun getConfiguration(): UrbanConfigurationRepository {
        return repository
    }
}
