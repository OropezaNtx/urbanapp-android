package com.oropeza.urbanapp.asd

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.oropeza.urbanapp.asd.data.local.AppDatabase
import com.oropeza.urbanapp.asd.data.local.DbProvider
import com.oropeza.urbanapp.asd.data.repository.AsdRepository
import com.oropeza.urbanapp.asd.location.TrackingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object AsdGraph {

    lateinit var db: AppDatabase
        private set

    lateinit var repo: AsdRepository
        private set

    lateinit var syncQueue: com.oropeza.urbanapp.asd.data.repository.AsdSyncQueueRepository
        private set

    lateinit var appContext: Context
        private set

    private const val TRACKING_GUARD_TAG = "TrackingIntegrity"
    private const val TRACKING_GUARD_INITIAL_DELAY_MS = 8_000L
    private const val TRACKING_GUARD_INTERVAL_MS = 10_000L

    private val processScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var trackingGuardJob: Job? = null

    @Synchronized
    fun init(context: Context) {
        if (
            ::appContext.isInitialized &&
            ::db.isInitialized &&
            ::repo.isInitialized &&
            ::syncQueue.isInitialized
        ) {
            ensureTrackingGuardStarted()
            return
        }

        appContext = context.applicationContext
        db = DbProvider.getInstance(appContext)
        repo = AsdRepository(db)
        syncQueue = com.oropeza.urbanapp.asd.data.repository.AsdSyncQueueRepository(
            db.asdSyncQueueDao()
        )
        ensureTrackingGuardStarted()
    }

    /**
     * Process-level safety net for the field-critical invariant:
     * an open ASD trip must have TrackingService owning the same trip.
     *
     * Normal navigation remains the primary starter. This guard waits for a grace
     * period and only intervenes when the normal flow did not leave tracking alive.
     * It never starts tracking for a closed trip and never takes ownership away from
     * another running trip.
     */
    @Synchronized
    private fun ensureTrackingGuardStarted() {
        if (!::appContext.isInitialized || !::repo.isInitialized) return
        if (trackingGuardJob?.isActive == true) return

        trackingGuardJob = processScope.launch {
            delay(TRACKING_GUARD_INITIAL_DELAY_MS)
            while (isActive) {
                runCatching {
                    val activeTrip = repo.getActiveTripOnce()
                    if (activeTrip != null) {
                        val metrics = TrackingService.trackingMetrics.value
                        val serviceRunning = TrackingService.isRunning
                        val ownerTripId = metrics.tripId

                        when {
                            serviceRunning && ownerTripId == activeTrip.tripId -> {
                                // Healthy. Do not interfere with the normal tracking path.
                            }

                            serviceRunning && ownerTripId != activeTrip.tripId -> {
                                Log.e(
                                    TRACKING_GUARD_TAG,
                                    "ACTIVE_TRIP_TRACKING_OWNER_MISMATCH activeTrip=${activeTrip.tripId} owner=${ownerTripId ?: -1L} action=NO_TAKEOVER"
                                )
                            }

                            else -> {
                                Log.w(
                                    TRACKING_GUARD_TAG,
                                    "ACTIVE_TRIP_WITHOUT_TRACKING trip=${activeTrip.tripId} action=RECOVERY_START"
                                )
                                val intent = Intent(appContext, TrackingService::class.java).apply {
                                    action = TrackingService.ACTION_START
                                    putExtra(TrackingService.EXTRA_TRIP_ID, activeTrip.tripId)
                                }
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    appContext.startForegroundService(intent)
                                } else {
                                    appContext.startService(intent)
                                }
                                Log.i(
                                    TRACKING_GUARD_TAG,
                                    "ACTIVE_TRIP_TRACKING_RECOVERY_REQUESTED trip=${activeTrip.tripId}"
                                )
                            }
                        }
                    }
                }.onFailure { error ->
                    Log.e(TRACKING_GUARD_TAG, "ACTIVE_TRIP_TRACKING_GUARD_FAILED", error)
                }

                delay(TRACKING_GUARD_INTERVAL_MS)
            }
        }
    }

    /**
     * Factory for the cloud sync target.
     * In the future, this could return different implementations based on config.
     */
    fun getCloudSyncTarget(): com.oropeza.urbanapp.asd.sync.cloud.CloudSyncTarget {
        return com.oropeza.urbanapp.asd.sync.cloud.firestore.FirestoreCloudSyncTarget()
    }

    fun getDeviceIdentity(context: Context): com.oropeza.urbanapp.core.identity.UrbanDeviceIdentity {
        return com.oropeza.urbanapp.core.identity.UrbanIdentityProvider.getIdentity(context)
    }
}
