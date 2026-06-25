package com.oropeza.urbanapp.core.license

class UrbanLicenseRepository {

    /**
     * Fetches the license for the given organization and project.
     * For now, returns a default active professional license.
     * Prepared for future Firestore integration.
     */
    fun getLicense(organizationId: String, projectId: String): UrbanLicense {
        return UrbanLicense(
            licenseId = "lic_default_prof",
            organizationId = organizationId,
            projectIds = listOf(projectId),
            type = UrbanLicenseType.PROFESSIONAL,
            status = UrbanLicenseStatus.ACTIVE,
            allowedUserIds = emptyList(), // Empty means all for now or handled by platform
            allowedDeviceIds = emptyList(),
            allowedModules = listOf("ASD", "FOV", "CC", "DASHBOARD"),
            allowedFeatures = listOf("OFFLINE_SYNC", "REMOTE_CONFIG"),
            issuedAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + (365L * 24 * 60 * 60 * 1000), // 1 year
            gracePeriodDays = 7
        )
    }
}
