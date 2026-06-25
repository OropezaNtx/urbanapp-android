package com.oropeza.urbanapp.core.platform.cloud

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.oropeza.urbanapp.core.platform.UrbanPlatformSettings
import kotlinx.coroutines.tasks.await

class UrbanPlatformRepository(private val context: Context) {

    private val db = FirebaseFirestore.getInstance()

    fun getActiveOrganizationId(): String {
        return UrbanPlatformSettings.getOrganizationId(context)
    }

    fun getActiveProjectId(): String {
        return UrbanPlatformSettings.getProjectId(context)
    }

    // Note: Authentication and User management will be implemented later.
    // For now, we provide placeholders to obtain "active" elements.
    
    suspend fun getActiveOrganization(): UrbanOrganization? {
        val orgId = getActiveOrganizationId()
        return try {
            val snapshot = db.collection("organizations").document(orgId).get().await()
            val dto = snapshot.toObject(UrbanOrganizationDto::class.java)
            dto?.let { UrbanPlatformCloudMapper.toDomain(it) }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getActiveProject(): UrbanProject? {
        val projId = getActiveProjectId()
        return try {
            val snapshot = db.collection("projects").document(projId).get().await()
            val dto = snapshot.toObject(UrbanProjectDto::class.java)
            dto?.let { UrbanPlatformCloudMapper.toDomain(it) }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getUser(userId: String): UrbanUser? {
        return try {
            val snapshot = db.collection("users").document(userId).get().await()
            val dto = snapshot.toObject(UrbanUserDto::class.java)
            dto?.let { UrbanPlatformCloudMapper.toDomain(it) }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getRole(roleId: String): UrbanRole? {
        return try {
            val snapshot = db.collection("roles").document(roleId).get().await()
            val dto = snapshot.toObject(UrbanRoleDto::class.java)
            dto?.let { UrbanPlatformCloudMapper.toDomain(it) }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getPermission(permissionId: String): UrbanPermission? {
        return try {
            val snapshot = db.collection("permissions").document(permissionId).get().await()
            val dto = snapshot.toObject(UrbanPermissionDto::class.java)
            dto?.let { UrbanPlatformCloudMapper.toDomain(it) }
        } catch (e: Exception) {
            null
        }
    }
}
