package com.oropeza.urbanapp.asd.presentation.trip

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.oropeza.urbanapp.asd.AsdGraph
import com.oropeza.urbanapp.asd.domain.trip.TripDomain
import com.oropeza.urbanapp.asd.domain.trip.TripOperationResult
import com.oropeza.urbanapp.asd.location.LocationFix
import com.oropeza.urbanapp.asd.location.TrackingService

class TripActions(
    private val exports: TripExportActions = TripExportActions()
) {

    suspend fun closeTrip(tripId: Long, fix: LocationFix): TripActionResult {
        return when (val result = TripDomain.useCases.closeTrip(tripId, fix)) {
            TripOperationResult.Success -> {
                stopOwnedTrackingAfterClosure(tripId)
                TripActionResult.Success
            }
            TripOperationResult.AlreadyClosed -> {
                // Defensive cleanup: if an old/stale TrackingService still owns this
                // already-closed trip, stop it without affecting any other active trip.
                stopOwnedTrackingAfterClosure(tripId)
                TripActionResult.Success
            }
            TripOperationResult.AlreadyRunning -> TripActionResult.Ignored
            is TripOperationResult.Failed -> TripActionResult.Failed(result.throwable.message ?: "No se pudo cerrar el recorrido")
        }
    }

    private fun stopOwnedTrackingAfterClosure(tripId: Long) {
        val runningTripId = TrackingService.trackingMetrics.value.tripId
        if (!TrackingService.isRunning || runningTripId != tripId) return

        val context = AsdGraph.appContext
        try {
            context.startService(
                Intent(context, TrackingService::class.java).apply {
                    action = TrackingService.ACTION_STOP
                    putExtra(TrackingService.EXTRA_TRIP_ID, tripId)
                }
            )
            Log.i("TripActions", "TRACK_STOP_AFTER_CLOSE requested trip=$tripId")
        } catch (e: Exception) {
            // The service also self-validates endTime from Room in its saver/watchdog
            // loops, so failure to deliver this immediate stop remains recoverable.
            Log.w("TripActions", "Immediate TRACK_STOP after closure failed trip=$tripId", e)
        }
    }

    suspend fun export(context: Context, tripId: Long, type: String, uri: Uri): Boolean {
        return when (type) {
            "CLIENT" -> exports.exportClientXlsx(context, tripId, uri)
            "CSV" -> exports.exportLayoutFinal(context, tripId, uri)
            "TRACK" -> exports.exportTrackCsv(context, tripId, uri)
            "GPS_AUDIT" -> exports.exportGpsAuditCsv(context, tripId, uri)
            "GPX" -> exports.exportTripGpx(context, tripId, uri)
            "KML" -> exports.exportTripKml(context, tripId, uri)
            "GARMIN_T" -> exports.exportGarminTrack(context, tripId, uri)
            "GARMIN_W" -> exports.exportGarminWaypoints(context, tripId, uri)
            "ZIP" -> exports.exportAllZip(context, tripId, uri)
            else -> false
        }
    }
}
