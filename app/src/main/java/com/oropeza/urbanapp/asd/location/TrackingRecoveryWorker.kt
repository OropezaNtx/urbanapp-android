package com.oropeza.urbanapp.asd.location

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.oropeza.urbanapp.asd.AsdGraph

/**
 * Red de seguridad para cold starts en los que Android recrea el proceso pero
 * no redelivera TrackingService inmediatamente.
 *
 * Nunca crea ni modifica recorridos. Solo reactiva TrackingService cuando el
 * marcador persistido y Room identifican exactamente el mismo recorrido activo.
 */
class TrackingRecoveryWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "TrackingRecovery"
        private const val WORK_NAME = "AsdTrackingRecovery"

        private const val PREFS_NAME = "asd_tracking_recovery"
        private const val PREF_SESSION_ACTIVE = "session_active"
        private const val PREF_TRIP_ID = "trip_id"
        private const val PREF_LAST_HEARTBEAT_MS = "last_heartbeat_ms"

        // El heartbeat normal se actualiza cada ~10 s (5 muestras x 2 s).
        // Dejamos margen suficiente para no competir con un servicio sano.
        private const val STALE_HEARTBEAT_MS = 20_000L

        fun enqueueForColdStart(context: Context) {
            val request = OneTimeWorkRequestBuilder<TrackingRecoveryWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
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
            AsdGraph.init(applicationContext)

            if (TrackingService.isRunning) {
                Log.i(TAG, "Supervisor sin acción: TrackingService ya está activo")
                return ListenableWorker.Result.success()
            }

            val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val markerActive = prefs.getBoolean(PREF_SESSION_ACTIVE, false)
            val markedTripId = prefs.getLong(PREF_TRIP_ID, -1L)
            val lastHeartbeatMs = prefs.getLong(PREF_LAST_HEARTBEAT_MS, 0L)

            if (!markerActive || markedTripId <= 0L) {
                Log.i(TAG, "Supervisor sin acción: no existe marcador de tracking activo")
                return ListenableWorker.Result.success()
            }

            val activeTrips = AsdGraph.db.tripDao().getAllOnce().filter { it.endTime == null }
            val activeTrip = activeTrips.singleOrNull()

            if (activeTrip == null || activeTrip.tripId != markedTripId) {
                Log.w(
                    TAG,
                    "Recuperación automática rechazada: markedTripId=$markedTripId " +
                        "activeTripCount=${activeTrips.size} activeTripId=${activeTrip?.tripId}"
                )
                return ListenableWorker.Result.success()
            }

            val now = System.currentTimeMillis()
            val heartbeatAgeMs = if (lastHeartbeatMs > 0L) {
                (now - lastHeartbeatMs).coerceAtLeast(0L)
            } else {
                Long.MAX_VALUE
            }

            if (heartbeatAgeMs < STALE_HEARTBEAT_MS) {
                Log.i(
                    TAG,
                    "Supervisor sin acción: heartbeat vigente trip=$markedTripId ageMs=$heartbeatAgeMs"
                )
                return ListenableWorker.Result.success()
            }

            Log.w(
                TAG,
                "Heartbeat vencido; intentando recuperar tracking trip=$markedTripId ageMs=$heartbeatAgeMs"
            )

            val intent = Intent(applicationContext, TrackingService::class.java).apply {
                action = TrackingService.ACTION_START
                putExtra(TrackingService.EXTRA_TRIP_ID, markedTripId)
                putExtra(EXTRA_RECOVERY_SUPERVISOR, true)
            }

            try {
                ContextCompat.startForegroundService(applicationContext, intent)
                Log.w(TAG, "TrackingService solicitado por supervisor trip=$markedTripId")
                ListenableWorker.Result.success()
            } catch (e: Exception) {
                val blockedBySystem = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    e.javaClass.simpleName == "ForegroundServiceStartNotAllowedException"

                Log.e(
                    TAG,
                    if (blockedBySystem) {
                        "Android bloqueó recuperación FGS en background; se conserva recuperación manual"
                    } else {
                        "Falló el arranque automático de TrackingService"
                    },
                    e
                )

                if (runAttemptCount < 2) {
                    ListenableWorker.Result.retry()
                } else {
                    ListenableWorker.Result.failure()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Falló supervisor de recuperación", e)
            if (runAttemptCount < 2) {
                ListenableWorker.Result.retry()
            } else {
                ListenableWorker.Result.failure()
            }
        }
    }
}

const val EXTRA_RECOVERY_SUPERVISOR = "recovery_supervisor"
