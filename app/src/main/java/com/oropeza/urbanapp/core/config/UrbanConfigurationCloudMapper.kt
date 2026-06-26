package com.oropeza.urbanapp.core.config

object UrbanConfigurationCloudMapper {

    @Suppress("UNCHECKED_CAST")
    fun fromMap(map: Map<String, Any?>): UrbanConfiguration {
        val defaults = UrbanConfigurationRepository().getDefaultConfiguration()
        
        return UrbanConfiguration(
            environment = map["environment"] as? String ?: defaults.environment,
            heartbeatIntervalSeconds = (map["heartbeatIntervalSeconds"] as? Long)?.toInt() 
                ?: (map["heartbeatIntervalSeconds"] as? Int) ?: defaults.heartbeatIntervalSeconds,
            syncIntervalSeconds = (map["syncIntervalSeconds"] as? Long)?.toInt()
                ?: (map["syncIntervalSeconds"] as? Int) ?: defaults.syncIntervalSeconds,
            trackChunkSize = (map["trackChunkSize"] as? Long)?.toInt()
                ?: (map["trackChunkSize"] as? Int) ?: defaults.trackChunkSize,
            gpsProfile = map["gpsProfile"] as? String ?: defaults.gpsProfile,
            minSupportedAppVersion = map["minSupportedAppVersion"] as? String ?: defaults.minSupportedAppVersion,
            enabledModules = (map["enabledModules"] as? List<String>) ?: defaults.enabledModules,
            featureFlags = (map["featureFlags"] as? Map<String, Boolean>) ?: defaults.featureFlags,
            updatedAt = (map["updatedAt"] as? Long) ?: System.currentTimeMillis()
        )
    }

    fun toMap(config: UrbanConfiguration): Map<String, Any?> {
        return mapOf(
            "environment" to config.environment,
            "heartbeatIntervalSeconds" to config.heartbeatIntervalSeconds,
            "syncIntervalSeconds" to config.syncIntervalSeconds,
            "trackChunkSize" to config.trackChunkSize,
            "gpsProfile" to config.gpsProfile,
            "minSupportedAppVersion" to config.minSupportedAppVersion,
            "enabledModules" to config.enabledModules,
            "featureFlags" to config.featureFlags,
            "updatedAt" to config.updatedAt
        )
    }
}
