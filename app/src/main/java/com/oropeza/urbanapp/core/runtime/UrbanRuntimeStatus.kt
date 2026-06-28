package com.oropeza.urbanapp.core.runtime

data class UrbanRuntimeStatus(
    val installationId: String,
    val shortInstallationId: String,
    val workspaceId: String, // standardized to workspaceId
    val projectId: String,
    val environment: String,
    val appVersionName: String,
    val appVersionCode: Long,
    val androidModel: String,
    val pendingSyncCount: Int?,
    val failedSyncCount: Int?,
    val lastSyncAt: Long?,
    val lastSyncError: String?,
    val lastSyncFailedPath: String?,
    val licenseStatus: String,
    val licenseType: String,
    val status: String
)
