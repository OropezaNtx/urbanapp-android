package com.oropeza.urbanapp.core.runtime

import android.content.Context
import com.oropeza.urbanapp.core.platform.sync.UrbanCloudSyncScheduler

object UrbanSyncManager {
    suspend fun syncNow(context: Context): Result<Unit> {
        return UrbanCloudSyncScheduler.syncNow(context)
    }

    fun syncInstallation(context: Context) {
        UrbanCloudSyncScheduler.enqueueAndSyncInstallation(context)
    }

    fun syncHeartbeat(context: Context, activeTripId: Long? = null) {
        UrbanCloudSyncScheduler.enqueueAndSyncHeartbeat(context, activeTripId)
    }
}
