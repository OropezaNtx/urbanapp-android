package com.oropeza.urbanapp.core.platform.cloud

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldPath
import com.oropeza.urbanapp.core.identity.UrbanIdentityProvider
import com.oropeza.urbanapp.core.platform.UrbanPlatformSettings
import kotlinx.coroutines.tasks.await

class UrbanPlatformRepository(private val context: Context) {

    private val db = FirebaseFirestore.getInstance()

    fun getActiveWorkspaceId(): String {
        return UrbanPlatformSettings.getWorkspaceId(context)
    }

    fun getActiveProjectId(): String {
        return UrbanPlatformSettings.getProjectId(context)
    }

    suspend fun getActiveWorkspace(): UrbanOrganization? {
        val workspaceId = getActiveWorkspaceId()
        val installationId = UrbanIdentityProvider.getIdentity(context).installationId
        
        return try {
            // Using a query to pass installationId to security rules
            val querySnapshot = db.collection("organizations")
                .whereEqualTo(FieldPath.documentId(), workspaceId)
                .whereEqualTo("installationId", installationId)
                .limit(1)
                .get()
                .await()
                
            if (!querySnapshot.isEmpty) {
                val dto = querySnapshot.documents[0].toObject(UrbanOrganizationDto::class.java)
                dto?.let { UrbanPlatformCloudMapper.toDomain(it) }
            } else null
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
