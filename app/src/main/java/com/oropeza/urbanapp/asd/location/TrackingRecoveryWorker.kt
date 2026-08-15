package com.oropeza.urbanapp.asd.location

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.oropeza.urbanapp.asd.AsdGraph
import com.oropeza.urbanapp.asd.telemetry.TripTelemetryRecorder
import com.oropeza.urbanapp.asd.telemetry.TripTelemetrySystemSampler
import com.oropeza.urbanapp.asd.telemetry.TripTelemetryWorker
import java.util.concurrent.TimeUnit

class TrackingRecoveryWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "TrackingRecovery"
        private const val WATCHDOG_TAG = "ASD_TRACKING_RECOVERY_WATCHDOG"
        private const val INPUT_TRIP_ID = "watchdog_trip_id"
        private const val INPUT_GENERATION = "watchdog_generation"

        private const val PREFS_NAME = "asd_tracking_recovery"
        private const val PREF_SESSION_ACTIVE = "session_active"
        private const val PREF_TRIP_ID = "trip_id"
        private const val PREF_LAST_HEARTBEAT_MS = "last_heartbeat_ms"
        private const val PREF_ASSISTED_PENDING = "assisted_recovery_pending"
        private const val PREF_ASSISTED_TRIP_ID = "assisted_recovery_trip_id"
        private const val PREF_TELEMETRY_TRIP_ID = "telemetry_trip_id"

        private const val STALE_HEARTBEAT_MS = 30_000L
        private const val HEALTHY_CHECK_DELAY_MS = 120_000L
        private const val EARLY_RECHECK_DELAY_MS = 30_000L
        private const val ASSISTED_RECHECK_DELAY_MS = 300_000L
        private const val FAILURE_RECHECK_DELAY_MS = 60_000L

        fun arm(context: Context, tripId: Long) {
            if (tripId <= 0L) return
            val appContext = context.applicationContext
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putLong(PREF_TELEMETRY_TRIP_ID, tripId).apply()
            WorkManager.getInstance(appContext).cancelAllWorkByTag(WATCHDOG_TAG)
            val hadPendingRecovery = clearAssistedPending(appContext, "tracking_active")
            if (hadPendingRecovery) TrackingRecoveryNotification.cancel(appContext, "tracking_active")
            TripTelemetryWorker.arm(appContext, tripId)
            enqueueGeneration(appContext, tripId, 0, HEALTHY_CHECK_DELAY_MS, "INITIAL_ARM")
        }

        fun disarm(context: Context, reason: String) {
            val appContext = context.applicationContext
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val inMemoryTripId = TrackingService.trackingMetrics.value.tripId ?: -1L
            val tripId = prefs.getLong(
                PREF_TELEMETRY_TRIP_ID,
                prefs.getLong(PREF_TRIP_ID, inMemoryTripId)
            )
            WorkManager.getInstance(appContext).cancelAllWorkByTag(WATCHDOG_TAG)
            clearAssistedPending(appContext, reason)
            TrackingRecoveryNotification.cancel(appContext, reason)
            if (tripId > 0L) {
                TripTelemetryWorker.finish(appContext, tripId)
                Log.i(TAG, "WATCHDOG_FINAL_TELEMETRY trip=$tripId reason=$reason source=${if (prefs.contains(PREF_TELEMETRY_TRIP_ID) || prefs.contains(PREF_TRIP_ID)) "PREFS" else "TRACKING_METRICS"}")
            } else {
                Log.w(TAG, "WATCHDOG_FINAL_TELEMETRY_SKIPPED reason=$reason noTripIdentity=true")
            }
            prefs.edit().remove(PREF_TELEMETRY_TRIP_ID).apply()
            Log.i(TAG, "WATCHDOG_DISARMED reason=$reason")
        }

        private fun markAssistedPending(context: Context, tripId: Long) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(PREF_ASSISTED_PENDING, true).putLong(PREF_ASSISTED_TRIP_ID, tripId).apply()
            Log.w(TAG, "ASSISTED_RECOVERY_PENDING trip=$tripId")
        }

        private fun clearAssistedPending(context: Context, reason: String): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val wasPending = prefs.getBoolean(PREF_ASSISTED_PENDING, false)
            val hadStoredTrip = prefs.contains(PREF_ASSISTED_TRIP_ID)
            if (!wasPending && !hadStoredTrip) return false
            prefs.edit().remove(PREF_ASSISTED_PENDING).remove(PREF_ASSISTED_TRIP_ID).apply()
            Log.i(TAG, "ASSISTED_RECOVERY_CLEARED reason=$reason")
            return true
        }

        private fun enqueueGeneration(context: Context, tripId: Long, generation: Int, delayMs: Long, reason: String) {
            val input = Data.Builder().putLong(INPUT_TRIP_ID, tripId).putInt(INPUT_GENERATION, generation).build()
            val request = OneTimeWorkRequestBuilder<TrackingRecoveryWorker>()
                .setInputData(input).setInitialDelay(delayMs, TimeUnit.MILLISECONDS).addTag(WATCHDOG_TAG).build()
            WorkManager.getInstance(context.applicationContext).enqueue(request)
            Log.i(TAG, "WATCHDOG_ARMED trip=$tripId generation=$generation delayMs=$delayMs reason=$reason workId=${request.id}")
        }

        private fun scheduleNext(context: Context, tripId: Long, generation: Int, delayMs: Long, reason: String) {
            val nextGeneration = generation + 1
            Log.i(TAG, "WATCHDOG_REARMED trip=$tripId fromGeneration=$generation toGeneration=$nextGeneration delayMs=$delayMs reason=$reason")
            enqueueGeneration(context, tripId, nextGeneration, delayMs, reason)
        }
    }

    override suspend fun doWork(): ListenableWorker.Result {
        val expectedTripId = inputData.getLong(INPUT_TRIP_ID, -1L)
        val generation = inputData.getInt(INPUT_GENERATION, 0)
        return try {
            AsdGraph.init(applicationContext)
            if (expectedTripId <= 0L) {
                Log.w(TAG, "WATCHDOG_DISARMED reason=invalid_expected_trip")
                return ListenableWorker.Result.success()
            }

            val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val markerActive = prefs.getBoolean(PREF_SESSION_ACTIVE, false)
            val markedTripId = prefs.getLong(PREF_TRIP_ID, -1L)
            val lastHeartbeatMs = prefs.getLong(PREF_LAST_HEARTBEAT_MS, 0L)
            val assistedPending = prefs.getBoolean(PREF_ASSISTED_PENDING, false)
            val assistedTripId = prefs.getLong(PREF_ASSISTED_TRIP_ID, -1L)
            val activeTrips = AsdGraph.db.tripDao().getAllOnce().filter { it.endTime == null }
            val activeTrip = activeTrips.singleOrNull()
            val identityValid = markerActive && markedTripId > 0L && markedTripId == expectedTripId && activeTrip != null && activeTrip.tripId == markedTripId

            if (!identityValid) {
                Log.w(TAG, "WATCHDOG_DISARMED reason=identity_mismatch expectedTripId=$expectedTripId markerActive=$markerActive markedTripId=$markedTripId activeTripCount=${activeTrips.size} activeTripId=${activeTrip?.tripId}")
                WorkManager.getInstance(applicationContext).cancelAllWorkByTag(WATCHDOG_TAG)
                clearAssistedPending(applicationContext, "identity_mismatch")
                TrackingRecoveryNotification.cancel(applicationContext, "identity_mismatch")
                TripTelemetryWorker.cancel(applicationContext, expectedTripId)
                return ListenableWorker.Result.success()
            }

            TripTelemetryRecorder.ensureStarted(applicationContext, markedTripId)
            TripTelemetrySystemSampler.sample(applicationContext, markedTripId)
            val now = System.currentTimeMillis()
            val heartbeatAgeMs = if (lastHeartbeatMs > 0L) (now - lastHeartbeatMs).coerceAtLeast(0L) else Long.MAX_VALUE
            val runningTripId = TrackingService.trackingMetrics.value.tripId
            val serviceOwnsExpectedTrip = TrackingService.isRunning && runningTripId == markedTripId

            if (serviceOwnsExpectedTrip) {
                Log.i(TAG, "WATCHDOG_HEALTHY trip=$markedTripId generation=$generation ageMs=$heartbeatAgeMs")
                TripTelemetryRecorder.recordWatchdogHealthy(markedTripId)
                if (assistedPending && assistedTripId == markedTripId) {
                    clearAssistedPending(applicationContext, "tracking_healthy")
                    TrackingRecoveryNotification.cancel(applicationContext, "tracking_healthy")
                }
                scheduleNext(applicationContext, markedTripId, generation, HEALTHY_CHECK_DELAY_MS, "HEALTHY")
                return ListenableWorker.Result.success()
            }

            if (TrackingService.isRunning && runningTripId != markedTripId) {
                Log.e(
                    TAG,
                    "WATCHDOG_OWNER_MISMATCH expectedTrip=$markedTripId runningTrip=${runningTripId ?: -1L} generation=$generation"
                )
                // Do not call this healthy and do not keep two logical owners alive.
                // ACTION_START is idempotent and TrackingService switches ownership after
                // validating that the expected trip is still the unique open trip.
                val intent = Intent(applicationContext, TrackingService::class.java).apply {
                    action = TrackingService.ACTION_START
                    putExtra(TrackingService.EXTRA_TRIP_ID, markedTripId)
                    putExtra(EXTRA_RECOVERY_SUPERVISOR, true)
                }
                try {
                    ContextCompat.startForegroundService(applicationContext, intent)
                    Log.w(TAG, "WATCHDOG_OWNER_REPAIR_REQUESTED expectedTrip=$markedTripId previousTrip=${runningTripId ?: -1L}")
                    scheduleNext(applicationContext, markedTripId, generation, EARLY_RECHECK_DELAY_MS, "OWNER_REPAIR")
                    return ListenableWorker.Result.success()
                } catch (e: Exception) {
                    Log.e(TAG, "WATCHDOG_OWNER_REPAIR_FAILED trip=$markedTripId", e)
                    TripTelemetryRecorder.recordFgsBlocked(markedTripId, heartbeatAgeMs)
                    markAssistedPending(applicationContext, markedTripId)
                    TrackingRecoveryNotification.show(applicationContext, markedTripId, heartbeatAgeMs)
                    scheduleNext(applicationContext, markedTripId, generation, ASSISTED_RECHECK_DELAY_MS, "OWNER_REPAIR_BLOCKED")
                    return ListenableWorker.Result.success()
                }
            }

            if (heartbeatAgeMs < STALE_HEARTBEAT_MS) {
                Log.i(TAG, "WATCHDOG_HEALTHY trip=$markedTripId generation=$generation serviceRunning=false ageMs=$heartbeatAgeMs recheck=true")
                TripTelemetryRecorder.recordWatchdogHealthy(markedTripId)
                scheduleNext(applicationContext, markedTripId, generation, EARLY_RECHECK_DELAY_MS, "HEARTBEAT_FRESH_SERVICE_DOWN")
                return ListenableWorker.Result.success()
            }

            if (assistedPending && assistedTripId == markedTripId) {
                Log.w(TAG, "ASSISTED_RECOVERY_PENDING trip=$markedTripId generation=$generation ageMs=$heartbeatAgeMs")
                scheduleNext(applicationContext, markedTripId, generation, ASSISTED_RECHECK_DELAY_MS, "ASSISTED_PENDING")
                return ListenableWorker.Result.success()
            }

            Log.w(TAG, "WATCHDOG_PROCESS_RECOVERY trip=$markedTripId generation=$generation ageMs=$heartbeatAgeMs")
            val intent = Intent(applicationContext, TrackingService::class.java).apply {
                action = TrackingService.ACTION_START
                putExtra(TrackingService.EXTRA_TRIP_ID, markedTripId)
                putExtra(EXTRA_RECOVERY_SUPERVISOR, true)
            }

            try {
                ContextCompat.startForegroundService(applicationContext, intent)
                Log.w(TAG, "WATCHDOG_FGS_STARTED trip=$markedTripId generation=$generation awaitingTrackingConfirmation=true")
                ListenableWorker.Result.success()
            } catch (e: Exception) {
                val blockedBySystem = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e.javaClass.simpleName == "ForegroundServiceStartNotAllowedException"
                if (blockedBySystem) Log.e(TAG, "WATCHDOG_FGS_BLOCKED trip=$markedTripId generation=$generation", e)
                else Log.e(TAG, "WATCHDOG_FGS_BLOCKED trip=$markedTripId generation=$generation error=${e.javaClass.simpleName}", e)
                TripTelemetryRecorder.recordFgsBlocked(markedTripId, heartbeatAgeMs)
                markAssistedPending(applicationContext, markedTripId)
                TrackingRecoveryNotification.show(applicationContext, markedTripId, heartbeatAgeMs)
                scheduleNext(applicationContext, markedTripId, generation, ASSISTED_RECHECK_DELAY_MS, "ASSISTED_PENDING")
                ListenableWorker.Result.success()
            }
        } catch (e: Exception) {
            Log.e(TAG, "WATCHDOG_FGS_BLOCKED reason=worker_failure", e)
            if (expectedTripId > 0L) scheduleNext(applicationContext, expectedTripId, generation, FAILURE_RECHECK_DELAY_MS, "WORKER_FAILURE")
            ListenableWorker.Result.success()
        }
    }
}

const val EXTRA_RECOVERY_SUPERVISOR = "recovery_supervisor"
