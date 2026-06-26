package com.oropeza.urbanapp.core.auth

import android.content.Context
import com.oropeza.urbanapp.core.platform.UrbanPlatformSettings

class UrbanAccessRepository(private val context: Context) {

    fun getCurrentUser(): UrbanUser {
        val orgId = UrbanPlatformSettings.getOrganizationId(context)
        val projId = UrbanPlatformSettings.getProjectId(context)
        
        // Default local user for now (no login yet)
        return UrbanUser(
            userId = "user_default_1",
            organizationId = orgId,
            projectId = projId,
            name = "Urban Operator",
            email = "operator@urbanapp.com",
            roleIds = listOf("OPERATOR"),
            status = "ACTIVE",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }

    fun getDefaultRoles(): List<UrbanRole> {
        val orgId = UrbanPlatformSettings.getOrganizationId(context)
        return listOf(
            UrbanRole("ADMIN", orgId, "Administrator", "Full system access", 
                getDefaultPermissions().map { it.code }, "ACTIVE", 0L, 0L),
            UrbanRole("SUPERVISOR", orgId, "Supervisor", "Monitor and edit access", 
                listOf("ASD_VIEW", "ASD_EXPORT", "ASD_SYNC", "CATALOG_SYNC", "CLOUD_SYNC", "DIAGNOSTICS_VIEW"), 
                "ACTIVE", 0L, 0L),
            UrbanRole("OPERATOR", orgId, "Operator", "Field operation access", 
                listOf("ASD_CREATE_TRIP", "ASD_EDIT_TRIP", "ASD_CLOSE_TRIP", "ASD_VIEW", "ASD_SYNC", "CATALOG_SYNC", "CLOUD_SYNC"), 
                "ACTIVE", 0L, 0L),
            UrbanRole("VIEWER", orgId, "Viewer", "Read-only access", 
                listOf("ASD_VIEW", "DIAGNOSTICS_VIEW"), 
                "ACTIVE", 0L, 0L)
        )
    }

    fun getDefaultPermissions(): List<UrbanPermission> {
        return listOf(
            UrbanPermission("p1", "ASD_CREATE_TRIP", "Create ASD Trip", null, "ASD", "CREATE", "ACTIVE"),
            UrbanPermission("p2", "ASD_EDIT_TRIP", "Edit ASD Trip", null, "ASD", "EDIT", "ACTIVE"),
            UrbanPermission("p3", "ASD_CLOSE_TRIP", "Close ASD Trip", null, "ASD", "CLOSE", "ACTIVE"),
            UrbanPermission("p4", "ASD_EXPORT", "Export Data", null, "ASD", "EXPORT", "ACTIVE"),
            UrbanPermission("p5", "ASD_SYNC", "Sync ASD", null, "ASD", "SYNC", "ACTIVE"),
            UrbanPermission("p6", "ASD_VIEW", "View ASD", null, "ASD", "VIEW", "ACTIVE"),
            UrbanPermission("p7", "CATALOG_SYNC", "Sync Catalog", null, "CATALOG", "SYNC", "ACTIVE"),
            UrbanPermission("p8", "CLOUD_SYNC", "Manual Cloud Sync", null, "CLOUD", "SYNC", "ACTIVE"),
            UrbanPermission("p9", "DIAGNOSTICS_VIEW", "View Diagnostics", null, "DIAGNOSTICS", "VIEW", "ACTIVE")
        )
    }

    fun getPermissionsForUser(user: UrbanUser): UrbanPermissionSet {
        val roles = getDefaultRoles().filter { it.roleId in user.roleIds }
        val allPermissionIds = roles.flatMap { it.permissionIds }.toSet()
        val permissions = getDefaultPermissions().filter { it.code in allPermissionIds }
        return UrbanPermissionSet(permissions)
    }
}
