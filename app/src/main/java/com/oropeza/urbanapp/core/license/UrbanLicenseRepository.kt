package com.oropeza.urbanapp.core.license

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.oropeza.urbanapp.core.identity.UrbanIdentityProvider
import com.oropeza.urbanapp.core.platform.UrbanPlatformSettings
import kotlinx.coroutines.tasks.await

class UrbanLicenseRepository(private val context: Context) {
    private val db = FirebaseFirestore.getInstance()
    private val gson = Gson()
    private val prefs: SharedPreferences? = context.getSharedPreferences("urban_license_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_CACHED_LICENSE = "cached_license"
    }

    suspend fun syncRemoteLicense(): UrbanLicense? {
        val licenseId = UrbanPlatformSettings.getLicenseId(context) ?: return null
        
        return try {
            // Reverted to direct document read. 
            // Security Rules will verify access via the installation_access document.
            val snapshot = db.collection("licenses").document(licenseId).get().await()

            if (snapshot.exists()) {
                val data = snapshot.data ?: return null
                val license = UrbanLicense(
                    licenseId = snapshot.id,
                    workspaceId = data["organizationId"] as? String ?: data["workspaceId"] as? String ?: "",
                    projectId = data["projectId"] as? String ?: "",
                    status = try { 
                        UrbanLicenseStatus.valueOf(data["status"] as? String ?: "UNKNOWN") 
                    } catch (e: Exception) { 
                        UrbanLicenseStatus.UNKNOWN 
                    },
                    type = try { 
                        UrbanLicenseType.valueOf(data["type"] as? String ?: "PILOT") 
                    } catch (e: Exception) { 
                        UrbanLicenseType.PILOT 
                    },
                    enabledModules = (data["enabledModules"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                    enabledFeatures = (data["enabledFeatures"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                    maxUsers = (data["maxUsers"] as? Long)?.toInt() ?: 0,
                    maxDevices = (data["maxDevices"] as? Long)?.toInt() ?: 0,
                    expirationAt = data["expiresAt"] as? Long ?: 0L,
                    gracePeriodDays = (data["gracePeriodDays"] as? Long)?.toInt() ?: 0,
                    createdAt = data["createdAt"] as? Long ?: 0L,
                    updatedAt = data["updatedAt"] as? Long ?: 0L
                )
                
                updateLicenseTelemetry()
                saveLocalLicense(license)
                license
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun updateLicenseTelemetry() {
        try {
            val identity = UrbanIdentityProvider.getIdentity(context)
            db.collection("installations").document(identity.installationId).update(
                "lastLicenseCheckAt", System.currentTimeMillis()
            ).await()
        } catch (e: Exception) {
            // Non-critical
        }
    }

    private fun saveLocalLicense(license: UrbanLicense) {
        val json = gson.toJson(license)
        prefs?.edit()?.putString(KEY_CACHED_LICENSE, json)?.apply()
    }

    fun getCurrentLicense(): UrbanLicense {
        val json = prefs?.getString(KEY_CACHED_LICENSE, null)
        if (json != null) {
            try {
                return gson.fromJson(json, UrbanLicense::class.java)
            } catch (e: Exception) {
                // Ignore and fall back to default
            }
        }
        val workspaceId = UrbanPlatformSettings.getWorkspaceId(context)
        val projId = UrbanPlatformSettings.getProjectId(context)
        return getDefaultLicense(workspaceId, projId)
    }

    fun getDefaultLicense(workspaceId: String, projId: String): UrbanLicense {
        return UrbanLicense(
            licenseId = "lic_default_pilot",
            workspaceId = workspaceId,
            projectId = projId,
            status = UrbanLicenseStatus.ACTIVE,
            type = UrbanLicenseType.PILOT,
            enabledModules = listOf("ASD"),
            enabledFeatures = listOf("EXPORT", "SYNC", "DIAGNOSTICS"),
            maxUsers = 10,
            maxDevices = 50,
            expirationAt = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000), // 30 days
            gracePeriodDays = 7,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }

    fun canUseModule(module: String): Boolean {
        val license = getCurrentLicense()
        return license.status == UrbanLicenseStatus.ACTIVE && module in license.enabledModules
    }

    fun canUseFeature(feature: String): Boolean {
        val license = getCurrentLicense()
        return license.status == UrbanLicenseStatus.ACTIVE && feature in license.enabledFeatures
    }

    fun licenseStatus(): UrbanLicenseStatus {
        return getCurrentLicense().status
    }
}
