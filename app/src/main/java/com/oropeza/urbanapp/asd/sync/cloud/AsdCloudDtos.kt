package com.oropeza.urbanapp.asd.sync.cloud

data class AsdTripCloudDto(
    val cloudTripId: String,
    val localTripId: Long,
    val organizationId: String,
    val projectId: String,
    val workspaceEnvironment: String,
    val routeId: String,
    val routeName: String,
    val direction: String,
    val tripNumber: Int?,
    val vehicleType: String?,
    val vehicleEco: String?,
    val plateNumber: String?,
    val observerName: String?,
    val supervisorName: String?,
    val deviceInstallationId: String,
    val appVersionName: String,
    val appVersionCode: Long,
    val startTime: Long,
    val endTime: Long?,
    val status: String, // ACTIVE / CLOSED
    val createdAt: Long,
    val updatedAt: Long
)

data class AsdEventCloudDto(
    val cloudEventId: String,
    val cloudTripId: String,
    val localEventId: Long,
    val localTripId: Long,
    val waypointStartId: Long?,
    val waypointStopId: Long?,
    val eventType: String,
    val timestamp: Long,
    val startTime: Long?,
    val stopTime: Long?,
    val lat: Double,
    val lon: Double,
    val alt: Double?,
    val accuracy: Double,
    val menUp: Int,
    val womenUp: Int,
    val menDown: Int,
    val womenDown: Int,
    val onboardMen: Int,
    val onboardWomen: Int,
    val delayCodes: String?,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long
)

data class AsdTrackChunkCloudDto(
    val chunkId: String,
    val cloudTripId: String,
    val localTripId: Long,
    val chunkIndex: Int,
    val pointCount: Int,
    val points: List<AsdTrackPointDto>,
    val startTime: Long,
    val endTime: Long,
    val createdAt: Long,
    val updatedAt: Long
)

data class AsdTrackPointDto(
    val lat: Double,
    val lon: Double,
    val alt: Double?,
    val accuracy: Double,
    val time: Long,
    val provider: String?
)
