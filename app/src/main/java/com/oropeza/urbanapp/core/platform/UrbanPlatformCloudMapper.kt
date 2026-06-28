package com.oropeza.urbanapp.core.platform

object UrbanPlatformCloudMapper {

    fun installationToMap(installation: UrbanInstallation): Map<String, Any?> {
        return mapOf(
            "installationId" to installation.installationId,
            "workspaceId" to installation.workspaceId,
            "projectId" to installation.projectId,
            "licenseId" to installation.licenseId,
            "androidId" to installation.androidId,
            "manufacturer" to installation.manufacturer,
            "model" to installation.model,
            "androidVersion" to installation.androidVersion,
            "sdkInt" to installation.sdkInt,
            "appVersionName" to installation.appVersionName,
            "appVersionCode" to installation.appVersionCode,
            "packageName" to installation.packageName,
            "registeredAt" to installation.registeredAt,
            "lastSeenAt" to installation.lastSeenAt,
            "lastLicenseCheckAt" to installation.lastLicenseCheckAt,
            "lastHeartbeatAt" to installation.lastHeartbeatAt,
            "lastSyncAt" to installation.lastSyncAt,
            "installationVersion" to installation.installationVersion,
            "status" to installation.status,
            "createdAt" to installation.createdAt,
            "updatedAt" to installation.updatedAt
        )
    }

    fun heartbeatToMap(heartbeat: UrbanHeartbeat): Map<String, Any?> {
        return mapOf(
            "installationId" to heartbeat.installationId,
            "workspaceId" to heartbeat.workspaceId,
            "projectId" to heartbeat.projectId,
            "activeTripId" to heartbeat.activeTripId,
            "gpsStatus" to heartbeat.gpsStatus,
            "batteryLevel" to heartbeat.batteryLevel,
            "pendingSyncCount" to heartbeat.pendingSyncCount,
            "failedSyncCount" to heartbeat.failedSyncCount,
            "appVersionName" to heartbeat.appVersionName,
            "appVersionCode" to heartbeat.appVersionCode,
            "lastLat" to heartbeat.lastLat,
            "lastLon" to heartbeat.lastLon,
            "lastFixTime" to heartbeat.lastFixTime,
            "status" to heartbeat.status,
            "createdAt" to heartbeat.createdAt,
            "updatedAt" to heartbeat.updatedAt
        )
    }
}
