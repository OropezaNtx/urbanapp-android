package com.oropeza.urbanapp.core.license

data class UrbanLicense(
    val licenseId: String,
    val organizationId: String,
    val projectIds: List<String>,
    val type: UrbanLicenseType,
    val status: UrbanLicenseStatus,
    val allowedUserIds: List<String>,
    val allowedDeviceIds: List<String>,
    val allowedModules: List<String>,
    val allowedFeatures: List<String>,
    val issuedAt: Long,
    val expiresAt: Long,
    val gracePeriodDays: Int = 7
)
