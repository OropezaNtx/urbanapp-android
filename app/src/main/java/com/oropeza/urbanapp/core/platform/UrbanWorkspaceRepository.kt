package com.oropeza.urbanapp.core.platform

import android.content.Context
import com.oropeza.urbanapp.core.config.UrbanRemoteConfiguration
import com.oropeza.urbanapp.core.license.UrbanLicenseStatus

class UrbanWorkspaceRepository(private val context: Context) {

    fun loadWorkspace(): UrbanWorkspace {
        val orgId = UrbanPlatformSettings.getOrganizationId(context)
        val projId = UrbanPlatformSettings.getProjectId(context)
        val env = UrbanPlatformSettings.getEnvironment(context)
        
        // Initial implementation using SharedPreferences defaults
        return UrbanWorkspace(
            organization = UrbanOrganization(
                organizationId = orgId,
                name = "Organization $orgId",
                status = "ACTIVE",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            ),
            project = UrbanProject(
                projectId = projId,
                organizationId = orgId,
                name = "Project $projId",
                description = null,
                status = "ACTIVE",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            ),
            environment = env,
            configuration = UrbanRemoteConfiguration(),
            licenseStatus = UrbanLicenseStatus.ACTIVE,
            modulesEnabled = listOf("ASD", "FOV", "CC"),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }

    fun saveWorkspace(workspace: UrbanWorkspace) {
        UrbanPlatformSettings.saveSettings(
            context,
            workspace.organization.organizationId,
            workspace.project.projectId,
            workspace.environment
        )
    }

    fun updateWorkspace(workspace: UrbanWorkspace) {
        saveWorkspace(workspace)
    }

    fun resetWorkspace() {
        UrbanPlatformSettings.saveSettings(context, "demo_org", "demo_project", "pilot")
    }
}
