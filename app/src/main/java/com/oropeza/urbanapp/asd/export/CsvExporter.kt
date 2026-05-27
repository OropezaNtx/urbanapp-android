package com.oropeza.urbanapp.asd.export

import android.content.Context
import android.net.Uri
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
import kotlin.math.min
import com.oropeza.urbanapp.asd.data.local.CcEvent
import com.oropeza.urbanapp.asd.data.local.CcSession

object CsvExporter {

    // Excel (MX) suele leer mejor con dd/MM/yyyy y HH:mm:ss
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

    private fun overlapMs(aStart: Long, aEnd: Long, bStart: Long, bEnd: Long): Long {
        val s = max(aStart, bStart)
        val e = min(aEnd, bEnd)
        return max(0, e - s)
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

            // BOM UTF-8 para que Excel respete acentos
            os.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))

            os.bufferedWriter(Charsets.UTF_8).use { out ->
                out.appendLine(headers.joinToString(","))
                rows.forEach { r ->
                    out.appendLine(r.joinToString(",") { escape(it) })
                }
            }
        }
    }

    // =============================
    // HELPERS: tiempos robustos
    // =============================
    private const val EPOCH_MIN_REASONABLE = 100_000_000_000L // ~1973. Si es menor, probablemente NO es epoch.

    private fun resolveStopEpoch(s: StopEvent): Long? {
        // stopTime ideal; si no, usa timestamp
        val st = s.stopTime
        return when {
            st >= EPOCH_MIN_REASONABLE -> st
            s.timestamp >= EPOCH_MIN_REASONABLE -> s.timestamp
            else -> null
        }
    }

    private fun resolveStartEpoch(s: StopEvent, stopEpoch: Long?): Long? {
        val st = s.startTime
        return when {
            st >= EPOCH_MIN_REASONABLE -> st
            // si startTime parece "duración" y stopEpoch sí es epoch → sumamos
            (stopEpoch != null && stopEpoch >= EPOCH_MIN_REASONABLE && st in 1..(6 * 60 * 60 * 1000L)) -> stopEpoch + st
            // último recurso: si timestamp es epoch y startTime es 0, usa timestamp (no ideal, pero evita 00:00:00)
            (st == 0L && s.timestamp >= EPOCH_MIN_REASONABLE) -> s.timestamp
            else -> null
        }
    }

    private fun resolveDelayDurationMs(s: StopEvent, stopEpoch: Long?, startEpoch: Long?): Long {
        // Caso normal: ambos epoch
        if (stopEpoch != null && startEpoch != null) return max(0, startEpoch - stopEpoch)

        // Caso legacy: startTime guarda duración
        val st = s.startTime
        if (st in 1..(6 * 60 * 60 * 1000L)) return st

        return 0L
    }

    // =============================
    // EXPORT RAW (respaldo)
    // =============================
    suspend fun exportTripAllInOne(
        context: Context,
        uri: Uri,
        trip: Trip,
        stops: List<StopEvent>,
        delays: List<DelayEvent>
    ) {
        val headers = listOf(
            "record_type",
            "tripId",
            "routeName",
            "company",
            "vehicleEco",
            "direction",
            "startTime",
            "endTime",
            "eventId",
            "timestamp",
            "stopType",
            "count",
            "stopName",
            "delayId",
            "delayType",
            "delayStart",
            "delayEnd",
            "delayDurationSec",
            "notes"
        )

        val rows = mutableListOf<List<Any?>>()

        rows += listOf(
            "TRIP",
            trip.tripId,
            trip.routeName,
            trip.company,
            trip.vehicleEco,
            trip.direction,
            fmtDateTime(trip.startTime),
            fmtDateTime(trip.endTime),
            null, null, null, null, null,
            null, null, null, null, null,
            trip.notes
        )

        stops.forEach { s ->
            rows += listOf(
                "STOP",
                trip.tripId,
                trip.routeName,
                trip.company,
                trip.vehicleEco,
                trip.direction,
                fmtDateTime(trip.startTime),
                fmtDateTime(trip.endTime),
                s.eventId,
                fmtDateTime(s.timestamp),
                s.stopType,
                s.count,
                s.stopName,
                null, null, null, null, null,
                s.notes
            )
        }

        delays.forEach { d ->
            val dur = d.timestampEnd?.let { (it - d.timestampStart) / 1000 } ?: 0
            rows += listOf(
                "DELAY",
                trip.tripId,
                trip.routeName,
                trip.company,
                trip.vehicleEco,
                trip.direction,
                fmtDateTime(trip.startTime),
                fmtDateTime(trip.endTime),
                null, null, null, null, null,
                d.delayId,
                d.delayType,
                fmtDateTime(d.timestampStart),
                fmtDateTime(d.timestampEnd),
                dur,
                d.notes
            )
        }

        writeCsvUtf8Bom(context, uri, headers, rows)
    }

    // ==========================================
    // EXPORT LAYOUT FINAL
    // ==========================================
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
            "Hora de arrranque",
            "Tiempo en demora",
            "Pax. Hombres Suben",
            "Pax. Mujeres Suben",
            "Pax. Hombres bajan",
            "Pax. Mujeres bajan",
            "Total a bordo",
            "Tipo de demora",
            "Porta maleta o bulto voluminoso",
            "Observaciones"
        )

        val rows = mutableListOf<List<Any?>>()

        val fecha = fmtDate(trip.startTime)
        val horaInicio = fmtTime(trip.startTime)
        val endMs = trip.endTime ?: System.currentTimeMillis()
        val horaFin = fmtTime(endMs)
        val tiempoRecorrido = fmtDurationMs(endMs - trip.startTime)

        val stopsOrdered = stops.sortedBy { it.timestamp }

        var totalAbordoAcc = 0

        for (s in stopsOrdered) {
            val menUp = s.paxMenUp
            val womenUp = s.paxWomenUp
            val menDown = s.paxMenDown
            val womenDown = s.paxWomenDown

            val qtyFromSexUp = menUp + womenUp
            val qtyFromSexDown = menDown + womenDown
            val qtyFallback = s.count.coerceAtLeast(0)

            val delta: Int = when (s.stopType.uppercase(Locale("es", "MX"))) {
                "ASCENSO" -> if (qtyFromSexUp > 0) qtyFromSexUp else qtyFallback
                "DESCENSO" -> -(if (qtyFromSexDown > 0) qtyFromSexDown else qtyFallback)
                else -> 0
            }

            totalAbordoAcc += delta
            if (totalAbordoAcc < 0) totalAbordoAcc = 0
            val totalAbordo = totalAbordoAcc

            val coordParada =
                if (s.stopLat != 0.0 || s.stopLon != 0.0) "${s.stopLat},${s.stopLon}" else ""
            val coordArranque =
                if (s.startLat != 0.0 || s.startLon != 0.0) "${s.startLat},${s.startLon}" else ""

            val demoraTipos = s.delayCodes ?: ""

            val obs = buildString {
                if (!s.notes.isNullOrBlank()) append(s.notes.trim())
                if (!s.otherDelayDesc.isNullOrBlank()) {
                    if (isNotEmpty()) append(" | ")
                    append("Otro: ${s.otherDelayDesc.trim()}")
                }
            }.ifBlank { "" }

            // ✅ tiempos robustos (soporta datos legacy)
            val stopEpoch = resolveStopEpoch(s)
            val startEpoch = resolveStartEpoch(s, stopEpoch)
            val delayDurMs = resolveDelayDurationMs(s, stopEpoch, startEpoch)

            rows += listOf(
                trip.tripId,                    // ID
                trip.routeNumber ?: "",         // No. Recorrido
                trip.routeName,                 // Ruta / Derrotero
                trip.company ?: "",             // Empresa
                fecha,                          // Fecha
                trip.esFs ?: "",                // ES / FS
                trip.direction,                 // Sentido
                trip.baseStart ?: "",           // Base de inicio
                trip.baseEnd ?: "",             // Base final
                horaInicio,                     // Hora de inicio
                horaFin,                        // Hora final
                tiempoRecorrido,                // Tiempo en recorrido
                trip.plateNumber ?: "",         // No. Placa
                trip.vehicleEco ?: "",          // No. Económico
                trip.vehicleType ?: "",         // Tipo de vehículo
                trip.seatCapacity ?: "",        // Capacidad de asientos
                if (s.waypointStopId != 0) s.waypointStopId else "",      // Waypoint de parada
                if (s.waypointStartId != 0) s.waypointStartId else "",    // Waypoint de arranque
                coordParada,                    // Coordenada de parada
                coordArranque,                  // Coordenada de arranque
                fmtTime(stopEpoch),             // Hora de parada
                fmtTime(startEpoch),            // Hora de arrranque
                fmtDurationMs(delayDurMs),      // Tiempo en demora
                menUp,                          // Pax. Hombres Suben
                womenUp,                        // Pax. Mujeres Suben
                menDown,                        // Pax. Hombres bajan
                womenDown,                      // Pax. Mujeres bajan
                totalAbordo,                    // Total a bordo
                demoraTipos,                    // Tipo de demora
                if (s.hasLuggage) 1 else 0,     // Porta maleta
                obs                              // Observaciones
            )
        }

        writeCsvUtf8Bom(context, uri, headers, rows)
    }

    // ==========================================
    // ✅ EXPORT TRACKPOINTS (TRACK CONTINUO)
    // ==========================================
    suspend fun exportTrackPointsCsv(
        context: Context,
        uri: Uri,
        points: List<TrackPoint>
    ) {
        val headers = listOf("tripId", "timeMs", "lat", "lon", "accM", "provider")

        val rows = points.map { p ->
            listOf(
                p.tripId,
                p.timeMs,
                p.lat,
                p.lon,
                p.accM,
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
            "Consecutivo",
            "ID",
            "Ubicación",
            "Aforador",
            "Fecha",
            "Base",
            "Terminal Origen",
            "Terminal Destino",
            "Sentido",
            "Nombre de la Empresa",
            "Derrotero",
            "Llegada/Salida",
            "Hora",
            "No. Placa",
            "Eco.",
            "Tipo de Vehículo",
            "Pasajeros",
            "Ocupan Maletero",
            "Observaciones",
            "Lat",
            "Lon",
            "Accuracy(m)",
            "GPS Quality",
            "Provider",
            "FixTime",
            "LocationStatus",
            "HoraManual"
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

