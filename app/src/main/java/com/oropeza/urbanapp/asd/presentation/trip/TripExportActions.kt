package com.oropeza.urbanapp.asd.presentation.trip

import android.content.Context
import android.net.Uri
import com.oropeza.urbanapp.asd.AsdGraph
import com.oropeza.urbanapp.asd.export.AsdClientXlsxExporter
import com.oropeza.urbanapp.asd.export.AsdGarminGpxExporter
import com.oropeza.urbanapp.asd.export.CsvExporter
import com.oropeza.urbanapp.asd.export.GpxExporter
import com.oropeza.urbanapp.asd.export.GpsAuditCsvExporter
import com.oropeza.urbanapp.asd.export.KmlExporter
import com.oropeza.urbanapp.asd.export.TrackCsvExporter
import com.oropeza.urbanapp.asd.location.LatLng
import com.oropeza.urbanapp.asd.location.PolylineSmoother

class TripExportActions {

    suspend fun exportLayoutFinal(context: Context, tripId: Long, uri: Uri): Boolean {
        val trip = AsdGraph.repo.getTripOnce(tripId) ?: return false
        CsvExporter.exportLayoutFinal(
            context = context,
            uri = uri,
            trip = trip,
            stops = AsdGraph.repo.getStopsOnce(tripId),
            delays = AsdGraph.repo.getDelaysOnce(tripId)
        )
        return true
    }

    suspend fun exportClientXlsx(context: Context, tripId: Long, uri: Uri): Boolean {
        val trip = AsdGraph.repo.getTripOnce(tripId) ?: return false
        AsdClientXlsxExporter.export(
            context = context,
            uri = uri,
            trip = trip,
            events = AsdGraph.repo.getStopsOnce(tripId),
            trackPoints = AsdGraph.repo.getTrackPointsOnce(tripId)
        )
        return true
    }

    suspend fun exportTrackCsv(context: Context, tripId: Long, uri: Uri): Boolean {
        val points = AsdGraph.repo.getTrackPointsOnce(tripId)
        val raw = points.map { LatLng(it.lat, it.lon) }
        val smooth = PolylineSmoother.movingAverage(raw, window = 3)
        val simplified = PolylineSmoother.douglasPeucker(smooth, epsilonMeters = 4.0)
        val rebuilt = points.take(simplified.size).mapIndexed { index, point ->
            point.copy(lat = simplified[index].lat, lon = simplified[index].lon)
        }
        TrackCsvExporter.exportTrackPointsCsv(context, uri, rebuilt)
        return true
    }

    suspend fun exportGpsAuditCsv(context: Context, tripId: Long, uri: Uri): Boolean {
        GpsAuditCsvExporter.export(context, uri, AsdGraph.repo.getTrackPointsOnce(tripId))
        return true
    }

    suspend fun exportTripGpx(context: Context, tripId: Long, uri: Uri): Boolean {
        val trip = AsdGraph.repo.getTripOnce(tripId) ?: return false
        GpxExporter.exportTripGpx(context, uri, trip, AsdGraph.repo.getTrackPointsOnce(tripId), AsdGraph.repo.getStopsOnce(tripId))
        return true
    }

    suspend fun exportTripKml(context: Context, tripId: Long, uri: Uri): Boolean {
        val trip = AsdGraph.repo.getTripOnce(tripId) ?: return false
        KmlExporter.exportTripKml(context, uri, trip, AsdGraph.repo.getTrackPointsOnce(tripId), AsdGraph.repo.getStopsOnce(tripId))
        return true
    }

    suspend fun exportGarminTrack(context: Context, tripId: Long, uri: Uri): Boolean {
        val trip = AsdGraph.repo.getTripOnce(tripId) ?: return false
        AsdGarminGpxExporter.exportGarminTrack(context, uri, trip, AsdGraph.repo.getTrackPointsOnce(tripId))
        return true
    }

    suspend fun exportGarminWaypoints(context: Context, tripId: Long, uri: Uri): Boolean {
        AsdGarminGpxExporter.exportGarminWaypoints(context, uri, AsdGraph.repo.getStopsOnce(tripId))
        return true
    }
}
