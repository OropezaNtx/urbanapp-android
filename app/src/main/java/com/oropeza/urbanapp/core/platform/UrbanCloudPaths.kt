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

    // Backward compatibility (to be removed in next sprint)
    fun installationPath(orgId: String, projectId: String, installationId: String) =
        "${projectPath(orgId, projectId)}/installations/$installationId"
        
    fun heartbeatPath(orgId: String, projectId: String, installationId: String) =
        "${projectPath(orgId, projectId)}/heartbeats/$installationId"
        
    fun tripPath(orgId: String, projectId: String, cloudTripId: String) =
        "${projectPath(orgId, projectId)}/trips/$cloudTripId"
}
