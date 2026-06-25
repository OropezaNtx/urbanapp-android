package com.oropeza.urbanapp.asd.sync

data class DeviceStatusDto(
    val deviceId: String,
    val currentTripId: Long?,
    val batteryLevel: Int?,
    val gpsStatus: String?,
    val lastLat: Double?,
    val lastLon: Double?,
    val lastFixTime: Long?,
    val appVersion: String,
    val updatedAt: Long = System.currentTimeMillis()
)

data class TrackSummaryDto(
    val tripId: Long,
    val pointCount: Int,
    val totalDistanceM: Double,
    val lastLat: Double?,
    val lastLon: Double?,
    val lastTime: Long?,
    val updatedAt: Long = System.currentTimeMillis()
)
