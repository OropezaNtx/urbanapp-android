package com.oropeza.urbanapp.core.license

import android.content.Context
import com.oropeza.urbanapp.core.platform.UrbanPlatformSettings

class UrbanLicenseRepository(private val context: Context) {

    fun getCurrentLicense(): UrbanLicense {
        val orgId = UrbanPlatformSettings.getOrganizationId(context)
        val projId = UrbanPlatformSettings.getProjectId(context)
        return getDefaultLicense(orgId, projId)
    }

    fun getDefaultLicense(orgId: String, projId: String): UrbanLicense {
        return UrbanLicense(
            licenseId = "lic_default_pilot",
            organizationId = orgId,
            projectId = projId,
            status = UrbanLicenseStatus.ACTIVE,
            type = UrbanLicenseType.PILOT,
            enabledModules = listOf("ASD"),
            enabledFeatures = listOf("EXPORT", "SYNC", "DIAGNOSTICS"),
            maxUsers = 10,
            maxDevices = 50,
            expirationAt = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000), // 30 days
            gracePeriodDays = 7,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }

    fun canUseModule(module: String): Boolean {
        val license = getCurrentLicense()
        return license.status == UrbanLicenseStatus.ACTIVE && module in license.enabledModules
    }

    fun canUseFeature(feature: String): Boolean {
        val license = getCurrentLicense()
        return license.status == UrbanLicenseStatus.ACTIVE && feature in license.enabledFeatures
    }

    fun licenseStatus(): UrbanLicenseStatus {
        return getCurrentLicense().status
    }
}
