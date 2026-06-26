package com.oropeza.urbanapp.core.platform

import com.oropeza.urbanapp.core.config.UrbanRemoteConfiguration
import com.oropeza.urbanapp.core.license.UrbanLicenseStatus

data class UrbanWorkspace(
    val organization: UrbanOrganization,
    val project: UrbanProject,
    val environment: String,
    val configuration: UrbanRemoteConfiguration,
    val licenseStatus: UrbanLicenseStatus,
    val modulesEnabled: List<String>,
    val createdAt: Long,
    val updatedAt: Long,
    
    // Future placeholders
    val users: List<String> = emptyList(),
    val roles: List<String> = emptyList(),
    val permissions: List<String> = emptyList()
)
