package com.oropeza.urbanapp.asd.export

import android.content.Context
import android.net.Uri
import com.oropeza.urbanapp.asd.data.local.CcEvent
import com.oropeza.urbanapp.asd.data.local.CcSession
import com.oropeza.urbanapp.asd.data.local.DelayEvent
import com.oropeza.urbanapp.asd.data.local.StopEvent
import com.oropeza.urbanapp.asd.data.local.TrackPoint
import com.oropeza.urbanapp.asd.data.local.Trip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

object CsvExporter {

    private val df = SimpleDateFormat("dd/MM/yyyy", Locale("es", "MX"))
    private val tf = SimpleDateFormat("HH:mm:ss", Locale("es", "MX"))
    private val dtf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("es", "MX"))

    private fun escape(v: Any?): String {
        val s = (v?.toString() ?: "")
        return if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            "\"${s.replace("\"", "\"\"")}\""
        } else s
    }

    private fun fmtDate(ms: Long?): String = if (ms == null) "" else df.format(Date(ms))
    private fun fmtTime(ms: Long?): String = if (ms == null) "" else tf.format(Date(ms))
    private fun fmtDateTime(ms: Long?): String = if (ms == null) "" else dtf.format(Date(ms))

    private fun fmtDurationMs(ms: Long?): String {
        if (ms == null) return ""
        val totalSec = max(0, (ms / 1000).toInt())
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return "%02d:%02d:%02d".format(h, m, s)
    }

    private suspend fun writeCsvUtf8Bom(
        context: Context,
        uri: Uri,
        headers: List<String>,
        rows: List<List<Any?>>
    ) {
        withContext(Dispatchers.IO) {
            val os = context.contentResolver.openOutputStream(uri)
            requireNotNull(os) { "No se pudo abrir OutputStream para: $uri" }

            os.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            os.bufferedWriter(Charsets.UTF_8).use { out ->
                out.appendLine(headers.joinToString(","))
                rows.forEach { row -> out.appendLine(row.joinToString(",") { escape(it) }) }
            }
        }
    }

    private const val EPOCH_MIN_REASONABLE = 100_000_000_000L

    private fun resolveStopEpoch(s: StopEvent): Long? {
        return when {
            s.stopTime >= EPOCH_MIN_REASONABLE -> s.stopTime
            s.timestamp >= EPOCH_MIN_REASONABLE -> s.timestamp
            else -> null
        }
    }

    private fun resolveStartEpoch(s: StopEvent, stopEpoch: Long?): Long? {
        val st = s.startTime
        return when {
            st >= EPOCH_MIN_REASONABLE -> st
            stopEpoch != null && st in 1..(6 * 60 * 60 * 1000L) -> stopEpoch + st
            st == 0L && s.timestamp >= EPOCH_MIN_REASONABLE -> s.timestamp
            else -> null
        }
    }

    private fun resolveDelayDurationMs(s: StopEvent, stopEpoch: Long?, startEpoch: Long?): Long {
        if (stopEpoch != null && startEpoch != null) return max(0, startEpoch - stopEpoch)
        if (s.startTime in 1..(6 * 60 * 60 * 1000L)) return s.startTime
        return 0L
    }

    suspend fun exportTripAllInOne(
        context: Context,
        uri: Uri,
        trip: Trip,
        stops: List<StopEvent>,
        delays: List<DelayEvent>
    ) {
        val headers = listOf(
            "record_type", "tripId", "routeName", "company", "vehicleEco", "direction",
            "startTime", "endTime", "eventId", "timestamp", "stopType", "count", "stopName",
            "delayId", "delayType", "delayStart", "delayEnd", "delayDurationSec", "notes"
        )

        val rows = mutableListOf<List<Any?>>()
        rows += listOf(
            "TRIP", trip.tripId, trip.routeName, trip.company, trip.vehicleEco, trip.direction,
            fmtDateTime(trip.startTime), fmtDateTime(trip.endTime), null, null, null, null, null,
            null, null, null, null, null, trip.notes
        )

        stops.forEach { s ->
            rows += listOf(
                "STOP", trip.tripId, trip.routeName, trip.company, trip.vehicleEco, trip.direction,
                fmtDateTime(trip.startTime), fmtDateTime(trip.endTime), s.eventId, fmtDateTime(s.timestamp),
                s.stopType, s.count, s.stopName, null, null, null, null, null, s.notes
            )
        }

        delays.forEach { d ->
            val dur = d.timestampEnd?.let { (it - d.timestampStart) / 1000 } ?: 0
            rows += listOf(
                "DELAY", trip.tripId, trip.routeName, trip.company, trip.vehicleEco, trip.direction,
                fmtDateTime(trip.startTime), fmtDateTime(trip.endTime), null, null, null, null, null,
                d.delayId, d.delayType, fmtDateTime(d.timestampStart), fmtDateTime(d.timestampEnd), dur, d.notes
            )
        }

        writeCsvUtf8Bom(context, uri, headers, rows)
    }

    suspend fun exportLayoutFinal(
        context: Context,
        uri: Uri,
        trip: Trip,
        stops: List<StopEvent>,
        delays: List<DelayEvent>
    ) {
        val headers = listOf(
            "ID",
            "No. Recorrido",
            "Ruta / Derrotero",
            "Empresa",
            "Fecha",
            "ES / FS",
            "Sentido",
            "Base de inicio",
            "Base final",
            "Hora de inicio",
            "Hora final",
            "Tiempo en recorrido",
            "No. Placa",
            "No. Económico",
            "Tipo de vehículo",
            "Capacidad de asientos",
            "Waypoint de parada",
            "Waypoint de arranque",
            "Coordenada de parada",
            "Coordenada de arranque",
            "Hora de parada",
            "Hora de arranque",
            "Tiempo en demora",
            "Pax. Hombres Suben",
            "Pax. Mujeres Suben",
            "Pax. Hombres bajan",
            "Pax. Mujeres bajan",
            "Total suben",
            "Total bajan",
            "Total a bordo",
            "Tipo de demora",
            "Porta maleta o bulto voluminoso",
            "Observaciones",
            "GPS Status",
            "GPS Accuracy m",
            "GPS Provider",
            "GPS Fix Time"
        )

        val rows = mutableListOf<List<Any?>>()
        val fecha = fmtDate(trip.startTime)
        val horaInicio = fmtTime(trip.startTime)
        val endMs = trip.endTime ?: System.currentTimeMillis()
        val horaFin = fmtTime(endMs)
        val tiempoRecorrido = fmtDurationMs(endMs - trip.startTime)

        var totalAbordoAcc = 0

        stops.sortedBy { it.timestamp }.forEach { s ->
            val menUp = s.paxMenUp
            val womenUp = s.paxWomenUp
            val menDown = s.paxMenDown
            val womenDown = s.paxWomenDown
            val totalSuben = menUp + womenUp
            val totalBajan = menDown + womenDown

            val delta = totalSuben - totalBajan
            totalAbordoAcc += delta
            if (totalAbordoAcc < 0) totalAbordoAcc = 0

            val coordParada = if (s.stopLat != 0.0 || s.stopLon != 0.0) "${s.stopLat},${s.stopLon}" else ""
            val coordArranque = if (s.startLat != 0.0 || s.startLon != 0.0) "${s.startLat},${s.startLon}" else ""
            val stopEpoch = resolveStopEpoch(s)
            val startEpoch = resolveStartEpoch(s, stopEpoch)
            val delayDurMs = resolveDelayDurationMs(s, stopEpoch, startEpoch)

            val obs = buildString {
                if (!s.notes.isNullOrBlank()) append(s.notes.trim())
                if (!s.otherDelayDesc.isNullOrBlank()) {
                    if (isNotEmpty()) append(" | ")
                    append("Otro: ${s.otherDelayDesc.trim()}")
                }
                if (s.locationStatus == "GPS_PENDING") {
                    if (isNotEmpty()) append(" | ")
                    append("GPS pendiente")
                }
            }

            rows += listOf(
                trip.tripId,
                trip.routeNumber ?: "",
                trip.routeName,
                trip.company ?: "",
                fecha,
                trip.esFs ?: "",
                trip.direction,
                trip.baseStart ?: "",
                trip.baseEnd ?: "",
                horaInicio,
                horaFin,
                tiempoRecorrido,
                trip.plateNumber ?: "",
                trip.vehicleEco ?: "",
                trip.vehicleType ?: "",
                trip.seatCapacity ?: "",
                if (s.waypointStopId != 0) s.waypointStopId else "",
                if (s.waypointStartId != 0) s.waypointStartId else "",
                coordParada,
                coordArranque,
                fmtTime(stopEpoch),
                fmtTime(startEpoch),
                fmtDurationMs(delayDurMs),
                menUp,
                womenUp,
                menDown,
                womenDown,
                totalSuben,
                totalBajan,
                totalAbordoAcc,
                s.delayCodes ?: "",
                if (s.hasLuggage) 1 else 0,
                obs,
                s.locationStatus,
                if (s.stopAccM > 0.0) s.stopAccM else "",
                s.stopProvider,
                if (s.stopFixTime > 0L) fmtDateTime(s.stopFixTime) else ""
            )
        }

        writeCsvUtf8Bom(context, uri, headers, rows)
    }

    suspend fun exportTrackPointsCsv(
        context: Context,
        uri: Uri,
        points: List<TrackPoint>
    ) {
        val ordered = points.sortedBy { it.timeMs }
        val headers = listOf(
            "tripId",
            "timeMs",
            "fecha_hora",
            "delta_seg",
            "lat",
            "lon",
            "accM",
            "gps_quality",
            "provider"
        )
        val rows = ordered.mapIndexed { index, p ->
            val previous = ordered.getOrNull(index - 1)
            val deltaSec = previous?.let { ((p.timeMs - it.timeMs).coerceAtLeast(0L) / 1000.0) } ?: 0.0
            listOf(
                p.tripId,
                p.timeMs,
                fmtDateTime(p.timeMs),
                deltaSec,
                p.lat,
                p.lon,
                p.accM,
                gpsQuality(p.accM),
                p.provider
            )
        }
        writeCsvUtf8Bom(context, uri, headers, rows)
    }

    private fun gpsQuality(accM: Double): String = when {
        accM <= 10.0 -> "OK"
        accM <= 25.0 -> "USABLE"
        else -> "MALO"
    }

    suspend fun exportCcLayout(
        context: Context,
        uri: Uri,
        session: CcSession,
        events: List<CcEvent>
    ) {
        val headers = listOf(
            "Consecutivo", "ID", "Ubicación", "Aforador", "Fecha", "Base", "Terminal Origen",
            "Terminal Destino", "Sentido", "Nombre de la Empresa", "Derrotero", "Llegada/Salida",
            "Hora", "No. Placa", "Eco.", "Tipo de Vehículo", "Pasajeros", "Ocupan Maletero",
            "Observaciones", "Lat", "Lon", "Accuracy(m)", "GPS Quality", "Provider", "FixTime",
            "LocationStatus", "HoraManual"
        )

        val rows = events.map { e ->
            listOf(
                e.seqInSession,
                session.planningId,
                session.locationName,
                session.aforador,
                fmtDate(session.dateDayMs),
                session.base,
                session.terminalOrigin,
                session.terminalDestination,
                session.direction,
                session.companyName,
                session.derrotero,
                e.eventType,
                fmtTime(e.timeMs),
                e.plate ?: "",
                e.eco ?: "",
                e.vehicleType ?: "",
                e.pax,
                e.luggageCount ?: "",
                e.notes ?: "",
                e.lat,
                e.lon,
                e.accM,
                gpsQuality(e.accM),
                e.provider,
                fmtDateTime(e.fixTime),
                e.locationStatus,
                if (e.timeIsManual) "Y" else "N"
            )
        }

        writeCsvUtf8Bom(context, uri, headers, rows)
    }
}
