package com.oropeza.urbanapp.asd.presentation.trip

import android.content.Context
import android.net.Uri
import android.util.Log
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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

    /**
     * Field/operator export: one controlled package containing the exact same
     * established exports used by the internal build. Raw/audit information is
     * not removed; it is simply packaged behind the restricted UI entry point.
     */
    suspend fun exportAllZip(context: Context, tripId: Long, destination: Uri): Boolean = withContext(Dispatchers.IO) {
        val trip = AsdGraph.repo.getTripOnce(tripId) ?: return@withContext false
        val safeRoute = sanitizeFilePart(trip.planningRouteId.ifBlank { trip.routeName })
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(trip.startTime))
        val base = "ASD_${safeRoute}_T${tripId}_$stamp"
        val tempDir = File(context.cacheDir, "afora_export_${tripId}_${System.nanoTime()}").apply { mkdirs() }

        try {
            val files = linkedMapOf<String, File>()

            suspend fun make(entryName: String, exporter: suspend (Uri) -> Boolean) {
                val file = File(tempDir, entryName)
                val ok = exporter(Uri.fromFile(file))
                if (!ok || !file.exists() || file.length() <= 0L) {
                    throw IllegalStateException("No se pudo generar $entryName")
                }
                files[entryName] = file
            }

            make("01_EXCEL_CLIENTE_${base}.xlsx") { exportClientXlsx(context, tripId, it) }
            make("02_LAYOUT_ASD_${base}.csv") { exportLayoutFinal(context, tripId, it) }
            make("03_TRACK_${base}.csv") { exportTrackCsv(context, tripId, it) }
            make("04_GPS_AUDIT_${base}.csv") { exportGpsAuditCsv(context, tripId, it) }
            make("05_GPX_${base}.gpx") { exportTripGpx(context, tripId, it) }
            make("06_KML_${base}.kml") { exportTripKml(context, tripId, it) }
            make("07_GARMIN_TRACK_${base}.gpx") { exportGarminTrack(context, tripId, it) }
            make("08_GARMIN_WAYPOINTS_${base}.gpx") { exportGarminWaypoints(context, tripId, it) }

            val output = context.contentResolver.openOutputStream(destination)
                ?: throw IllegalStateException("No se pudo abrir el destino ZIP")

            ZipOutputStream(output.buffered()).use { zip ->
                val manifest = buildString {
                    appendLine("AFORA - PAQUETE ASD")
                    appendLine("Trip local: $tripId")
                    appendLine("ID planeación: ${trip.planningRouteId}")
                    appendLine("Ruta: ${trip.routeName}")
                    appendLine("Sentido: ${trip.direction}")
                    appendLine("Inicio: ${Date(trip.startTime)}")
                    appendLine("Fin: ${trip.endTime?.let { Date(it) } ?: "EN CURSO"}")
                    appendLine("Archivos: ${files.size}")
                }.toByteArray(Charsets.UTF_8)

                zip.putNextEntry(ZipEntry("00_MANIFIESTO_${base}.txt"))
                zip.write(manifest)
                zip.closeEntry()

                files.forEach { (entryName, file) ->
                    zip.putNextEntry(ZipEntry(entryName))
                    file.inputStream().buffered().use { input -> input.copyTo(zip) }
                    zip.closeEntry()
                }
            }

            Log.i("FieldExport", "PACKAGE_OK trip=$tripId files=${files.size} destination=$destination")
            true
        } catch (t: Throwable) {
            Log.e("FieldExport", "PACKAGE_FAILED trip=$tripId", t)
            false
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun sanitizeFilePart(value: String): String = value
        .uppercase(Locale.US)
        .replace(Regex("[^A-Z0-9_-]+"), "_")
        .trim('_')
        .take(48)
        .ifBlank { "RECORRIDO" }
}
