package com.oropeza.urbanapp.core.license

object UrbanLicenseValidator {

    fun validate(license: UrbanLicense, context: LicenseValidationContext): LicenseResult {
        // 1. Check status
        if (license.status == UrbanLicenseStatus.SUSPENDED) {
            return LicenseResult.Denied("License is suspended")
        }

        // 2. Check expiration
        val now = System.currentTimeMillis()
        if (now > license.expiresAt) {
            val gracePeriodEnd = license.expiresAt + (license.gracePeriodDays * 24L * 60 * 60 * 1000)
            if (now > gracePeriodEnd) {
                return LicenseResult.Denied("License has expired and grace period ended")
            }
        }

        // 3. Check Project
        if (license.projectIds.isNotEmpty() && context.projectId !in license.projectIds) {
            return LicenseResult.Denied("Project ${context.projectId} is not covered by this license")
        }

        // 4. Check Module
        context.moduleId?.let { mod ->
            if (license.allowedModules.isNotEmpty() && mod !in license.allowedModules) {
                return LicenseResult.Denied("Module $mod is not allowed by this license")
            }
        }

        // 5. Check Feature
        context.featureId?.let { feat ->
            if (license.allowedFeatures.isNotEmpty() && feat !in license.allowedFeatures) {
                return LicenseResult.Denied("Feature $feat is not allowed by this license")
            }
        }

        // 6. Check User
        context.userId?.let { user ->
            if (license.allowedUserIds.isNotEmpty() && user !in license.allowedUserIds) {
                return LicenseResult.Denied("User $user is not authorized by this license")
            }
        }

        // 7. Check Device
        context.deviceId?.let { device ->
            if (license.allowedDeviceIds.isNotEmpty() && device !in license.allowedDeviceIds) {
                return LicenseResult.Denied("Device $device is not authorized by this license")
            }
        }

        return LicenseResult.Allowed
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
