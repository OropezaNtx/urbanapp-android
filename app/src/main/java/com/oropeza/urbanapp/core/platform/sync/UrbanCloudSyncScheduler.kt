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
    private const val MAX_MANUAL_SYNC_ITEMS = 5_000
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Programa la sincronización y regresa de inmediato.
     *
     * Este método es seguro para flujos operativos Offline First: crear, guardar
     * y finalizar nunca esperan a Firebase ni a la disponibilidad de red.
     */
    suspend fun syncNow(context: Context): Result<Unit> {
        return try {
            UrbanRuntime.publishEvent(UrbanEventFactory.sync(UrbanEventTypes.SYNC_REQUESTED))
            AsdCloudSyncWorker.enqueue(context.applicationContext)
            Result.success(Unit)
        } catch (e: Exception) {
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
     *
     * Se detiene cuando la cola queda vacía, cuando ningún elemento del lote pudo
     * avanzar o al alcanzar el límite de seguridad. Los elementos con error se
     * conservan con su mensaje real y no impiden sincronizar los demás.
     */
    suspend fun syncNowBlocking(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting full manual cloud sync attempt...")
            UrbanRuntime.publishEvent(UrbanEventFactory.sync(UrbanEventTypes.SYNC_REQUESTED))

            AsdGraph.repo.recoverStaleSyncItems()

            val engine = CloudSyncEngine(
                repository = AsdGraph.repo,
                target = AsdGraph.getCloudSyncTarget()
            )

            var totalSynced = 0
            while (totalSynced < MAX_MANUAL_SYNC_ITEMS) {
                val syncedInBatch = engine.processNextBatch()
                if (syncedInBatch <= 0) break
                totalSynced += syncedInBatch
            }

            val remainingEligible = AsdGraph.repo.getPendingSyncItems(1).isNotEmpty()
            Log.d(
                TAG,
                "Full manual sync finished. Synced=$totalSynced, remainingEligible=$remainingEligible"
            )

            UrbanRuntime.publishEvent(
                UrbanEventFactory.sync(
                    UrbanEventTypes.SYNC_COMPLETED,
                    mapOf(
                        "syncedCount" to totalSynced,
                        "remainingEligible" to remainingEligible
                    )
                )
            )

            // Si quedan elementos elegibles por haber alcanzado el límite de
            // seguridad, WorkManager continuará sin bloquear la interfaz.
            if (remainingEligible) AsdCloudSyncWorker.enqueue(context.applicationContext)

            Result.success(Unit)
        } catch (e: Exception) {
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

    fun enqueueAndSyncInstallation(context: Context) {
        scope.launch {
            AsdGraph.syncQueue.enqueueInstallationRegister(context)
            syncNow(context)
        }
    }

    fun enqueueAndSyncHeartbeat(context: Context, activeTripId: Long? = null) {
        scope.launch {
            AsdGraph.syncQueue.enqueueHeartbeat(context, activeTripId)
            syncNow(context)
        }
    }
}
