package com.oropeza.urbanapp.core.runtime

data class UrbanRuntimeStatus(
    val installationId: String,
    val shortInstallationId: String,
    val organizationId: String,
    val projectId: String,
    val environment: String,
    val appVersionName: String,
    val appVersionCode: Long,
    val androidModel: String,
    val pendingSyncCount: Int?,
    val failedSyncCount: Int?,
    val lastSyncAt: Long?,
    val status: String
)
