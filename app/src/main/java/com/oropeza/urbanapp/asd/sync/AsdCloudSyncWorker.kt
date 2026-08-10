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
import com.oropeza.urbanapp.asd.telemetry.TripTelemetryRecorder
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class AsdCloudSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "AsdCloudSyncWorker"
        private const val INTEGRITY_TAG = "CloudSyncIntegrity"
        private const val WORK_NAME = "AsdCloudSyncWorkV2"
        private const val LEGACY_WORK_NAME = "AsdCloudSyncWork"
        private const val MAX_ITEMS_PER_RUN = 2_000
        private val DIRECT_EXECUTOR = Executor { command -> command.run() }

        fun enqueue(context: Context, cancelDeferredRetry: Boolean = true) {
            val appContext = context.applicationContext
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val request = OneTimeWorkRequestBuilder<AsdCloudSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag("ASD_CLOUD_SYNC_V2")
                .build()
            val workManager = WorkManager.getInstance(appContext)
            workManager.cancelUniqueWork(LEGACY_WORK_NAME)
            if (cancelDeferredRetry) AsdCloudSyncRetryKickWorker.cancel(appContext)
            Log.i(INTEGRITY_TAG, "SYNC_WORK_REQUESTED requestedWorkId=${request.id} uniqueName=$WORK_NAME requiresNetwork=true policy=KEEP")
            val operation = workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
            operation.result.addListener(
                {
                    try {
                        operation.result.get()
                        Log.i(INTEGRITY_TAG, "SYNC_WORK_ENQUEUE_COMPLETED requestedWorkId=${request.id} uniqueName=$WORK_NAME")
                        logUniqueWorkState(workManager, request.id.toString())
                    } catch (e: Exception) {
                        Log.e(INTEGRITY_TAG, "SYNC_WORK_ENQUEUE_FAILED requestedWorkId=${request.id} error=${e.javaClass.simpleName}:${e.message}", e)
                    }
                }, DIRECT_EXECUTOR
            )
        }

        private fun logUniqueWorkState(workManager: WorkManager, requestedWorkId: String) {
            val future = workManager.getWorkInfosForUniqueWork(WORK_NAME)
            future.addListener(
                {
                    try {
                        val infos = future.get()
                        if (infos.isEmpty()) {
                            Log.w(INTEGRITY_TAG, "SYNC_UNIQUE_WORK_STATE requestedWorkId=$requestedWorkId actualWorkId=NONE state=NONE runAttemptCount=0 requestedAccepted=false")
                            return@addListener
                        }
                        val active = infos.firstOrNull { !it.state.isFinished } ?: infos.lastOrNull()
                        infos.forEach { info ->
                            Log.i(INTEGRITY_TAG, "SYNC_UNIQUE_WORK_STATE requestedWorkId=$requestedWorkId actualWorkId=${info.id} state=${info.state} runAttemptCount=${info.runAttemptCount} requestedAccepted=${info.id.toString() == requestedWorkId} selectedActive=${active?.id == info.id}")
                        }
                    } catch (e: Exception) {
                        Log.e(INTEGRITY_TAG, "SYNC_UNIQUE_WORK_STATE_FAILED requestedWorkId=$requestedWorkId error=${e.javaClass.simpleName}:${e.message}", e)
                    }
                }, DIRECT_EXECUTOR
            )
        }
    }

    override suspend fun doWork(): ListenableWorker.Result {
        val runId = id.toString()
        val startedAt = System.currentTimeMillis()
        Log.i(INTEGRITY_TAG, "SYNC_WORK_STARTED runId=$runId attempt=$runAttemptCount")

        return try {
            AsdGraph.init(applicationContext)
            val sanitizedRows = SyncQueueStateSanitizer.sanitize()
            if (sanitizedRows > 0) Log.i(INTEGRITY_TAG, "SYNC_QUEUE_SANITIZED runId=$runId rows=$sanitizedRows")

            val recoveredStale = AsdGraph.repo.recoverStaleSyncItems()
            if (recoveredStale > 0) Log.w(INTEGRITY_TAG, "SYNC_STALE_RECOVERED runId=$runId count=$recoveredStale")

            val chunkReconciliation = TrackChunkQueueReconciler.reconcileRecentClosedTrips()
            Log.i(
                INTEGRITY_TAG,
                "SYNC_TRACK_RECONCILIATION runId=$runId phase=PRE_DRAIN scannedTrips=${chunkReconciliation.scannedTrips} " +
                    "expectedChunks=${chunkReconciliation.expectedChunks} requeued=${chunkReconciliation.requeuedChunks} " +
                    "missing=${chunkReconciliation.missingChunks} changed=${chunkReconciliation.changedChunks}"
            )

            val legacyMigration = LegacyCloudPathMigrator.migrateIfNeeded(applicationContext)
            Log.i(INTEGRITY_TAG, "SYNC_LEGACY_PATH_MIGRATION runId=$runId eligible=${legacyMigration.eligible} migrated=${legacyMigration.migrated} skipped=${legacyMigration.skipped} reason=${legacyMigration.reason ?: "NONE"}")

            TrackingPipelineIntegrityAuditor.logRelevantTrips("BEFORE_CLOUD_DRAIN")
            val engine = CloudSyncEngine(repository = AsdGraph.repo, target = AsdGraph.getCloudSyncTarget(), runId = runId)

            var totalSynced = 0
            var batchNumber = 0

            while (totalSynced < MAX_ITEMS_PER_RUN) {
                batchNumber++
                val syncedInBatch = engine.processNextBatch(batchNumber)
                if (syncedInBatch <= 0) break
                totalSynced += syncedInBatch
            }

            // Final stabilization pass. A closed trip may have been marked ended while the
            // saver coroutine was finishing its last Room insert. Re-read Room only after
            // the first queue drain, then enqueue any late/missing/changed chunks before
            // running Cloud Data Integrity. This keeps the close boundary lossless without
            // changing the capture pipeline or deleting raw data.
            val finalReconciliation = TrackChunkQueueReconciler.reconcileRecentClosedTrips()
            Log.i(
                INTEGRITY_TAG,
                "SYNC_TRACK_RECONCILIATION runId=$runId phase=POST_DRAIN scannedTrips=${finalReconciliation.scannedTrips} " +
                    "expectedChunks=${finalReconciliation.expectedChunks} requeued=${finalReconciliation.requeuedChunks} " +
                    "missing=${finalReconciliation.missingChunks} changed=${finalReconciliation.changedChunks}"
            )

            if (finalReconciliation.requeuedChunks > 0 && totalSynced < MAX_ITEMS_PER_RUN) {
                Log.w(
                    INTEGRITY_TAG,
                    "SYNC_TRACK_FINAL_DRAIN runId=$runId requeued=${finalReconciliation.requeuedChunks} reason=ROOM_CHANGED_AFTER_INITIAL_RECONCILIATION"
                )
                while (totalSynced < MAX_ITEMS_PER_RUN) {
                    batchNumber++
                    val syncedInBatch = engine.processNextBatch(batchNumber)
                    if (syncedInBatch <= 0) break
                    totalSynced += syncedInBatch
                }
            }

            TrackingPipelineIntegrityAuditor.logRelevantTrips("AFTER_CLOUD_DRAIN")
            if (totalSynced > 0 || finalReconciliation.requeuedChunks > 0) {
                try {
                    CloudDataIntegrityAuditor.auditRelevantTrips(stage = "AFTER_CLOUD_DRAIN", runId = runId)
                } catch (auditError: Exception) {
                    Log.e("DataIntegrity", "DATA_AUDIT_MISMATCH stage=AFTER_CLOUD_DRAIN runId=$runId reason=AUDITOR_FAILURE error=${auditError.javaClass.simpleName}:${auditError.message}", auditError)
                }
            }

            TripTelemetryRecorder.finishSyncRun(runId, System.currentTimeMillis())
            val telemetryFlushed = TripTelemetryRecorder.flushTouchedSyncTelemetry(applicationContext)
            if (telemetryFlushed > 0 && totalSynced < MAX_ITEMS_PER_RUN) {
                batchNumber++
                val telemetryConfirmed = engine.processNextBatch(batchNumber)
                totalSynced += telemetryConfirmed
                Log.i(INTEGRITY_TAG, "SYNC_TELEMETRY_DRAIN runId=$runId enqueued=$telemetryFlushed confirmed=$telemetryConfirmed")
            }

            val queueHealth = SyncQueueHealth.snapshot()
            val elapsedMs = System.currentTimeMillis() - startedAt
            when {
                queueHealth.retryable > 0 -> {
                    val delayMs = CloudSyncRetryPlanner.nextDelayMs()
                    AsdCloudSyncRetryKickWorker.schedule(applicationContext, delayMs)
                    Log.i(
                        INTEGRITY_TAG,
                        "SYNC_FINISHED runId=$runId result=DEFERRED synced=$totalSynced " +
                            "retryable=${queueHealth.retryable} deadLetters=${queueHealth.deadLetter} unresolved=${queueHealth.unresolved} " +
                            "batches=$batchNumber elapsedMs=$elapsedMs retryInMs=$delayMs"
                    )
                    ListenableWorker.Result.success()
                }
                queueHealth.deadLetter > 0 -> {
                    AsdCloudSyncRetryKickWorker.cancel(applicationContext)
                    Log.w(
                        INTEGRITY_TAG,
                        "SYNC_FINISHED runId=$runId result=ATTENTION_REQUIRED synced=$totalSynced " +
                            "retryable=0 deadLetters=${queueHealth.deadLetter} unresolved=${queueHealth.unresolved} " +
                            "batches=$batchNumber elapsedMs=$elapsedMs"
                    )
                    ListenableWorker.Result.success()
                }
                else -> {
                    AsdCloudSyncRetryKickWorker.cancel(applicationContext)
                    Log.i(INTEGRITY_TAG, "SYNC_QUEUE_EMPTY runId=$runId")
                    Log.i(INTEGRITY_TAG, "SYNC_FINISHED runId=$runId result=SUCCESS synced=$totalSynced unresolved=0 batches=$batchNumber elapsedMs=$elapsedMs")
                    ListenableWorker.Result.success()
                }
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                runCatching { TripTelemetryRecorder.finishSyncRun(runId, System.currentTimeMillis()) }
            }
            val elapsedMs = System.currentTimeMillis() - startedAt
            Log.w(INTEGRITY_TAG, "SYNC_WORK_CANCELLED runId=$runId attempt=$runAttemptCount elapsedMs=$elapsedMs")
            throw e
        } catch (e: Exception) {
            try {
                TripTelemetryRecorder.finishSyncRun(runId, System.currentTimeMillis())
            } catch (telemetryError: Exception) {
                Log.w(TAG, "No se pudo cerrar telemetría de sync runId=$runId", telemetryError)
            }
            val elapsedMs = System.currentTimeMillis() - startedAt
            val result = if (runAttemptCount < 5) "RETRY_EXCEPTION" else "FAILURE"
            Log.e(INTEGRITY_TAG, "SYNC_FINISHED runId=$runId result=$result attempt=$runAttemptCount elapsedMs=$elapsedMs error=${e.javaClass.simpleName}:${e.message}", e)
            Log.e(TAG, "Falló la sincronización en background", e)
            if (runAttemptCount < 5) ListenableWorker.Result.retry() else ListenableWorker.Result.failure()
        }
    }
}
