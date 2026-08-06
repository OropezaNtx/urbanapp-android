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
     * Ejecuta la cola en el proceso actual y espera el resultado.
     *
     * Solo debe usarse para una acción manual explícita o diagnóstico; nunca
     * desde una operación de captura de campo.
     */
    suspend fun syncNowBlocking(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting manual cloud sync attempt...")
            UrbanRuntime.publishEvent(UrbanEventFactory.sync(UrbanEventTypes.SYNC_REQUESTED))

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
            UrbanRuntime.publishEvent(
                UrbanEventFactory.sync(
                    UrbanEventTypes.SYNC_COMPLETED,
                    mapOf("syncedCount" to totalSynced)
                )
            )
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
