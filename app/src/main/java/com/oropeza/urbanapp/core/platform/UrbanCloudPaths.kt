package com.oropeza.urbanapp.core.platform

object UrbanCloudPaths {

    fun organizationPath(orgId: String) = "asd_organizations/$orgId"
    
    fun projectPath(orgId: String, projectId: String) = 
        "${organizationPath(orgId)}/projects/$projectId"
    
    fun installationPath(orgId: String, projectId: String, installationId: String) =
        "${projectPath(orgId, projectId)}/installations/$installationId"
        
    fun heartbeatPath(orgId: String, projectId: String, installationId: String) =
        "${projectPath(orgId, projectId)}/heartbeats/$installationId"
        
    fun tripPath(orgId: String, projectId: String, cloudTripId: String) =
        "${projectPath(orgId, projectId)}/trips/$cloudTripId"
}
