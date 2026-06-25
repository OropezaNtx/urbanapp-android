package com.oropeza.urbanapp.core.license

import android.content.Context
import com.oropeza.urbanapp.core.platform.UrbanPlatformSettings
import com.oropeza.urbanapp.core.runtime.UrbanRuntime

object UrbanLicenseManager {

    private val repository = UrbanLicenseRepository()

    fun getLicense(context: Context): UrbanLicense {
        val orgId = UrbanPlatformSettings.getOrganizationId(context)
        val projId = UrbanPlatformSettings.getProjectId(context)
        return repository.getLicense(orgId, projId)
    }

    fun canUseModule(context: Context, moduleId: String): LicenseResult {
        val license = getLicense(context)
        val validationContext = buildValidationContext(context, moduleId = moduleId)
        return UrbanLicenseValidator.validate(license, validationContext)
    }

    fun canUseFeature(context: Context, featureId: String): LicenseResult {
        val license = getLicense(context)
        val validationContext = buildValidationContext(context, featureId = featureId)
        return UrbanLicenseValidator.validate(license, validationContext)
    }

    fun getLicenseStatus(context: Context): UrbanLicenseStatus {
        return getLicense(context).status
    }

    private fun buildValidationContext(
        context: Context,
        moduleId: String? = null,
        featureId: String? = null
    ): LicenseValidationContext {
        val orgId = UrbanPlatformSettings.getOrganizationId(context)
        val projId = UrbanPlatformSettings.getProjectId(context)
        val identity = UrbanRuntime.identity(context)
        
        return LicenseValidationContext(
            organizationId = orgId,
            projectId = projId,
            moduleId = moduleId,
            featureId = featureId,
            deviceId = identity.installationId,
            userId = null // To be implemented when User system is active
        )
    }
}
