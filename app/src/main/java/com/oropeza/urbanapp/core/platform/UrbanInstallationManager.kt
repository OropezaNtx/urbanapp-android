package com.oropeza.urbanapp.core.platform

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.oropeza.urbanapp.core.identity.UrbanIdentityManager
import com.oropeza.urbanapp.core.identity.UrbanIdentityProvider
import kotlinx.coroutines.tasks.await

class UrbanInstallationManager(private val context: Context) {
    private val db = FirebaseFirestore.getInstance()
    private val prefs = context.getSharedPreferences("urban_installation_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_STATUS = "installation_status"
        private const val KEY_LICENSE_ID = "license_id"
        private const val KEY_WORKSPACE_ID = "workspace_id"
        private const val KEY_PROJECT_ID = "project_id"
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
        val workspaceId = UrbanPlatformSettings.getWorkspaceId(context)
            .ifBlank { UrbanPlatformSettings.DEFAULT_ORGANIZATION_ID }
        val projectId = UrbanPlatformSettings.getProjectId(context)
            .ifBlank { UrbanPlatformSettings.DEFAULT_PROJECT_ID }
        val path = UrbanCloudPaths.installationPath(workspaceId, projectId, identity.installationId)

        return try {
            val ref = db.document(path)
            val snapshot = ref.get().await()

            if (!snapshot.exists()) {
                registerInstallation(path)
                return InstallationStatus.PENDING
            }

            val statusName = snapshot.getString("status") ?: InstallationStatus.PENDING.name
            val status = try {
                InstallationStatus.valueOf(statusName)
            } catch (e: Exception) {
                InstallationStatus.PENDING
            }
            val licenseId = snapshot.getString("licenseId")
            val resolvedWorkspaceId = snapshot.getString("workspaceId") ?: workspaceId
            val resolvedProjectId = snapshot.getString("projectId") ?: projectId

            saveLocalStatus(status, licenseId, resolvedWorkspaceId, resolvedProjectId)

            // Presence/last-sync fields are informational. Merge avoids clobbering
            // license or administrative fields written by Operations Center.
            ref.set(
                mapOf(
                    "lastSyncAt" to System.currentTimeMillis(),
                    "updatedAt" to System.currentTimeMillis(),
                    "ownerUid" to UrbanIdentityManager.getUid()
                ),
                SetOptions.merge()
            ).await()

            status
        } catch (e: Exception) {
            getLocalStatus()
        }
    }

    private suspend fun registerInstallation(path: String) {
        val installation = UrbanPlatformService.buildCurrentInstallation(context).copy(
            ownerUid = UrbanIdentityManager.getUid(),
            status = InstallationStatus.PENDING.name,
            registeredAt = System.currentTimeMillis(),
        )
        db.document(path)
            .set(UrbanPlatformCloudMapper.installationToMap(installation), SetOptions.merge())
            .await()
        saveLocalStatus(
            InstallationStatus.PENDING,
            installation.licenseId,
            installation.workspaceId,
            installation.projectId
        )
    }

    private fun saveLocalStatus(status: InstallationStatus, licenseId: String?, workspaceId: String?, projectId: String?) {
        prefs.edit()
            .putString(KEY_STATUS, status.name)
            .putString(KEY_LICENSE_ID, licenseId)
            .putString(KEY_WORKSPACE_ID, workspaceId)
            .putString(KEY_PROJECT_ID, projectId)
            .apply()

        if (status == InstallationStatus.ACTIVE && !workspaceId.isNullOrBlank()) {
            UrbanPlatformSettings.saveSettings(
                context,
                workspaceId,
                projectId ?: UrbanPlatformSettings.DEFAULT_PROJECT_ID,
                licenseId ?: ""
            )
        }
    }
}
