package com.oropeza.urbanapp.core.platform

import android.content.Context
import android.os.BatteryManager
import com.oropeza.urbanapp.core.identity.UrbanIdentityProvider
import com.oropeza.urbanapp.core.runtime.UrbanRuntime
import kotlinx.coroutines.flow.firstOrNull

object UrbanPlatformService {

    fun buildCurrentInstallation(context: Context): UrbanInstallation {
        val identity = UrbanIdentityProvider.getIdentity(context)
        val orgId = UrbanPlatformSettings.getOrganizationId(context)
        val projectId = UrbanPlatformSettings.getProjectId(context)
        val licenseId = UrbanPlatformSettings.getLicenseId(context)

        return UrbanInstallation(
            installationId = identity.installationId,
            workspaceId = orgId,
            projectId = projectId,
            licenseId = licenseId,
            androidId = identity.androidId,
            manufacturer = identity.manufacturer,
            model = identity.model,
            androidVersion = identity.androidVersion,
            sdkInt = identity.sdkInt,
            appVersionName = identity.appVersionName,
            appVersionCode = identity.appVersionCode,
            packageName = identity.packageName,
            registeredAt = identity.createdAt,
            lastSeenAt = System.currentTimeMillis(),
            lastLicenseCheckAt = null,
            lastHeartbeatAt = System.currentTimeMillis(),
            lastSyncAt = null,
            installationVersion = identity.appVersionName,
            status = "ACTIVE",
            createdAt = identity.createdAt,
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun buildHeartbeat(context: Context, activeTripId: String? = null): UrbanHeartbeat {
        val identity = UrbanIdentityProvider.getIdentity(context)
        val orgId = UrbanPlatformSettings.getOrganizationId(context)
        val projectId = UrbanPlatformSettings.getProjectId(context)
        
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val battery = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        
        val pendingCount = UrbanRuntime.syncStatus().pendingSyncCountFlow().firstOrNull() ?: 0
        val failedCount = UrbanRuntime.syncStatus().failedSyncCountFlow().firstOrNull() ?: 0
        
        return UrbanHeartbeat(
            installationId = identity.installationId,
            workspaceId = orgId,
            projectId = projectId,
            activeTripId = activeTripId,
            gpsStatus = "UNKNOWN", // To be filled by caller if needed
            batteryLevel = battery,
            pendingSyncCount = pendingCount,
            failedSyncCount = failedCount,
            appVersionName = identity.appVersionName,
            appVersionCode = identity.appVersionCode,
            lastLat = null,
            lastLon = null,
            lastFixTime = null,
            status = "ONLINE",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }
}
