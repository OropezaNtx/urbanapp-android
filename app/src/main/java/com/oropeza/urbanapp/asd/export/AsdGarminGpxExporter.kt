package com.oropeza.urbanapp.asd.export

import android.content.Context
import android.net.Uri
import com.oropeza.urbanapp.asd.data.local.StopEvent
import com.oropeza.urbanapp.asd.data.local.TrackPoint
import com.oropeza.urbanapp.asd.data.local.Trip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.*

object AsdGarminGpxExporter {

    private val isoUtc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun fmtIso(ms: Long): String = isoUtc.format(Date(ms))

    /**
     * Limpia texto para compatibilidad extrema con MapSource:
     * - Elimina acentos.
     * - Elimina caracteres no ASCII (emojis, etc).
     * - Escapa XML básico.
     */
    private fun cleanForMapSource(input: String?): String {
        if (input == null) return ""
        val normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
        val accentFree = normalized.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        return accentFree.replace("[^\\x20-\\x7E]".toRegex(), "")
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun trackName(trip: Trip): String =
        if (trip.direction.uppercase(Locale.ROOT).contains("REGRESO")) "REGRESO" else "IDA"

    private fun garminTrackColor(trip: Trip): String =
        if (trackName(trip) == "REGRESO") "Cyan" else "Red"

    private fun waypointNameFromEvent(stop: StopEvent): String {
        val wp = when {
            stop.waypointStopId > 0 -> stop.waypointStopId
            stop.waypointStartId > 0 -> stop.waypointStartId
            else -> 0
        }
        return if (wp > 0) "%03d".format(Locale.US, wp) else "000"
    }

    private val GPX_HEADER = """<?xml version="1.0" encoding="UTF-8" standalone="no" ?>
<gpx
 xmlns="http://www.topografix.com/GPX/1/1"
 xmlns:gpxx="http://www.garmin.com/xmlschemas/GpxExtensions/v3"
 xmlns:gpxtrkx="http://www.garmin.com/xmlschemas/TrackStatsExtension/v1"
 xmlns:wptx1="http://www.garmin.com/xmlschemas/WaypointExtension/v1"
 xmlns:gpxtpx="http://www.garmin.com/xmlschemas/TrackPointExtension/v1"
 creator="eTrex 20x"
 version="1.1"
 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
 xsi:schemaLocation="http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd http://www.garmin.com/xmlschemas/GpxExtensions/v3 http://www8.garmin.com/xmlschemas/GpxExtensionsv3.xsd http://www.garmin.com/xmlschemas/TrackStatsExtension/v1 http://www8.garmin.com/xmlschemas/TrackStatsExtension.xsd http://www.garmin.com/xmlschemas/WaypointExtension/v1 http://www8.garmin.com/xmlschemas/WaypointExtensionv1.xsd http://www.garmin.com/xmlschemas/TrackPointExtension/v1 http://www8.garmin.com/xmlschemas/TrackPointExtensionv1.xsd">""".trimIndent()

    /**
     * Exporta el track en formato compatible con Garmin MapSource.
     */
    suspend fun exportGarminTrack(
        context: Context,
        uri: Uri,
        trip: Trip,
        points: List<TrackPoint>
    ) = withContext(Dispatchers.IO) {
        val os = context.contentResolver.openOutputStream(uri)
        requireNotNull(os) { "No se pudo abrir OutputStream para: $uri" }

        os.bufferedWriter(Charsets.UTF_8).use { out ->
            out.appendLine(GPX_HEADER)
            out.appendLine("  <metadata>")
            out.appendLine("    <link href=\"http://www.garmin.com\">")
            out.appendLine("      <text>Garmin International</text>")
            out.appendLine("    </link>")
            out.appendLine("    <time>${fmtIso(trip.startTime)}</time>")
            out.appendLine("  </metadata>")

            val name = trackName(trip)
            val color = garminTrackColor(trip)

            out.appendLine("  <trk>")
            out.appendLine("    <name>${cleanForMapSource(name)}</name>")
            out.appendLine("    <extensions>")
            out.appendLine("      <gpxx:TrackExtension>")
            out.appendLine("        <gpxx:DisplayColor>$color</gpxx:DisplayColor>")
            out.appendLine("      </gpxx:TrackExtension>")
            out.appendLine("    </extensions>")

            val orderedPts = points.filter { it.lat != 0.0 && it.lon != 0.0 }.sortedBy { it.timeMs }
            if (orderedPts.isNotEmpty()) {
                out.appendLine("    <trkseg>")
                for (p in orderedPts) {
                    val ele = if (p.altM > 0.0) p.altM else 0.0
                    out.appendLine("""      <trkpt lat="${"%.6f".format(Locale.US, p.lat)}" lon="${"%.6f".format(Locale.US, p.lon)}">""")
                    out.appendLine("        <ele>${"%.1f".format(Locale.US, ele)}</ele>")
                    out.appendLine("        <time>${fmtIso(p.timeMs)}</time>")
                    out.appendLine("      </trkpt>")
                }
                out.appendLine("    </trkseg>")
            }
            out.appendLine("  </trk>")
            out.appendLine("</gpx>")
        }
    }

    /**
     * Exporta los waypoints en formato compatible con Garmin MapSource.
     */
    suspend fun exportGarminWaypoints(
        context: Context,
        uri: Uri,
        stops: List<StopEvent>
    ) = withContext(Dispatchers.IO) {
        val os = context.contentResolver.openOutputStream(uri)
        requireNotNull(os) { "No se pudo abrir OutputStream para: $uri" }

        os.bufferedWriter(Charsets.UTF_8).use { out ->
            out.appendLine(GPX_HEADER)
            out.appendLine("  <metadata>")
            out.appendLine("    <link href=\"http://www.garmin.com\">")
            out.appendLine("      <text>Garmin International</text>")
            out.appendLine("    </link>")
            out.appendLine("  </metadata>")

            // Filtrar eventos GPS_PENDING o sin coordenadas.
            // El nombre del WP debe respetar la numeracion operativa real de Room,
            // no un contador local 001, 002, 003. Ejemplo: 143, 145, 147...
            val validStops = stops.filter {
                it.stopLat != 0.0 && it.stopLon != 0.0 && it.locationStatus != "GPS_PENDING"
            }.sortedBy { it.timestamp }

            for (s in validStops) {
                val wpName = waypointNameFromEvent(s)
                val ele = if (s.stopAltM > 0.0) s.stopAltM else 0.0
                out.appendLine("""  <wpt lat="${"%.6f".format(Locale.US, s.stopLat)}" lon="${"%.6f".format(Locale.US, s.stopLon)}">""")
                out.appendLine("    <ele>${"%.1f".format(Locale.US, ele)}</ele>")
                out.appendLine("    <time>${fmtIso(if (s.stopTime > 0L) s.stopTime else s.timestamp)}</time>")
                out.appendLine("    <name>$wpName</name>")
                out.appendLine("    <sym>Flag, Blue</sym>")
                out.appendLine("  </wpt>")
            }
            out.appendLine("</gpx>")
        }
    }
}
