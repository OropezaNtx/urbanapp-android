package com.oropeza.urbanapp.core.platform

data class UrbanHeartbeat(
    val installationId: String,
    val organizationId: String?,
    val projectId: String?,
    val activeTripId: String?,
    val gpsStatus: String?,
    val batteryLevel: Int?,
    val pendingSyncCount: Int,
    val failedSyncCount: Int,
    val appVersionName: String,
    val appVersionCode: Long,
    val lastLat: Double?,
    val lastLon: Double?,
    val lastFixTime: Long?,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long
)
