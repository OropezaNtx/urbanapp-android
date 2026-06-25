package com.oropeza.urbanapp.core.runtime

import android.content.Context
import com.oropeza.urbanapp.asd.AsdGraph
import com.oropeza.urbanapp.core.identity.UrbanDeviceIdentity
import com.oropeza.urbanapp.core.platform.UrbanCloudPaths
import com.oropeza.urbanapp.core.platform.UrbanPlatformSettings
import kotlinx.coroutines.flow.firstOrNull

object UrbanRuntime {

    fun identity(context: Context): UrbanDeviceIdentity {
        return UrbanIdentityManager.getIdentity(context)
    }

    fun getShortInstallationId(context: Context): String {
        return UrbanIdentityManager.getShortInstallationId(context)
    }

    fun platformSettings(context: Context): UrbanPlatformSettings {
        return UrbanPlatformSettings
    }

    fun cloudPaths(): UrbanCloudPaths {
        return UrbanPlatformManager.getCloudPaths()
    }

    suspend fun syncNow(context: Context): Result<Unit> {
        return UrbanSyncManager.syncNow(context)
    }

    fun syncInstallation(context: Context) {
        UrbanSyncManager.syncInstallation(context)
    }

    fun syncHeartbeat(context: Context, activeTripId: Long? = null) {
        UrbanSyncManager.syncHeartbeat(context, activeTripId)
    }

    suspend fun getRuntimeStatus(context: Context): UrbanRuntimeStatus {
        val identity = identity(context)
        val orgId = UrbanPlatformManager.getOrganizationId(context)
        val projId = UrbanPlatformManager.getProjectId(context)
        val env = UrbanPlatformManager.getEnvironment(context)
        
        val pendingCount = AsdGraph.repo.syncQueuePendingCountFlow().firstOrNull()
        val failedCount = AsdGraph.repo.syncQueueFailedCountFlow().firstOrNull()
        val lastSyncAt = AsdGraph.repo.lastSyncTimeFlow().firstOrNull()

        return UrbanRuntimeStatus(
            installationId = identity.installationId,
            shortInstallationId = getShortInstallationId(context),
            organizationId = orgId,
            projectId = projId,
            environment = env,
            appVersionName = identity.appVersionName,
            appVersionCode = identity.appVersionCode,
            androidModel = identity.model,
            pendingSyncCount = pendingCount,
            failedSyncCount = failedCount,
            lastSyncAt = lastSyncAt,
            status = "READY"
        )
    }
}
