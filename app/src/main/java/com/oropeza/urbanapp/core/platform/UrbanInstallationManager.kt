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
        val docRef = db.collection("installations").document(identity.installationId)
        
        return try {
            val snapshot = docRef.get().await()
            if (snapshot.exists()) {
                val status = snapshot.getString("status") ?: InstallationStatus.PENDING.name
                val licenseId = snapshot.getString("licenseId")
                val workspaceId = snapshot.getString("workspaceId")
                val projectId = snapshot.getString("projectId") ?: "default_project"
                
                // Update local status with fields derived from Firestore
                val currentStatus = try { 
                    InstallationStatus.valueOf(status) 
                } catch (e: Exception) { 
                    InstallationStatus.PENDING 
                }
                
                saveLocalStatus(currentStatus, licenseId, workspaceId, projectId)
                
                // Update lastSyncAt in Firestore (Telemetry only)
                docRef.update("lastSyncAt", System.currentTimeMillis()).await()

                currentStatus
            } else {
                // Not registered yet, register as PENDING
                registerInstallation(identity.installationId)
                InstallationStatus.PENDING
            }
        } catch (e: Exception) {
            getLocalStatus() // Fallback to local if offline
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
