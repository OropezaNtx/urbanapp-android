package com.oropeza.urbanapp.asd.telemetry

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.oropeza.urbanapp.asd.AsdGraph
import com.oropeza.urbanapp.asd.sync.AsdCloudSyncWorker

/**
 * One-shot lifecycle worker. Periodic samples are piggybacked on the existing
 * persistent tracking watchdog; this worker only captures the initial/final edge.
 */
class TripTelemetryWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "TripTelemetry"
        private const val INPUT_TRIP_ID = "telemetry_trip_id"
        private const val INPUT_FORCE_FINAL = "telemetry_force_final"

        private fun tripTag(tripId: Long) = "ASD_TRIP_TELEMETRY_$tripId"

        fun arm(context: Context, tripId: Long) {
            if (tripId <= 0L) return
            val appContext = context.applicationContext
            WorkManager.getInstance(appContext).cancelAllWorkByTag(tripTag(tripId))
            enqueue(appContext, tripId, false)
            Log.i(TAG, "TELEMETRY_ARMED trip=$tripId mode=INITIAL_ONLY")
        }

        fun finish(context: Context, tripId: Long) {
            if (tripId <= 0L) return
            val appContext = context.applicationContext
            WorkManager.getInstance(appContext).cancelAllWorkByTag(tripTag(tripId))
            enqueue(appContext, tripId, true)
            Log.i(TAG, "TELEMETRY_FINISH_REQUESTED trip=$tripId")
        }

        fun cancel(context: Context, tripId: Long) {
            if (tripId > 0L) WorkManager.getInstance(context.applicationContext).cancelAllWorkByTag(tripTag(tripId))
            Log.i(TAG, "TELEMETRY_CANCELLED trip=$tripId")
        }

        private fun enqueue(context: Context, tripId: Long, forceFinal: Boolean) {
            val input = Data.Builder()
                .putLong(INPUT_TRIP_ID, tripId)
                .putBoolean(INPUT_FORCE_FINAL, forceFinal)
                .build()
            val request = OneTimeWorkRequestBuilder<TripTelemetryWorker>()
                .setInputData(input)
                .addTag("ASD_TRIP_TELEMETRY")
                .addTag(tripTag(tripId))
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }

    override suspend fun doWork(): ListenableWorker.Result {
        val tripId = inputData.getLong(INPUT_TRIP_ID, -1L)
        val forceFinal = inputData.getBoolean(INPUT_FORCE_FINAL, false)
        if (tripId <= 0L) return ListenableWorker.Result.success()

        return try {
            AsdGraph.init(applicationContext)
            val trip = AsdGraph.db.tripDao().getByIdOnce(tripId) ?: return ListenableWorker.Result.success()
            TripTelemetryRecorder.ensureStarted(applicationContext, tripId)
            val finished = forceFinal || trip.endTime != null
            TripTelemetrySystemSampler.sample(applicationContext, tripId, finished = finished)

            // Initial sample creates the first cloud-visible telemetry snapshot;
            // final sample always closes and flushes the last one.
            TripTelemetryRecorder.enqueueCloudSnapshot(applicationContext, tripId)
            AsdCloudSyncWorker.enqueue(applicationContext)

            Log.i(TAG, "TELEMETRY_LIFECYCLE_SAMPLE trip=$tripId final=$finished")
            if (finished) Log.i(TAG, "TELEMETRY_FINALIZED trip=$tripId")
            ListenableWorker.Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "TELEMETRY_SAMPLE_FAILED trip=$tripId forceFinal=$forceFinal", e)
            ListenableWorker.Result.success()
        }
    }
}
