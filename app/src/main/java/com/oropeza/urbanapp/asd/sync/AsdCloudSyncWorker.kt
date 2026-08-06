package com.oropeza.urbanapp.asd.sync

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.oropeza.urbanapp.asd.AsdGraph
import com.oropeza.urbanapp.asd.sync.cloud.CloudSyncEngine
import java.util.concurrent.TimeUnit

/**
 * Procesa la cola cloud sin bloquear las operaciones locales de ASD.
 *
 * Room y sync_queue se confirman antes de programar este worker. WorkManager
 * espera conectividad y ejecuta el motor real cuando la red está disponible.
 */
class AsdCloudSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "AsdCloudSyncWorker"
        private const val WORK_NAME = "AsdCloudSyncWork"
        private const val MAX_ITEMS_PER_RUN = 2_000

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<AsdCloudSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30,
                    TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }

    override suspend fun doWork(): ListenableWorker.Result {
        return try {
            AsdGraph.repo.recoverStaleSyncItems()

            val engine = CloudSyncEngine(
                repository = AsdGraph.repo,
                target = AsdGraph.getCloudSyncTarget()
            )

            var totalSynced = 0
            while (totalSynced < MAX_ITEMS_PER_RUN) {
                val syncedInBatch = engine.processNextBatch()
                if (syncedInBatch <= 0) break
                totalSynced += syncedInBatch
            }

            val remainingEligible = AsdGraph.repo.getPendingSyncItems(1).isNotEmpty()
            Log.i(
                TAG,
                "Background sync finished. Synced=$totalSynced, remainingEligible=$remainingEligible"
            )

            when {
                remainingEligible && totalSynced > 0 -> ListenableWorker.Result.retry()
                remainingEligible && runAttemptCount < 5 -> ListenableWorker.Result.retry()
                else -> ListenableWorker.Result.success()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Falló la sincronización en background", e)
            if (runAttemptCount < 5) {
                ListenableWorker.Result.retry()
            } else {
                ListenableWorker.Result.failure()
            }
        }
    }
}
