package com.oropeza.urbanapp.core.license

import android.content.Context
import com.oropeza.urbanapp.core.runtime.UrbanRuntime

object UrbanLicenseManager {

    private val repository = UrbanLicenseRepository()

    fun getLicense(context: Context): UrbanLicense {
        val workspace = UrbanRuntime.workspace(context)
        return repository.getLicense(workspace.organization.organizationId, workspace.project.projectId)
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
        return UrbanRuntime.workspace(context).licenseStatus
    }

    private fun buildValidationContext(
        context: Context,
        moduleId: String? = null,
        featureId: String? = null
    ): LicenseValidationContext {
        val workspace = UrbanRuntime.workspace(context)
        val identity = UrbanRuntime.identity(context)
        
        return LicenseValidationContext(
            organizationId = workspace.organization.organizationId,
            projectId = workspace.project.projectId,
            moduleId = moduleId,
            featureId = featureId,
            deviceId = identity.installationId,
            userId = null // To be implemented when User system is active
        )
    }
}
