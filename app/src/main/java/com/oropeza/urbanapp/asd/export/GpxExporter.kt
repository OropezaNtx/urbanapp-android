package com.oropeza.urbanapp.asd.export

import android.content.Context
import android.net.Uri
import com.oropeza.urbanapp.asd.data.local.StopEvent
import com.oropeza.urbanapp.asd.data.local.TrackPoint
import com.oropeza.urbanapp.asd.data.local.Trip
import com.oropeza.urbanapp.asd.location.LatLng
import com.oropeza.urbanapp.asd.location.PolylineSmoother
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object GpxExporter {

    private val isoUtc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun fmtIso(ms: Long): String = isoUtc.format(Date(ms))

    private fun esc(s: String): String =
        s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    private fun safeType(t: String?): String =
        (t ?: "EVENTO").trim().ifBlank { "EVENTO" }.uppercase(Locale("es", "MX"))

    /**
     * Exporta 1 GPX por viaje:
     * - Track con trkpt (suavizado sin perder timestamps)
     * - Waypoints: 2 por StopEvent (IN/OUT) usando waypointStopId/waypointStartId
     */
    suspend fun exportTripGpx(
        context: Context,
        uri: Uri,
        trip: Trip,
        points: List<TrackPoint>,
        stops: List<StopEvent>
    ) = withContext(Dispatchers.IO) {

        val os = context.contentResolver.openOutputStream(uri)
        requireNotNull(os) { "No se pudo abrir OutputStream para: $uri" }

        os.bufferedWriter(Charsets.UTF_8).use { out ->

            val trackName = "${trip.routeName} - ${trip.direction} (Trip ${trip.tripId})"

            out.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            out.appendLine("""<gpx version="1.1" creator="UrbanApp ASD" xmlns="http://www.topografix.com/GPX/1/1">""")

            // ===== metadata =====
            out.appendLine("<metadata>")
            out.appendLine("<name>${esc(trackName)}</name>")
            out.appendLine("<time>${esc(fmtIso(trip.startTime))}</time>")
            out.appendLine("</metadata>")

            // ===== WAYPOINTS (banderas IN/OUT) =====
            // Ordenados por timestamp (inicio del evento)
            val orderedStops = stops.sortedBy { it.timestamp }

            for (s in orderedStops) {
                val type = safeType(s.stopType)

                // --- IN (parada/inicio) ---
                val hasIn = (s.stopLat != 0.0 || s.stopLon != 0.0) && s.waypointStopId > 0 && s.stopTime > 0L
                if (hasIn) {
                    val name = "WP${s.waypointStopId}-$type-IN"

                    val desc = buildString {
                        append("Tipo: $type")
                        if (!s.delayCodes.isNullOrBlank()) append("\nDemora: ${s.delayCodes}")
                        if (!s.otherDelayDesc.isNullOrBlank()) append("\nOtro: ${s.otherDelayDesc}")
                        if (!s.stopName.isNullOrBlank()) append("\nParada: ${s.stopName}")
                        if (!s.notes.isNullOrBlank()) append("\nObs: ${s.notes}")
                        if (s.count > 0) append("\nConteo: ${s.count}")
                        if (s.hasLuggage) append("\nPorta maleta/bulto: SI")

                        val up = s.paxMenUp + s.paxWomenUp
                        val down = s.paxMenDown + s.paxWomenDown
                        if (up + down > 0) {
                            append("\nSuben: H${s.paxMenUp} M${s.paxWomenUp} (T${up})")
                            append("\nBajan: H${s.paxMenDown} M${s.paxWomenDown} (T${down})")
                        }

                        append("\nAcc: ±${"%.1f".format(Locale.US, s.stopAccM)} m")
                        if (!s.stopProvider.isNullOrBlank()) append("\nProv: ${s.stopProvider}")
                        if (!s.locationStatus.isNullOrBlank()) append("\nStatus: ${s.locationStatus}")
                    }

                    out.appendLine("""<wpt lat="${s.stopLat}" lon="${s.stopLon}">""")
                    out.appendLine("<name>${esc(name)}</name>")
                    out.appendLine("<time>${esc(fmtIso(s.stopTime))}</time>")
                    out.appendLine("<sym>Flag</sym>")
                    out.appendLine("<desc>${esc(desc)}</desc>")
                    out.appendLine("</wpt>")
                }

                // --- OUT (arranque/fin) ---
                val hasOut = (s.startLat != 0.0 || s.startLon != 0.0) && s.waypointStartId > 0 && s.startTime > 0L
                if (hasOut) {
                    val name = "WP${s.waypointStartId}-$type-OUT"

                    val desc = buildString {
                        append("Tipo: $type")
                        append("\nAcc: ±${"%.1f".format(Locale.US, s.startAccM)} m")
                        if (!s.startProvider.isNullOrBlank()) append("\nProv: ${s.startProvider}")
                        if (!s.locationStatus.isNullOrBlank()) append("\nStatus: ${s.locationStatus}")
                    }

                    out.appendLine("""<wpt lat="${s.startLat}" lon="${s.startLon}">""")
                    out.appendLine("<name>${esc(name)}</name>")
                    out.appendLine("<time>${esc(fmtIso(s.startTime))}</time>")
                    out.appendLine("<sym>Flag</sym>")
                    out.appendLine("<desc>${esc(desc)}</desc>")
                    out.appendLine("</wpt>")
                }
            }

            // ===== TRACK =====
            out.appendLine("<trk>")
            out.appendLine("<name>${esc(trackName)}</name>")

            val orderedPts = points.sortedBy { it.timeMs }
            if (orderedPts.isNotEmpty()) {

                // ✅ Suavizado que NO cambia cantidad de puntos (mantiene timestamps)
                val raw = orderedPts.map { LatLng(it.lat, it.lon) }
                val smooth = PolylineSmoother.movingAverage(raw, window = 3)

                // Segmentación por gaps (túnel/pérdida de señal)
                val gapMs = 12_000L

                out.appendLine("<trkseg>")
                for (i in orderedPts.indices) {
                    if (i > 0) {
                        val dt = orderedPts[i].timeMs - orderedPts[i - 1].timeMs
                        if (dt > gapMs) {
                            out.appendLine("</trkseg>")
                            out.appendLine("<trkseg>")
                        }
                    }

                    val p = orderedPts[i]
                    val s = smooth[i]

                    out.appendLine("""<trkpt lat="${s.lat}" lon="${s.lon}">""")
                    out.appendLine("<time>${esc(fmtIso(p.timeMs))}</time>")
                    out.appendLine("</trkpt>")
                }
                out.appendLine("</trkseg>")
            }

            out.appendLine("</trk>")
            out.appendLine("</gpx>")
        }
    }
}
