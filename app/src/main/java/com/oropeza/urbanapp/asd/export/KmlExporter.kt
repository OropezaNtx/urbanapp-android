package com.oropeza.urbanapp.asd.export

import android.content.Context
import android.net.Uri
import com.oropeza.urbanapp.asd.data.local.StopEvent
import com.oropeza.urbanapp.asd.data.local.TrackPoint
import com.oropeza.urbanapp.asd.data.local.Trip
import com.oropeza.urbanapp.asd.location.engine.TrackPointQuality
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

    private fun Trip.operationalDescription(): String {
        return buildString {
            append("Afora ASD")
            append("\nInicio: ${fmtIso(startTime)}")
            if (!company.isNullOrBlank()) append("\nEmpresa: $company")
            if (!aforador.isNullOrBlank()) append("\nAforador: $aforador")
            if (!supervisor.isNullOrBlank()) append("\nSupervisor: $supervisor")
            if (!deviceNumber.isNullOrBlank()) append("\nDispositivo: $deviceNumber")
            if (!vehicleEco.isNullOrBlank()) append("\nEco: $vehicleEco")
            if (!plateNumber.isNullOrBlank()) append("\nPlaca: $plateNumber")
            if (!notes.isNullOrBlank()) append("\nObservaciones: $notes")
        }
    }

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
            val orderedPts = TrackPointQuality.cleanRoutePoints(points)

            out.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            out.appendLine("""<kml xmlns="http://www.opengis.net/kml/2.2">""")
            out.appendLine("<Document>")
            out.appendLine("<name>${esc(tripName)}</name>")
            out.appendLine("<description>${esc(trip.operationalDescription())}</description>")

            writeStyles(out)

            out.appendLine("<Folder>")
            out.appendLine("<name>Recorrido limpio</name>")
            out.appendLine("<Placemark>")
            out.appendLine("<name>${esc(tripName)}</name>")
            out.appendLine("<styleUrl>#routeStyle</styleUrl>")
            out.appendLine("<LineString>")
            out.appendLine("<altitudeMode>relativeToGround</altitudeMode>")
            out.appendLine("<tessellate>1</tessellate>")
            out.appendLine("<coordinates>")
            orderedPts.forEach { p ->
                // filteredLat/filteredLon are already the canonical analytical
                // coordinates produced by AforaGpsEngine. Do not smooth a second
                // time here: a moving average can cut corners and shift the route.
                out.appendLine("${p.filteredLon},${p.filteredLat},${if (p.altM > 0.0) p.altM else 0.0}")
            }
            out.appendLine("</coordinates>")
            out.appendLine("</LineString>")
            out.appendLine("</Placemark>")
            out.appendLine("</Folder>")

            out.appendLine("<Folder>")
            out.appendLine("<name>Eventos ASD</name>")
            stops.sortedBy { it.timestamp }.forEach { stop ->
                writeStopStartPlacemark(out, stop)
                writeStopClosePlacemark(out, stop)
            }
            out.appendLine("</Folder>")

            out.appendLine("</Document>")
            out.appendLine("</kml>")
        }
    }

    private fun writeStyles(out: java.io.Writer) {
        out.appendLine("<Style id=\"routeStyle\">")
        out.appendLine("<LineStyle><color>ffff6500</color><width>5</width></LineStyle>")
        out.appendLine("</Style>")
        out.appendLine("<Style id=\"startStyle\">")
        out.appendLine("<IconStyle><color>ff00c853</color><scale>1.1</scale><Icon><href>http://maps.google.com/mapfiles/kml/paddle/grn-circle.png</href></Icon></IconStyle>")
        out.appendLine("</Style>")
        out.appendLine("<Style id=\"closeStyle\">")
        out.appendLine("<IconStyle><color>ff0000ff</color><scale>1.1</scale><Icon><href>http://maps.google.com/mapfiles/kml/paddle/red-circle.png</href></Icon></IconStyle>")
        out.appendLine("</Style>")
        out.appendLine("<Style id=\"eventStyle\">")
        out.appendLine("<IconStyle><scale>1.0</scale><Icon><href>http://maps.google.com/mapfiles/kml/paddle/wht-circle.png</href></Icon></IconStyle>")
        out.appendLine("</Style>")
    }

    private fun writeStopStartPlacemark(out: java.io.Writer, stop: StopEvent) {
        if (stop.stopLat == 0.0 && stop.stopLon == 0.0) return
        val wp = if (stop.waypointStopId > 0) stop.waypointStopId.toString() else "?"
        val name = "WP$wp-INICIO-${stop.category()}"
        out.appendLine("<Placemark>")
        out.appendLine("<name>${esc(name)}</name>")
        out.appendLine("<styleUrl>#startStyle</styleUrl>")
        out.appendLine("<TimeStamp><when>${esc(fmtIso(stop.stopTime.takeIf { it > 0L } ?: stop.timestamp))}</when></TimeStamp>")
        out.appendLine("<description>${esc(stop.description(isClose = false))}</description>")
        out.appendLine("<Point>")
        if (stop.stopAltM > 0.0) out.appendLine("<altitudeMode>relativeToGround</altitudeMode>")
        out.appendLine("<coordinates>${stop.stopLon},${stop.stopLat},${if (stop.stopAltM > 0.0) stop.stopAltM else 0.0}</coordinates>")
        out.appendLine("</Point>")
        out.appendLine("</Placemark>")
    }

    private fun writeStopClosePlacemark(out: java.io.Writer, stop: StopEvent) {
        if (stop.startLat == 0.0 && stop.startLon == 0.0) return
        if (stop.startTime <= 0L) return
        val wp = if (stop.waypointStartId > 0) stop.waypointStartId.toString() else "?"
        val name = "WP$wp-CIERRE-${stop.category()}"
        out.appendLine("<Placemark>")
        out.appendLine("<name>${esc(name)}</name>")
        out.appendLine("<styleUrl>#closeStyle</styleUrl>")
        out.appendLine("<TimeStamp><when>${esc(fmtIso(stop.startTime))}</when></TimeStamp>")
        out.appendLine("<description>${esc(stop.description(isClose = true))}</description>")
        out.appendLine("<Point>")
        if (stop.startAltM > 0.0) out.appendLine("<altitudeMode>relativeToGround</altitudeMode>")
        out.appendLine("<coordinates>${stop.startLon},${stop.startLat},${if (stop.startAltM > 0.0) stop.startAltM else 0.0}</coordinates>")
        out.appendLine("</Point>")
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
            hasPax && hasDelay -> "ASD_DEMORA"
            hasBoarding() && hasAlighting() -> "ASD"
            hasBoarding() -> "ASCENSO"
            hasAlighting() -> "DESCENSO"
            hasDelay -> "DEMORA"
            else -> type.ifBlank { "EVENTO" }
        }
    }

    private fun StopEvent.description(isClose: Boolean): String {
        val up = paxMenUp + paxWomenUp
        val down = paxMenDown + paxWomenDown
        val time = if (isClose) startTime else stopTime.takeIf { it > 0L } ?: timestamp
        val acc = if (isClose) startAccM else stopAccM
        val provider = if (isClose) startProvider else stopProvider
        val lat = if (isClose) startLat else stopLat
        val lon = if (isClose) startLon else stopLon
        return buildString {
            append(if (isClose) "Fase: CIERRE" else "Fase: INICIO")
            append("\nHora: ${fmtIso(time)}")
            append("\nTipo: ${category()}")
            if (up > 0) append("\nSuben: H$paxMenUp M$paxWomenUp Total $up")
            if (down > 0) append("\nBajan: H$paxMenDown M$paxWomenDown Total $down")
            if (!delayCodes.isNullOrBlank()) append("\nDemora: $delayCodes")
            if (!otherDelayDesc.isNullOrBlank()) append("\nOtro: $otherDelayDesc")
            if (!stopName.isNullOrBlank()) append("\nParada: $stopName")
            if (!notes.isNullOrBlank()) append("\nNotas: $notes")
            if (hasLuggage) append("\nPorta maleta/bulto: SI")
            append("\nCoordenada: $lat,$lon")
            append("\nGPS: ±${"%.1f".format(Locale.US, acc)} m")
            if (provider.isNotBlank()) append("\nProveedor: $provider")
            append("\nStatus: $locationStatus")
        }
    }
}
