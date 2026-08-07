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
 * Sprint 2.2.3: toda la vida del WorkRequest queda trazada bajo
 * CloudSyncIntegrity para poder distinguir programación, ejecución, drenado,
 * retry y finalización sin depender de dumpsys JobScheduler.
 */
class AsdCloudSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "AsdCloudSyncWorker"
        private const val INTEGRITY_TAG = "CloudSyncIntegrity"
        private const val WORK_NAME = "AsdCloudSyncWork"
        private const val MAX_ITEMS_PER_RUN = 2_000

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<AsdCloudSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag("ASD_CLOUD_SYNC")
                .build()

            Log.i(
                INTEGRITY_TAG,
                "SYNC_WORK_CREATED workId=${request.id} uniqueName=$WORK_NAME " +
                    "requiresNetwork=true policy=KEEP"
            )

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }

    override suspend fun doWork(): ListenableWorker.Result {
        val runId = id.toString()
        val startedAt = System.currentTimeMillis()

        Log.i(
            INTEGRITY_TAG,
            "SYNC_WORK_STARTED runId=$runId attempt=$runAttemptCount"
        )

        return try {
            AsdGraph.init(applicationContext)
            val recoveredStale = AsdGraph.repo.recoverStaleSyncItems()
            if (recoveredStale > 0) {
                Log.w(
                    INTEGRITY_TAG,
                    "SYNC_STALE_RECOVERED runId=$runId count=$recoveredStale"
                )
            }

            TrackingPipelineIntegrityAuditor.logRelevantTrips("BEFORE_CLOUD_DRAIN")

            val engine = CloudSyncEngine(
                repository = AsdGraph.repo,
                target = AsdGraph.getCloudSyncTarget(),
                runId = runId
            )

            var totalSynced = 0
            var batchNumber = 0
            while (totalSynced < MAX_ITEMS_PER_RUN) {
                batchNumber++
                val syncedInBatch = engine.processNextBatch(batchNumber)
                if (syncedInBatch <= 0) break
                totalSynced += syncedInBatch
            }

            TrackingPipelineIntegrityAuditor.logRelevantTrips("AFTER_CLOUD_DRAIN")

            val outstanding = AsdGraph.repo.getOutstandingSyncCount()
            val elapsedMs = System.currentTimeMillis() - startedAt

            if (outstanding > 0) {
                Log.w(
                    INTEGRITY_TAG,
                    "SYNC_FINISHED runId=$runId result=RETRY synced=$totalSynced " +
                        "outstanding=$outstanding batches=$batchNumber elapsedMs=$elapsedMs"
                )
                Log.w(
                    TAG,
                    "Quedan $outstanding elementos por resolver; WorkManager continuará automáticamente con backoff"
                )
                ListenableWorker.Result.retry()
            } else {
                Log.i(INTEGRITY_TAG, "SYNC_QUEUE_EMPTY runId=$runId")
                Log.i(
                    INTEGRITY_TAG,
                    "SYNC_FINISHED runId=$runId result=SUCCESS synced=$totalSynced " +
                        "outstanding=0 batches=$batchNumber elapsedMs=$elapsedMs"
                )
                ListenableWorker.Result.success()
            }
        } catch (e: Exception) {
            val elapsedMs = System.currentTimeMillis() - startedAt
            val result = if (runAttemptCount < 5) "RETRY_EXCEPTION" else "FAILURE"
            Log.e(
                INTEGRITY_TAG,
                "SYNC_FINISHED runId=$runId result=$result attempt=$runAttemptCount " +
                    "elapsedMs=$elapsedMs error=${e.javaClass.simpleName}:${e.message}",
                e
            )
            Log.e(TAG, "Falló la sincronización en background", e)
            if (runAttemptCount < 5) ListenableWorker.Result.retry() else ListenableWorker.Result.failure()
        }
    }
}
