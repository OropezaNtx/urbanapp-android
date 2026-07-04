package com.oropeza.urbanapp.asd.presentation.trip

import android.content.Context
import android.net.Uri
import com.oropeza.urbanapp.asd.domain.trip.TripDomain
import com.oropeza.urbanapp.asd.domain.trip.TripOperationResult
import com.oropeza.urbanapp.asd.location.LocationFix

class TripActions(
    private val exports: TripExportActions = TripExportActions()
) {

    suspend fun closeTrip(tripId: Long, fix: LocationFix): TripActionResult {
        return when (val result = TripDomain.useCases.closeTrip(tripId, fix)) {
            TripOperationResult.Success -> TripActionResult.Success
            TripOperationResult.AlreadyClosed -> TripActionResult.Success
            TripOperationResult.AlreadyRunning -> TripActionResult.Ignored
            is TripOperationResult.Failed -> TripActionResult.Failed(result.throwable.message ?: "No se pudo cerrar el recorrido")
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
            else -> false
        }
    }
}
