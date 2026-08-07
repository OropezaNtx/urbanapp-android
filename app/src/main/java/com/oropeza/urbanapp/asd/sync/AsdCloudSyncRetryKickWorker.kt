package com.oropeza.urbanapp.asd.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Despierta un nuevo drenado cloud cuando sync_queue indica que algún elemento
 * vuelve a ser elegible. No procesa datos por sí mismo.
 */
class AsdCloudSyncRetryKickWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "CloudSyncIntegrity"
        private const val WORK_NAME = "AsdCloudSyncRetryKickV2"

        fun schedule(context: Context, delayMs: Long) {
            val request = OneTimeWorkRequestBuilder<AsdCloudSyncRetryKickWorker>()
                .setInitialDelay(delayMs.coerceAtLeast(1_000L), TimeUnit.MILLISECONDS)
                .addTag("ASD_CLOUD_SYNC_RETRY_KICK")
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )

            Log.i(
                TAG,
                "SYNC_RETRY_SCHEDULED kickWorkId=${request.id} delayMs=${delayMs.coerceAtLeast(1_000L)}"
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "SYNC_RETRY_KICK_STARTED kickWorkId=$id")
        AsdCloudSyncWorker.enqueue(applicationContext)
        Log.i(TAG, "SYNC_RETRY_KICK_FINISHED kickWorkId=$id")
        return Result.success()
    }
}
