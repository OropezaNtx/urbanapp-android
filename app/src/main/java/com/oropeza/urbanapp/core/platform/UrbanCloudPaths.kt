package com.oropeza.urbanapp.core.platform

object UrbanCloudPaths {

    fun organizationPath(orgId: String) = "asd_organizations/$orgId"
    
    fun projectPath(orgId: String, projectId: String) = 
        "${organizationPath(orgId)}/projects/$projectId"

    fun organizationPath(workspace: UrbanWorkspace) = 
        organizationPath(workspace.organization.organizationId)

    fun projectPath(workspace: UrbanWorkspace) = 
        projectPath(workspace.organization.organizationId, workspace.project.projectId)
    
    fun installationPath(workspace: UrbanWorkspace, installationId: String) =
        "${projectPath(workspace)}/installations/$installationId"
        
    fun heartbeatPath(workspace: UrbanWorkspace, installationId: String) =
        "${projectPath(workspace)}/heartbeats/$installationId"
        
    fun tripPath(workspace: UrbanWorkspace, cloudTripId: String) =
        "${projectPath(workspace)}/trips/$cloudTripId"

    fun configurationPath(orgId: String, projectId: String) =
        "asd_organizations/$orgId/projects/$projectId/configuration/current"

    fun licensePath(orgId: String, licenseId: String) = 
        "asd_organizations/$orgId/licenses/$licenseId"

    fun projectLicensePath(orgId: String, projectId: String, licenseId: String) =
        "asd_organizations/$orgId/projects/$projectId/licenses/$licenseId"

    fun userPath(orgId: String, userId: String) = 
        "asd_organizations/$orgId/users/$userId"

    fun rolePath(orgId: String, roleId: String) = 
        "asd_organizations/$orgId/roles/$roleId"

    fun permissionPath(orgId: String, permissionId: String) = 
        "asd_organizations/$orgId/permissions/$permissionId"

    fun projectUserPath(orgId: String, projectId: String, userId: String) =
        "asd_organizations/$orgId/projects/$projectId/users/$userId"

    // Backward compatibility (to be removed in next sprint)
    fun installationPath(orgId: String, projectId: String, installationId: String) =
        "${projectPath(orgId, projectId)}/installations/$installationId"
        
    fun heartbeatPath(orgId: String, projectId: String, installationId: String) =
        "${projectPath(orgId, projectId)}/heartbeats/$installationId"
        
    fun tripPath(orgId: String, projectId: String, cloudTripId: String) =
        "${projectPath(orgId, projectId)}/trips/$cloudTripId"
}
