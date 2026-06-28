package com.oropeza.urbanapp.core.platform

data class UrbanInstallation(
    val installationId: String,
    val workspaceId: String?, // Maps to organizationId internally
    val projectId: String?,
    val licenseId: String?,
    val androidId: String?,
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val sdkInt: Int,
    val appVersionName: String,
    val appVersionCode: Long,
    val packageName: String,
    val registeredAt: Long?,
    val lastSeenAt: Long?,
    val lastLicenseCheckAt: Long?,
    val lastHeartbeatAt: Long?,
    val lastSyncAt: Long?,
    val installationVersion: String?,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long
)
