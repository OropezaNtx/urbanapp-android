package com.oropeza.urbanapp.core.platform

object UrbanCloudPaths {

    fun workspacePath(workspaceId: String) = "asd_organizations/$workspaceId"
    
    fun projectPath(workspaceId: String, projectId: String) = 
        "${workspacePath(workspaceId)}/projects/$projectId"

    fun workspacePath(workspace: UrbanWorkspace) = 
        workspacePath(workspace.organization.workspaceId)

    fun projectPath(workspace: UrbanWorkspace) = 
        projectPath(workspace.organization.workspaceId, workspace.project.projectId)
    
    fun installationPath(workspace: UrbanWorkspace, installationId: String) =
        "${projectPath(workspace)}/installations/$installationId"
        
    /**
     * Live presence is an overwrite-only operational document consumed by the
     * Operations Center. Keep it separate from the durable installation record.
     */
    fun heartbeatPath(workspace: UrbanWorkspace, installationId: String) =
        "${projectPath(workspace)}/live_status/$installationId"
        
    fun tripPath(workspace: UrbanWorkspace, cloudTripId: String) =
        "${projectPath(workspace)}/trips/$cloudTripId"

    fun tripEventPath(workspace: UrbanWorkspace, cloudTripId: String, cloudEventId: String) =
        "${tripPath(workspace, cloudTripId)}/events/$cloudEventId"

    fun trackChunkPath(workspace: UrbanWorkspace, cloudTripId: String, chunkId: String) =
        "${tripPath(workspace, cloudTripId)}/track_chunks/$chunkId"

    fun tripTelemetryPath(workspace: UrbanWorkspace, cloudTripId: String) =
        "${tripPath(workspace, cloudTripId)}/telemetry/summary"

    /**
     * Project-scoped ASD catalog. Catalog data belongs to the same operational
     * project as trips/installations and is read-only for field devices.
     *
     * Document shape:
     *   catalog_versions/current
     *     /routes
     *     /people
     *     /vehicle_types
     */
    fun catalogVersionPath(workspaceId: String, projectId: String) =
        "${projectPath(workspaceId, projectId)}/catalog_versions/current"

    fun catalogVersionPath(workspace: UrbanWorkspace) =
        catalogVersionPath(workspace.organization.workspaceId, workspace.project.projectId)

    fun configurationPath(workspaceId: String, projectId: String) =
        "asd_organizations/$workspaceId/projects/$projectId/configuration/current"

    fun licensePath(workspaceId: String, licenseId: String) = 
        "asd_organizations/$workspaceId/licenses/$licenseId"

    fun projectLicensePath(workspaceId: String, projectId: String, licenseId: String) =
        "asd_organizations/$workspaceId/projects/$projectId/licenses/$licenseId"

    fun userPath(workspaceId: String, userId: String) = 
        "asd_organizations/$workspaceId/users/$userId"

    fun rolePath(workspaceId: String, roleId: String) =
        "asd_organizations/$workspaceId/roles/$roleId"

    fun permissionPath(workspaceId: String, permissionId: String) =
        "asd_organizations/$workspaceId/permissions/$permissionId"

    fun projectUserPath(workspaceId: String, projectId: String, userId: String) =
        "asd_organizations/$workspaceId/projects/$projectId/users/$userId"

    fun installationPath(workspaceId: String, projectId: String, installationId: String) =
        "${projectPath(workspaceId, projectId)}/installations/$installationId"
        
    fun heartbeatPath(workspaceId: String, projectId: String, installationId: String) =
        "${projectPath(workspaceId, projectId)}/live_status/$installationId"
        
    fun tripPath(workspaceId: String, projectId: String, cloudTripId: String) =
        "${projectPath(workspaceId, projectId)}/trips/$cloudTripId"
}
