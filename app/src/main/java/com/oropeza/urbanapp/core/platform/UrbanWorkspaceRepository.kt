package com.oropeza.urbanapp.core.platform

import android.content.Context
import com.oropeza.urbanapp.core.auth.UrbanAccessRepository
import com.oropeza.urbanapp.core.license.UrbanLicenseRepository
import com.oropeza.urbanapp.core.config.UrbanConfigurationManager

class UrbanWorkspaceRepository(private val context: Context) {

    fun loadWorkspace(): UrbanWorkspace {
        val workspaceId = UrbanPlatformSettings.getWorkspaceId(context)
            .ifBlank { UrbanPlatformSettings.DEFAULT_ORGANIZATION_ID }
        val projId = UrbanPlatformSettings.getProjectId(context)
            .ifBlank { UrbanPlatformSettings.DEFAULT_PROJECT_ID }
        val env = UrbanPlatformSettings.getEnvironment(context)
        
        val accessRepo = UrbanAccessRepository(context)
        val licenseRepo = UrbanLicenseRepository(context)
        
        // Initial implementation using standardized workspaceId
        return UrbanWorkspace(
            organization = UrbanOrganization(
                workspaceId = workspaceId,
                name = "Workspace $workspaceId",
                status = "ACTIVE",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            ),
            project = UrbanProject(
                projectId = projId,
                workspaceId = workspaceId,
                name = "Project $projId",
                description = null,
                status = "ACTIVE",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            ),
            environment = env,
            configuration = UrbanConfigurationManager.configuration(context),
            license = licenseRepo.getCurrentLicense(),
            currentUser = accessRepo.getCurrentUser(),
            roles = accessRepo.getDefaultRoles(),
            permissions = accessRepo.getDefaultPermissions(),
            modulesEnabled = listOf("ASD", "DIAGNOSTICS"),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }

    fun saveWorkspace(workspace: UrbanWorkspace) {
        UrbanPlatformSettings.saveSettings(
            context,
            workspace.organization.workspaceId,
            workspace.project.projectId,
            workspace.license.licenseId,
            workspace.environment
        )
    }

    fun updateWorkspace(workspace: UrbanWorkspace) {
        saveWorkspace(workspace)
    }

    fun resetWorkspace() {
        UrbanPlatformSettings.saveSettings(context, "", "", "", "pilot")
    }
}
