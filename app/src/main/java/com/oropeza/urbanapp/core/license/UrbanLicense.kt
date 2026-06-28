package com.oropeza.urbanapp.core.license

data class UrbanLicense(
    val licenseId: String,
    val workspaceId: String, // standardized to workspaceId
    val projectId: String,
    val status: UrbanLicenseStatus,
    val type: UrbanLicenseType,
    val enabledModules: List<String>,
    val enabledFeatures: List<String>,
    val maxUsers: Int,
    val maxDevices: Int,
    val expirationAt: Long,
    val gracePeriodDays: Int,
    val createdAt: Long,
    val updatedAt: Long
)
