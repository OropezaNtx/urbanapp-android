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

    private fun StopEvent.hasBoarding(): Boolean = paxMenUp + paxWomenUp > 0
    private fun StopEvent.hasAlighting(): Boolean = paxMenDown + paxWomenDown > 0
    private fun StopEvent.hasDelay(): Boolean {
        val type = stopType.trim().uppercase(Locale("es", "MX"))
        return !delayCodes.isNullOrBlank() || type in setOf("DEMORA", "BANDERA", "DELAY")
    }

    private fun StopEvent.displayType(): String {
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

    private fun Trip.operationalDescription(): String {
        return buildString {
            if (!company.isNullOrBlank()) append("Empresa: $company\n")
            if (!aforador.isNullOrBlank()) append("Aforador: $aforador\n")
            if (!supervisor.isNullOrBlank()) append("Supervisor: $supervisor\n")
            if (!deviceNumber.isNullOrBlank()) append("Dispositivo: $deviceNumber\n")
            if (!vehicleEco.isNullOrBlank()) append("Eco: $vehicleEco\n")
            if (!plateNumber.isNullOrBlank()) append("Placa: $plateNumber\n")
            if (!notes.isNullOrBlank()) append("Observaciones: $notes")
        }.trim()
    }

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
            val tripDesc = trip.operationalDescription()

            out.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            out.appendLine("""<gpx version="1.1" creator="UrbanApp ASD" xmlns="http://www.topografix.com/GPX/1/1">""")

            out.appendLine("<metadata>")
            out.appendLine("<name>${esc(trackName)}</name>")
            if (tripDesc.isNotBlank()) out.appendLine("<desc>${esc(tripDesc)}</desc>")
            out.appendLine("<time>${esc(fmtIso(trip.startTime))}</time>")
            out.appendLine("</metadata>")

            val orderedStops = stops.sortedBy { it.timestamp }

            for (s in orderedStops) {
                val type = s.displayType()

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
                        if (s.stopAltM > 0.0) append("\nAlt: ${"%.1f".format(Locale.US, s.stopAltM)} m")
                        if (s.stopProvider.isNotBlank()) append("\nProv: ${s.stopProvider}")
                        if (s.locationStatus.isNotBlank()) append("\nStatus: ${s.locationStatus}")
                    }

                    out.appendLine("""<wpt lat="${s.stopLat}" lon="${s.stopLon}">""")
                    if (s.stopAltM > 0.0) out.appendLine("<ele>${s.stopAltM}</ele>")
                    out.appendLine("<name>${esc(name)}</name>")
                    out.appendLine("<time>${esc(fmtIso(s.stopTime))}</time>")
                    out.appendLine("<sym>Flag</sym>")
                    out.appendLine("<desc>${esc(desc)}</desc>")
                    out.appendLine("</wpt>")
                }

                val hasOut = (s.startLat != 0.0 || s.startLon != 0.0) && s.waypointStartId > 0 && s.startTime > 0L
                if (hasOut) {
                    val name = "WP${s.waypointStartId}-$type-OUT"
                    val desc = buildString {
                        append("Tipo: $type")
                        append("\nAcc: ±${"%.1f".format(Locale.US, s.startAccM)} m")
                        if (s.startAltM > 0.0) append("\nAlt: ${"%.1f".format(Locale.US, s.startAltM)} m")
                        if (s.startProvider.isNotBlank()) append("\nProv: ${s.startProvider}")
                        if (s.locationStatus.isNotBlank()) append("\nStatus: ${s.locationStatus}")
                    }

                    out.appendLine("""<wpt lat="${s.startLat}" lon="${s.startLon}">""")
                    if (s.startAltM > 0.0) out.appendLine("<ele>${s.startAltM}</ele>")
                    out.appendLine("<name>${esc(name)}</name>")
                    out.appendLine("<time>${esc(fmtIso(s.startTime))}</time>")
                    out.appendLine("<sym>Flag</sym>")
                    out.appendLine("<desc>${esc(desc)}</desc>")
                    out.appendLine("</wpt>")
                }
            }

            out.appendLine("<trk>")
            out.appendLine("<name>${esc(trackName)}</name>")

            val orderedPts = points
                .filter { it.lat != 0.0 && it.lon != 0.0 }
                .sortedBy { it.timeMs }
            if (orderedPts.isNotEmpty()) {
                val raw = orderedPts.map { LatLng(it.lat, it.lon) }
                val smooth = PolylineSmoother.movingAverage(raw, window = 3)
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
                    if (p.altM > 0.0) out.appendLine("<ele>${p.altM}</ele>")
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
