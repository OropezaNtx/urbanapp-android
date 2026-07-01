package com.oropeza.urbanapp.core.platform

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.oropeza.urbanapp.core.identity.UrbanIdentityProvider
import com.oropeza.urbanapp.core.identity.UrbanIdentityManager
import kotlinx.coroutines.tasks.await

class UrbanInstallationManager(private val context: Context) {
    private val db = FirebaseFirestore.getInstance()
    private val prefs = context.getSharedPreferences("urban_installation_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_STATUS = "installation_status"
        private const val KEY_LICENSE_ID = "license_id"
        private const val KEY_WORKSPACE_ID = "workspace_id"
    }

    fun getLocalStatus(): InstallationStatus {
        val statusStr = prefs.getString(KEY_STATUS, InstallationStatus.PENDING.name)
        return try {
            InstallationStatus.valueOf(statusStr ?: InstallationStatus.PENDING.name)
        } catch (e: Exception) {
            InstallationStatus.PENDING
        }
    }

    suspend fun syncInstallationStatus(): InstallationStatus {
        val identity = UrbanIdentityProvider.getIdentity(context)
        val installationId = identity.installationId
        val ownerUid = UrbanIdentityManager.getUid() ?: return InstallationStatus.PENDING
        
        return try {
            // Step 1: Read derived access document (Primary truth for authorization)
            // Keyed by ownerUid to satisfy direct document rules without query params
            val accessRef = db.collection("installation_access").document(ownerUid)
            val accessSnapshot = accessRef.get().await()

            if (accessSnapshot.exists()) {
                val status = accessSnapshot.getString("status") ?: InstallationStatus.PENDING.name
                val licenseId = accessSnapshot.getString("licenseId")
                val workspaceId = accessSnapshot.getString("workspaceId")
                val projectId = accessSnapshot.getString("projectId") ?: "default_project"
                
                val currentStatus = try { 
                    InstallationStatus.valueOf(status) 
                } catch (e: Exception) { 
                    InstallationStatus.PENDING 
                }
                
                saveLocalStatus(currentStatus, licenseId, workspaceId, projectId)
                
                // Step 2: Update telemetry in main installations doc
                db.collection("installations").document(installationId)
                    .update("lastSyncAt", System.currentTimeMillis()).await()

                currentStatus
            } else {
                // If installation_access doesn't exist, check if installation exists
                val instSnapshot = db.collection("installations").document(installationId).get().await()
                if (!instSnapshot.exists()) {
                    registerInstallation(installationId)
                }
                InstallationStatus.PENDING
            }
        } catch (e: Exception) {
            getLocalStatus() // Offline fallback
        }
    }

    private suspend fun registerInstallation(installationId: String) {
        val installation = UrbanPlatformService.buildCurrentInstallation(context).copy(
            ownerUid = UrbanIdentityManager.getUid(),
            workspaceId = null,
            licenseId = null,
            status = InstallationStatus.PENDING.name,
            registeredAt = null,
        )
        val map = UrbanPlatformCloudMapper.installationToMap(installation)
        db.collection("installations").document(installationId).set(map).await()
        saveLocalStatus(InstallationStatus.PENDING, null, null, null)
    }

    private fun saveLocalStatus(status: InstallationStatus, licenseId: String?, workspaceId: String?, projectId: String?) {
        prefs.edit()
            .putString(KEY_STATUS, status.name)
            .putString(KEY_LICENSE_ID, licenseId)
            .putString(KEY_WORKSPACE_ID, workspaceId)
            .apply()
            
        // Also sync with UrbanPlatformSettings if ACTIVE
        if (status == InstallationStatus.ACTIVE && workspaceId != null) {
            UrbanPlatformSettings.saveSettings(context, workspaceId, projectId ?: "default_project", licenseId ?: "")
        }
    }
}
