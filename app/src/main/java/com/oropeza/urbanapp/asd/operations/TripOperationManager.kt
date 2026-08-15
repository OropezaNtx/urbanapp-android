package com.oropeza.urbanapp.asd.operations

import android.content.Intent
import android.util.Log
import com.oropeza.urbanapp.asd.AsdGraph
import com.oropeza.urbanapp.asd.location.LocationFix
import com.oropeza.urbanapp.asd.location.TrackingService
import com.oropeza.urbanapp.asd.sync.AsdCloudSyncWorker
import com.oropeza.urbanapp.core.events.UrbanEventFactory
import com.oropeza.urbanapp.core.events.UrbanEventTypes
import com.oropeza.urbanapp.core.runtime.UrbanRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Single operational gate for trip-level mutations.
 *
 * UI may emit duplicated taps and coroutines may race. This manager is the first
 * application-level guard before Repository/Room. It keeps user operations
 * idempotent without weakening Offline First behavior.
 */
object TripOperationManager {

    sealed class OperationResult {
        data object Success : OperationResult()
        data object AlreadyRunning : OperationResult()
        data object AlreadyClosed : OperationResult()
        data class Failed(val throwable: Throwable) : OperationResult()

        val isSuccess: Boolean get() = this is Success || this is AlreadyClosed
    }

    private val closingTrips = ConcurrentHashMap<Long, Long>()

    fun isClosing(tripId: Long): Boolean = closingTrips.containsKey(tripId)

    suspend fun closeTrip(tripId: Long, fix: LocationFix): OperationResult {
        val now = System.currentTimeMillis()
        if (closingTrips.putIfAbsent(tripId, now) != null) {
            UrbanRuntime.publishEvent(
                UrbanEventFactory.asd(
                    "ASD_TRIP_CLOSE_IGNORED_ALREADY_RUNNING",
                    mapOf("tripId" to tripId)
                )
            )
            return OperationResult.AlreadyRunning
        }

        return try {
            withContext(Dispatchers.IO) {
                val current = AsdGraph.repo.getTripOnce(tripId)
                if (current?.endTime != null) {
                    return@withContext OperationResult.AlreadyClosed
                }

                val ok = AsdGraph.repo.endTripWithFix(
                    tripId = tripId,
                    stopLat = fix.lat,
                    stopLon = fix.lon,
                    stopAltM = fix.altM,
                    stopAccM = fix.accM,
                    stopProvider = fix.provider,
                    stopFixTime = fix.fixTime,
                    locationStatus = fix.status
                )

                if (ok) {
                    UrbanRuntime.publishEvent(
                        UrbanEventFactory.asd(
                            UrbanEventTypes.ASD_TRIP_CLOSED,
                            mapOf("tripId" to tripId)
                        )
                    )

                    // The database endTime is the authoritative close boundary. If the
                    // foreground service still owns this same trip, stop it immediately
                    // instead of waiting for the next 2-second saver tick. Never stop a
                    // service that already belongs to another trip.
                    val metrics = TrackingService.trackingMetrics.value
                    if (TrackingService.isRunning && metrics.tripId == tripId) {
                        runCatching {
                            AsdGraph.appContext.startService(
                                Intent(AsdGraph.appContext, TrackingService::class.java).apply {
                                    action = TrackingService.ACTION_STOP
                                    putExtra(TrackingService.EXTRA_TRIP_ID, tripId)
                                }
                            )
                        }.onFailure { e ->
                            Log.w("TripOperationManager", "TRACK_STOP_REQUEST_FAILED trip=$tripId", e)
                        }
                    }

                    // Closing a trip is a durability boundary. Always request the cloud
                    // worker explicitly so the final Room track is reconciled even when
                    // no later event or UI action happens. WorkManager keeps Offline First:
                    // without network this request simply waits until connectivity returns.
                    AsdCloudSyncWorker.enqueue(AsdGraph.appContext)
                    Log.i("TripOperationManager", "TRIP_CLOSE_SYNC_REQUESTED trip=$tripId")
                    OperationResult.Success
                } else {
                    val after = AsdGraph.repo.getTripOnce(tripId)
                    if (after?.endTime != null) OperationResult.AlreadyClosed else OperationResult.AlreadyRunning
                }
            }
        } catch (t: Throwable) {
            Log.e("TripOperationManager", "Failed to close trip $tripId", t)
            OperationResult.Failed(t)
        } finally {
            closingTrips.remove(tripId)
        }
    }
}
