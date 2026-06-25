package com.oropeza.urbanapp.core.config

data class UrbanRemoteConfiguration(
    val heartbeatIntervalMs: Long = 60_000L,
    val syncIntervalMs: Long = 300_000L,
    val uploadChunkSize: Int = 50,
    val gpsProfile: String = "BALANCED",
    val enabledModules: List<String> = listOf("ASD", "FOV", "CC"),
    val featureFlags: Map<String, Boolean> = emptyMap()
)
