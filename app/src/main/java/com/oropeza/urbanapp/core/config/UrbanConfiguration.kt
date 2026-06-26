package com.oropeza.urbanapp.core.config

data class UrbanConfiguration(
    val environment: String,
    val heartbeatIntervalSeconds: Int,
    val syncIntervalSeconds: Int,
    val trackChunkSize: Int,
    val gpsProfile: String,
    val minSupportedAppVersion: String?,
    val enabledModules: List<String>,
    val featureFlags: Map<String, Boolean>,
    val updatedAt: Long
)
