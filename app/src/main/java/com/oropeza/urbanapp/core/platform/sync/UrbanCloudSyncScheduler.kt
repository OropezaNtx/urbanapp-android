package com.oropeza.urbanapp.core.platform.sync

import android.content.Context
import android.util.Log
import com.oropeza.urbanapp.asd.AsdGraph
import com.oropeza.urbanapp.asd.sync.cloud.CloudSyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object UrbanCloudSyncScheduler {

    private const val TAG = "UrbanSyncScheduler"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Executes the sync engine to process the queue in background.
     * Returns Result.success(Unit) once the processing attempt is finished.
     */
    suspend fun syncNow(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting manual cloud sync attempt...")
            val engine = CloudSyncEngine(
                repository = AsdGraph.repo,
                target = AsdGraph.getCloudSyncTarget()
            )
            
            var totalSynced = 0
            var lastBatchCount: Int
            do {
                lastBatchCount = engine.processNextBatch()
                totalSynced += lastBatchCount
            } while (lastBatchCount > 0 && totalSynced < 100)
            
            Log.d(TAG, "Cloud sync attempt finished. Items synced: $totalSynced")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Cloud sync attempt failed", e)
            Result.failure(e)
        }
    }

    /**
     * Enqueues the installation technical data and triggers sync in background.
     */
    fun enqueueAndSyncInstallation(context: Context) {
        scope.launch {
            AsdGraph.syncQueue.enqueueInstallationRegister(context)
            syncNow(context)
        }
    }

    /**
     * Enqueues a device heartbeat and triggers sync in background.
     */
    fun enqueueAndSyncHeartbeat(context: Context, activeTripId: Long? = null) {
        scope.launch {
            AsdGraph.syncQueue.enqueueHeartbeat(context, activeTripId)
            syncNow(context)
        }
    }
}
