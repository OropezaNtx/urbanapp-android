package com.oropeza.urbanapp.core.license

object UrbanLicenseValidator {

    fun validate(license: UrbanLicense, context: LicenseValidationContext): UrbanLicenseResult {
        // 1. Check status
        if (license.status == UrbanLicenseStatus.SUSPENDED) {
            return UrbanLicenseResult.Denied("License is suspended")
        }

        // 2. Check expiration
        val now = System.currentTimeMillis()
        if (now > license.expirationAt) {
            val gracePeriodEnd = license.expirationAt + (license.gracePeriodDays * 24L * 60 * 60 * 1000)
            if (now > gracePeriodEnd) {
                return UrbanLicenseResult.Denied("License has expired and grace period ended")
            }
        }

        // 3. Check Project
        if (license.projectId != context.projectId) {
            return UrbanLicenseResult.Denied("Project ${context.projectId} is not covered by this license")
        }

        // 4. Check Module
        context.moduleId?.let { mod ->
            if (license.enabledModules.isNotEmpty() && mod !in license.enabledModules) {
                return UrbanLicenseResult.Denied("Module $mod is not allowed by this license")
            }
        }

        // 5. Check Feature
        context.featureId?.let { feat ->
            if (license.enabledFeatures.isNotEmpty() && feat !in license.enabledFeatures) {
                return UrbanLicenseResult.Denied("Feature $feat is not allowed by this license")
            }
        }

        // Note: maxUsers and maxDevices checks will be implemented in future phases 
        // when User/Device registration flows are complete.

        return UrbanLicenseResult.Allowed
    }
}

data class LicenseValidationContext(
    val organizationId: String,
    val projectId: String,
    val moduleId: String? = null,
    val featureId: String? = null,
    val userId: String? = null,
    val deviceId: String? = null
)
