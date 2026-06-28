package com.oropeza.urbanapp.core.platform

data class UrbanLicense(
    val licenseId: String,
    val workspaceId: String?, // standardized to workspaceId
    val projectId: String?,
    val type: String,
    val status: String,
    val expirationAt: Long?,
    val maxDevices: Int?,
    val enabledModulesJson: String?,
    val signature: String?,
    val createdAt: Long,
    val updatedAt: Long
)
