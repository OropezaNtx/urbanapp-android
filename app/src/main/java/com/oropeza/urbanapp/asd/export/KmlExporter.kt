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

object KmlExporter {

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

    suspend fun exportTripKml(
        context: Context,
        uri: Uri,
        trip: Trip,
        points: List<TrackPoint>,
        stops: List<StopEvent>
    ) = withContext(Dispatchers.IO) {
        val os = context.contentResolver.openOutputStream(uri)
        requireNotNull(os) { "No se pudo abrir OutputStream para: $uri" }

        os.bufferedWriter(Charsets.UTF_8).use { out ->
            val tripName = "${trip.routeName} - ${trip.direction} (Trip ${trip.tripId})"
            val orderedPts = points
                .filter { it.lat != 0.0 && it.lon != 0.0 }
                .sortedBy { it.timeMs }
            val raw = orderedPts.map { LatLng(it.lat, it.lon) }
            val smooth = if (raw.size >= 3) PolylineSmoother.movingAverage(raw, window = 3) else raw

            out.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            out.appendLine("""<kml xmlns="http://www.opengis.net/kml/2.2">"")
            out.appendLine("<Document>")
            out.appendLine("<name>${esc(tripName)}</name>")
            out.appendLine("<description>${esc("UrbanApp ASD | Inicio: ${fmtIso(trip.startTime)}")}</description>")

            writeStyles(out)

            out.appendLine("<Folder>")
            out.appendLine("<name>Recorrido</name>")
            out.appendLine("<Placemark>")
            out.appendLine("<name>${esc(tripName)}</name>")
            out.appendLine("<styleUrl>#routeStyle</styleUrl>")
            out.appendLine("<LineString>")
            out.appendLine("<tessellate>1</tessellate>")
            out.appendLine("<coordinates>")
            smooth.forEach { p -> out.appendLine("${p.lon},${p.lat},0") }
            out.appendLine("</coordinates>")
            out.appendLine("</LineString>")
            out.appendLine("</Placemark>")
            out.appendLine("</Folder>")

            out.appendLine("<Folder>")
            out.appendLine("<name>Eventos</name>")
            stops
                .filter { it.stopLat != 0.0 && it.stopLon != 0.0 }
                .sortedBy { it.timestamp }
                .forEach { stop -> writeStopPlacemark(out, stop) }
            out.appendLine("</Folder>")

            out.appendLine("</Document>")
            out.appendLine("</kml>")
        }
    }

    private fun writeStyles(out: java.io.Writer) {
        out.appendLine("""
            <Style id="routeStyle">
              <LineStyle><color>ffff6500</color><width>5</width></LineStyle>
            </Style>
            <Style id="boardingStyle">
              <IconStyle><color>ffffaa00</color><scale>1.1</scale><Icon><href>http://maps.google.com/mapfiles/kml/paddle/blu-circle.png</href></Icon></IconStyle>
            </Style>
            <Style id="alightingStyle">
              <IconStyle><color>ff00a5ff</color><scale>1.1</scale><Icon><href>http://maps.google.com/mapfiles/kml/paddle/orange-circle.png</href></Icon></IconStyle>
            </Style>
            <Style id="delayStyle">
              <IconStyle><color>ffff00ff</color><scale>1.1</scale><Icon><href>http://maps.google.com/mapfiles/kml/paddle/purple-circle.png</href></Icon></IconStyle>
            </Style>
            <Style id="combinedStyle">
              <IconStyle><color>ffff00ff</color><scale>1.2</scale><Icon><href>http://maps.google.com/mapfiles/kml/paddle/purple-stars.png</href></Icon></IconStyle>
            </Style>
            <Style id="eventStyle">
              <IconStyle><scale>1.0</scale><Icon><href>http://maps.google.com/mapfiles/kml/paddle/wht-circle.png</href></Icon></IconStyle>
            </Style>
        """.trimIndent())
    }

    private fun writeStopPlacemark(out: java.io.Writer, stop: StopEvent) {
        val category = stop.category()
        val style = when (category) {
            "ASCENSO" -> "#boardingStyle"
            "DESCENSO" -> "#alightingStyle"
            "DEMORA" -> "#delayStyle"
            "ASD + DEMORA" -> "#combinedStyle"
            else -> "#eventStyle"
        }
        val title = buildString {
            append(category)
            if (!stop.stopName.isNullOrBlank()) append(" - ${stop.stopName}")
        }
        out.appendLine("<Placemark>")
        out.appendLine("<name>${esc(title)}</name>")
        out.appendLine("<styleUrl>$style</styleUrl>")
        out.appendLine("<TimeStamp><when>${esc(fmtIso(stop.timestamp))}</when></TimeStamp>")
        out.appendLine("<description>${esc(stop.description())}</description>")
        out.appendLine("<Point><coordinates>${stop.stopLon},${stop.stopLat},0</coordinates></Point>")
        out.appendLine("</Placemark>")
    }

    private fun StopEvent.hasBoarding(): Boolean = paxMenUp + paxWomenUp > 0
    private fun StopEvent.hasAlighting(): Boolean = paxMenDown + paxWomenDown > 0
    private fun StopEvent.hasDelay(): Boolean {
        val type = stopType.trim().uppercase(Locale("es", "MX"))
        return !delayCodes.isNullOrBlank() || type in setOf("DEMORA", "BANDERA", "DELAY")
    }

    private fun StopEvent.category(): String {
        val type = stopType.trim().uppercase(Locale("es", "MX"))
        val hasPax = hasBoarding() || hasAlighting()
        val hasDelay = hasDelay()
        return when {
            hasPax && hasDelay -> "ASD + DEMORA"
            type in setOf("ASCENSO", "SUBE", "BOARDING") || hasBoarding() -> "ASCENSO"
            type in setOf("DESCENSO", "BAJA", "ALIGHTING") || hasAlighting() -> "DESCENSO"
            hasDelay -> "DEMORA"
            else -> type.ifBlank { "EVENTO" }
        }
    }

    private fun StopEvent.description(): String {
        val up = paxMenUp + paxWomenUp
        val down = paxMenDown + paxWomenDown
        return buildString {
            append("Hora: ${fmtIso(timestamp)}")
            append("\nTipo: ${category()}")
            if (up > 0) append("\nSuben: H$paxMenUp M$paxWomenUp Total $up")
            if (down > 0) append("\nBajan: H$paxMenDown M$paxWomenDown Total $down")
            if (!delayCodes.isNullOrBlank()) append("\nDemora: $delayCodes")
            if (!otherDelayDesc.isNullOrBlank()) append("\nOtro: $otherDelayDesc")
            if (!notes.isNullOrBlank()) append("\nNotas: $notes")
            append("\nGPS: ±${"%.1f".format(Locale.US, stopAccM)} m")
            append("\nStatus: $locationStatus")
        }
    }
}
