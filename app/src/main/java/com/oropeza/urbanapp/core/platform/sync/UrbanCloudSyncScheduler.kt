package com.oropeza.urbanapp.core.platform.sync

import android.content.Context
import android.util.Log
import com.oropeza.urbanapp.asd.AsdGraph
import com.oropeza.urbanapp.asd.sync.AsdCloudSyncWorker
import com.oropeza.urbanapp.asd.sync.cloud.CloudSyncEngine
import com.oropeza.urbanapp.core.events.UrbanEventFactory
import com.oropeza.urbanapp.core.events.UrbanEventTypes
import com.oropeza.urbanapp.core.runtime.UrbanRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object UrbanCloudSyncScheduler {

    private const val TAG = "UrbanSyncScheduler"
    private const val INTEGRITY_TAG = "CloudSyncIntegrity"
    private const val MAX_MANUAL_SYNC_ITEMS = 5_000
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Programa la sincronización y regresa de inmediato.
     *
     * SYNC_ENQUEUED significa que el flujo local solicitó drenar sync_queue. La
     * existencia real de filas queda demostrada posteriormente por SYNC_BATCH_READ
     * y SYNC_ITEM_STARTED, ambos con queueId.
     */
    suspend fun syncNow(context: Context): Result<Unit> {
        return try {
            Log.i(INTEGRITY_TAG, "SYNC_ENQUEUED source=ASYNC_SCHEDULER")
            UrbanRuntime.publishEvent(UrbanEventFactory.sync(UrbanEventTypes.SYNC_REQUESTED))
            AsdCloudSyncWorker.enqueue(context.applicationContext)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(
                INTEGRITY_TAG,
                "SYNC_SCHEDULE_FAILED source=ASYNC_SCHEDULER error=${e.javaClass.simpleName}:${e.message}",
                e
            )
            Log.e(TAG, "No se pudo programar la sincronización", e)
            UrbanRuntime.publishEvent(
                UrbanEventFactory.error(
                    UrbanEventTypes.SYNC_FAILED,
                    "SYNC_SCHEDULER",
                    "SCHEDULE",
                    mapOf("error" to e.message)
                )
            )
            Result.failure(e)
        }
    }

    /**
     * Drena todos los elementos elegibles de la cola en una sola acción manual.
     */
    suspend fun syncNowBlocking(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        val runId = "MANUAL_${System.currentTimeMillis()}"
        val startedAt = System.currentTimeMillis()

        try {
            Log.i(INTEGRITY_TAG, "SYNC_ENQUEUED source=MANUAL_BLOCKING runId=$runId")
            Log.i(INTEGRITY_TAG, "SYNC_WORK_STARTED runId=$runId attempt=0 source=MANUAL_BLOCKING")
            UrbanRuntime.publishEvent(UrbanEventFactory.sync(UrbanEventTypes.SYNC_REQUESTED))

            val recovered = AsdGraph.repo.recoverStaleSyncItems()
            if (recovered > 0) {
                Log.w(INTEGRITY_TAG, "SYNC_STALE_RECOVERED runId=$runId count=$recovered")
            }

            val engine = CloudSyncEngine(
                repository = AsdGraph.repo,
                target = AsdGraph.getCloudSyncTarget(),
                runId = runId
            )

            var totalSynced = 0
            var batchNumber = 0
            while (totalSynced < MAX_MANUAL_SYNC_ITEMS) {
                batchNumber++
                val syncedInBatch = engine.processNextBatch(batchNumber)
                if (syncedInBatch <= 0) break
                totalSynced += syncedInBatch
            }

            val remainingEligible = AsdGraph.repo.getPendingSyncItems(1).isNotEmpty()
            val elapsedMs = System.currentTimeMillis() - startedAt

            UrbanRuntime.publishEvent(
                UrbanEventFactory.sync(
                    UrbanEventTypes.SYNC_COMPLETED,
                    mapOf(
                        "syncedCount" to totalSynced,
                        "remainingEligible" to remainingEligible
                    )
                )
            )

            if (remainingEligible) {
                Log.w(
                    INTEGRITY_TAG,
                    "SYNC_FINISHED runId=$runId result=PARTIAL synced=$totalSynced " +
                        "remainingEligible=true batches=$batchNumber elapsedMs=$elapsedMs"
                )
                AsdCloudSyncWorker.enqueue(context.applicationContext)
            } else {
                Log.i(INTEGRITY_TAG, "SYNC_QUEUE_EMPTY runId=$runId")
                Log.i(
                    INTEGRITY_TAG,
                    "SYNC_FINISHED runId=$runId result=SUCCESS synced=$totalSynced " +
                        "remainingEligible=false batches=$batchNumber elapsedMs=$elapsedMs"
                )
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(
                INTEGRITY_TAG,
                "SYNC_FINISHED runId=$runId result=FAILURE source=MANUAL_BLOCKING " +
                    "elapsedMs=${System.currentTimeMillis() - startedAt} " +
                    "error=${e.javaClass.simpleName}:${e.message}",
                e
            )
            Log.e(TAG, "Cloud sync attempt failed", e)
            UrbanRuntime.publishEvent(
                UrbanEventFactory.error(
                    UrbanEventTypes.SYNC_FAILED,
                    "SYNC_SCHEDULER",
                    "SYNC",
                    mapOf("error" to e.message)
                )
            )
            Result.failure(e)
        }
    }

    /**
     * enqueueInstallationRegister/enqueueHeartbeat already schedule sync when the
     * queue row is persisted. Requesting sync again here only amplifies WorkManager
     * wakeups and can produce duplicate presence writes during bootstrap races.
     */
    fun enqueueAndSyncInstallation(context: Context) {
        scope.launch {
            AsdGraph.syncQueue.enqueueInstallationRegister(context)
        }
    }

    fun enqueueAndSyncHeartbeat(context: Context, activeTripId: Long? = null) {
        scope.launch {
            AsdGraph.syncQueue.enqueueHeartbeat(context, activeTripId)
        }
    }
}
