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
import java.util.concurrent.TimeUnit

/**
 * Watchdog persistente del tracking ASD.
 *
 * Se arma cuando TrackingService confirma un recorrido activo y queda registrado
 * en WorkManager, por lo que Android puede ejecutar la comprobación aunque el
 * proceso de Urban haya sido destruido. Cada ejecución sana agenda la siguiente
 * generación antes de finalizar.
 *
 * Nunca crea recorridos ni TrackPoints. Para recuperar exige coincidencia entre:
 * - marcador persistido de tracking,
 * - tripId esperado por el watchdog,
 * - exactamente un recorrido activo en Room.
 */
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

        // El servicio escribe heartbeat aproximadamente cada 10 s.
        private const val STALE_HEARTBEAT_MS = 30_000L

        // No es la cadencia GPS. Solo es la frecuencia de supervisión del proceso.
        private const val HEALTHY_CHECK_DELAY_MS = 120_000L
        private const val EARLY_RECHECK_DELAY_MS = 30_000L
        private const val BLOCKED_RECHECK_DELAY_MS = 60_000L

        fun arm(context: Context, tripId: Long) {
            if (tripId <= 0L) return
            val appContext = context.applicationContext
            WorkManager.getInstance(appContext).cancelAllWorkByTag(WATCHDOG_TAG)
            enqueueGeneration(
                context = appContext,
                tripId = tripId,
                generation = 0,
                delayMs = HEALTHY_CHECK_DELAY_MS,
                reason = "INITIAL_ARM"
            )
        }

        fun disarm(context: Context, reason: String) {
            WorkManager.getInstance(context.applicationContext).cancelAllWorkByTag(WATCHDOG_TAG)
            Log.i(TAG, "WATCHDOG_DISARMED reason=$reason")
        }

        private fun enqueueGeneration(
            context: Context,
            tripId: Long,
            generation: Int,
            delayMs: Long,
            reason: String
        ) {
            val input = Data.Builder()
                .putLong(INPUT_TRIP_ID, tripId)
                .putInt(INPUT_GENERATION, generation)
                .build()

            val request = OneTimeWorkRequestBuilder<TrackingRecoveryWorker>()
                .setInputData(input)
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .addTag(WATCHDOG_TAG)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueue(request)
            Log.i(
                TAG,
                "WATCHDOG_ARMED trip=$tripId generation=$generation delayMs=$delayMs " +
                    "reason=$reason workId=${request.id}"
            )
        }

        private fun scheduleNext(
            context: Context,
            tripId: Long,
            generation: Int,
            delayMs: Long,
            reason: String
        ) {
            val nextGeneration = generation + 1
            Log.i(
                TAG,
                "WATCHDOG_REARMED trip=$tripId fromGeneration=$generation " +
                    "toGeneration=$nextGeneration delayMs=$delayMs reason=$reason"
            )
            enqueueGeneration(
                context = context,
                tripId = tripId,
                generation = nextGeneration,
                delayMs = delayMs,
                reason = reason
            )
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

            val activeTrips = AsdGraph.db.tripDao().getAllOnce().filter { it.endTime == null }
            val activeTrip = activeTrips.singleOrNull()

            val identityValid = markerActive &&
                markedTripId > 0L &&
                markedTripId == expectedTripId &&
                activeTrip != null &&
                activeTrip.tripId == markedTripId

            if (!identityValid) {
                Log.w(
                    TAG,
                    "WATCHDOG_DISARMED reason=identity_mismatch expectedTripId=$expectedTripId " +
                        "markerActive=$markerActive markedTripId=$markedTripId " +
                        "activeTripCount=${activeTrips.size} activeTripId=${activeTrip?.tripId}"
                )
                WorkManager.getInstance(applicationContext).cancelAllWorkByTag(WATCHDOG_TAG)
                TrackingRecoveryNotification.cancel(applicationContext, "identity_mismatch")
                return ListenableWorker.Result.success()
            }

            val now = System.currentTimeMillis()
            val heartbeatAgeMs = if (lastHeartbeatMs > 0L) {
                (now - lastHeartbeatMs).coerceAtLeast(0L)
            } else {
                Long.MAX_VALUE
            }

            if (TrackingService.isRunning) {
                Log.i(
                    TAG,
                    "WATCHDOG_HEALTHY trip=$markedTripId generation=$generation ageMs=$heartbeatAgeMs"
                )
                TrackingRecoveryNotification.cancel(applicationContext, "tracking_healthy")
                scheduleNext(
                    context = applicationContext,
                    tripId = markedTripId,
                    generation = generation,
                    delayMs = HEALTHY_CHECK_DELAY_MS,
                    reason = "HEALTHY"
                )
                return ListenableWorker.Result.success()
            }

            if (heartbeatAgeMs < STALE_HEARTBEAT_MS) {
                Log.i(
                    TAG,
                    "WATCHDOG_HEALTHY trip=$markedTripId generation=$generation " +
                        "serviceRunning=false ageMs=$heartbeatAgeMs recheck=true"
                )
                scheduleNext(
                    context = applicationContext,
                    tripId = markedTripId,
                    generation = generation,
                    delayMs = EARLY_RECHECK_DELAY_MS,
                    reason = "HEARTBEAT_FRESH_SERVICE_DOWN"
                )
                return ListenableWorker.Result.success()
            }

            Log.w(
                TAG,
                "WATCHDOG_PROCESS_RECOVERY trip=$markedTripId generation=$generation ageMs=$heartbeatAgeMs"
            )

            val intent = Intent(applicationContext, TrackingService::class.java).apply {
                action = TrackingService.ACTION_START
                putExtra(TrackingService.EXTRA_TRIP_ID, markedTripId)
                putExtra(EXTRA_RECOVERY_SUPERVISOR, true)
            }

            try {
                ContextCompat.startForegroundService(applicationContext, intent)
                Log.w(TAG, "WATCHDOG_FGS_STARTED trip=$markedTripId generation=$generation")
                ListenableWorker.Result.success()
            } catch (e: Exception) {
                val blockedBySystem = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    e.javaClass.simpleName == "ForegroundServiceStartNotAllowedException"

                if (blockedBySystem) {
                    Log.e(
                        TAG,
                        "WATCHDOG_FGS_BLOCKED trip=$markedTripId generation=$generation",
                        e
                    )
                } else {
                    Log.e(
                        TAG,
                        "WATCHDOG_FGS_BLOCKED trip=$markedTripId generation=$generation " +
                            "error=${e.javaClass.simpleName}",
                        e
                    )
                }

                TrackingRecoveryNotification.show(
                    context = applicationContext,
                    tripId = markedTripId,
                    suspensionMs = heartbeatAgeMs
                )

                scheduleNext(
                    context = applicationContext,
                    tripId = markedTripId,
                    generation = generation,
                    delayMs = BLOCKED_RECHECK_DELAY_MS,
                    reason = "FGS_BLOCKED"
                )
                ListenableWorker.Result.success()
            }
        } catch (e: Exception) {
            Log.e(TAG, "WATCHDOG_FGS_BLOCKED reason=worker_failure", e)

            if (expectedTripId > 0L) {
                scheduleNext(
                    context = applicationContext,
                    tripId = expectedTripId,
                    generation = generation,
                    delayMs = BLOCKED_RECHECK_DELAY_MS,
                    reason = "WORKER_FAILURE"
                )
            }
            ListenableWorker.Result.success()
        }
    }
}

const val EXTRA_RECOVERY_SUPERVISOR = "recovery_supervisor"
