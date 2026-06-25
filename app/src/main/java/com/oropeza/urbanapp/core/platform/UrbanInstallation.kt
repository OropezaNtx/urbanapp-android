package com.oropeza.urbanapp.core.platform

data class UrbanInstallation(
    val installationId: String,
    val organizationId: String?,
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
    val status: String,
    val createdAt: Long,
    val updatedAt: Long
)
