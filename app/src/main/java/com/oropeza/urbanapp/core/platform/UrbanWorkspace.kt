package com.oropeza.urbanapp.core.platform

import com.oropeza.urbanapp.core.config.UrbanConfiguration
import com.oropeza.urbanapp.core.license.UrbanLicense
import com.oropeza.urbanapp.core.auth.UrbanUser
import com.oropeza.urbanapp.core.auth.UrbanRole
import com.oropeza.urbanapp.core.auth.UrbanPermission

data class UrbanWorkspace(
    val organization: UrbanOrganization,
    val project: UrbanProject,
    val environment: String,
    val configuration: UrbanConfiguration,
    val license: UrbanLicense,
    val currentUser: UrbanUser,
    val roles: List<UrbanRole>,
    val permissions: List<UrbanPermission>,
    val modulesEnabled: List<String>,
    val createdAt: Long,
    val updatedAt: Long
)
