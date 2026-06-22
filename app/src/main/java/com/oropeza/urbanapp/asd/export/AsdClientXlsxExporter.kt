package com.oropeza.urbanapp.asd.export

import android.content.Context
import android.net.Uri
import com.oropeza.urbanapp.asd.data.local.StopEvent
import com.oropeza.urbanapp.asd.data.local.TrackPoint
import com.oropeza.urbanapp.asd.data.local.Trip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.BorderStyle
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object AsdClientXlsxExporter {
    private val dtf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("es", "MX"))
    private val tf = SimpleDateFormat("HH:mm:ss", Locale("es", "MX"))

    suspend fun export(
        context: Context,
        uri: Uri,
        trip: Trip,
        events: List<StopEvent>,
        trackPoints: List<TrackPoint>
    ) = withContext(Dispatchers.IO) {
        val wb = XSSFWorkbook()
        val styles = Styles(wb)
        val orderedEvents = events.sortedBy { it.timestamp }
        val displayWaypoints = buildDisplayWaypoints(orderedEvents)
        createResumenSheet(wb, styles, trip, orderedEvents, trackPoints)
        createEventosSheet(wb, styles, orderedEvents, displayWaypoints)
        createDemorasSheet(wb, styles, orderedEvents, displayWaypoints)
        createRecorridoSheet(wb, styles, trackPoints)
        val os = context.contentResolver.openOutputStream(uri)
        requireNotNull(os) { "No se pudo abrir OutputStream para: $uri" }
        os.use { wb.write(it) }
        wb.close()
    }

    private fun createResumenSheet(wb: Workbook, styles: Styles, trip: Trip, events: List<StopEvent>, trackPoints: List<TrackPoint>) {
        val sheet = wb.createSheet("Resumen")
        sheet.addMergedRegion(CellRangeAddress(0, 0, 0, 3))
        sheet.addMergedRegion(CellRangeAddress(1, 1, 0, 3))
        sheet.createRow(0).apply {
            createCell(0).setCellValue("URBAN APP")
            getCell(0).cellStyle = styles.brand
        }
        sheet.createRow(1).apply {
            createCell(0).setCellValue("REPORTE PROFESIONAL ASD")
            getCell(0).cellStyle = styles.title
        }
        var r = 3
        fun section(title: String) {
            val row = sheet.createRow(r++)
            row.createCell(0).setCellValue(title)
            row.getCell(0).cellStyle = styles.section
        }
        fun item(label: String, value: String) {
            val row = sheet.createRow(r++)
            row.createCell(0).setCellValue(label)
            row.createCell(1).setCellValue(value)
            row.getCell(0).cellStyle = styles.header
            row.getCell(1).cellStyle = styles.value
        }
        val boardings = events.sumOf { it.paxMenUp + it.paxWomenUp }
        val alightings = events.sumOf { it.paxMenDown + it.paxWomenDown }
        val delays = events.count { it.hasDelay() }
        section("Datos del recorrido")
        item("Ruta", trip.routeName)
        item("Empresa", trip.company.orDash())
        item("Sentido", trip.direction)
        item("Aforador", trip.aforador.orDash())
        item("Supervisor", trip.supervisor.orDash())
        item("Dispositivo", trip.deviceNumber.orDash())
        item("Unidad", "ECO ${trip.vehicleEco.orDash()} / Placa ${trip.plateNumber.orDash()}")
        item("Inicio", dtf.format(Date(trip.startTime)))
        item("Fin", trip.endTime?.let { dtf.format(Date(it)) } ?: "EN CURSO")
        item("Capacidad", trip.seatCapacity?.toString() ?: "-")
        r++
        section("Resumen ejecutivo")
        item("Eventos registrados", events.size.toString())
        item("Ascensos", boardings.toString())
        item("Descensos", alightings.toString())
        item("Demoras", delays.toString())
        item("Demanda neta", (boardings - alightings).coerceAtLeast(0).toString())
        item("Distancia GPS", formatDistance(distanceMeters(trackPoints)))
        item("Puntos GPS", trackPoints.size.toString())
        item("Precisión promedio", avgAccuracyText(trackPoints))
        item("Calidad GPS", gpsClientQuality(trackPoints))
        item("Observaciones", trip.notes.orDash())
        applyColumnWidths(sheet, intArrayOf(28, 52, 18, 18))
    }

    private fun createEventosSheet(wb: Workbook, styles: Styles, events: List<StopEvent>, displayWaypoints: Map<Long, DisplayWaypoint>) {
        val sheet = wb.createSheet("Eventos ASD")
        val headers = listOf(
            "Fecha/hora", "Hora", "Tipo", "Parada", "WP parada", "WP arranque",
            "H suben", "M suben", "H bajan", "M bajan", "Suben", "Bajan", "A bordo",
            "Demoras", "Observaciones", "GPS parada", "GPS arranque"
        )
        writeHeader(sheet, 0, headers, styles)
        var onboard = 0
        events.forEachIndexed { idx, e ->
            val up = e.paxMenUp + e.paxWomenUp
            val down = e.paxMenDown + e.paxWomenDown
            onboard = (onboard + up - down).coerceAtLeast(0)
            val wp = displayWaypoints[e.eventId]
            val row = sheet.createRow(idx + 1)
            
            val obsText = if (e.locationStatus == "GPS_PENDING") {
                val baseObs = e.notes.orEmpty()
                if (baseObs.contains("SIN GPS")) baseObs else "$baseObs [SIN GPS DISPONIBLE EN EL MOMENTO DEL REGISTRO]".trim()
            } else {
                e.notes.orDash()
            }

            val values = listOf(
                dtf.format(Date(e.timestamp)),
                tf.format(Date(e.timestamp)),
                clientEventType(e),
                e.stopName.orDash(),
                wp?.stopId ?: "",
                wp?.startId ?: "",
                e.paxMenUp,
                e.paxWomenUp,
                e.paxMenDown,
                e.paxWomenDown,
                up,
                down,
                onboard,
                e.delayCodes.orDash(),
                obsText,
                simpleGps(e.stopLat, e.stopLon, e.stopAccM, e.locationStatus),
                simpleGps(e.startLat, e.startLon, e.startAccM, e.locationStatus)
            )
            values.forEachIndexed { c, v -> row.createCell(c).setCellValue(v.toString()); row.getCell(c).cellStyle = styles.value }
        }
        applyColumnWidths(sheet, intArrayOf(20, 12, 16, 24, 12, 13, 10, 10, 10, 10, 10, 10, 10, 16, 26, 30, 30))
    }

    private fun createDemorasSheet(wb: Workbook, styles: Styles, events: List<StopEvent>, displayWaypoints: Map<Long, DisplayWaypoint>) {
        val sheet = wb.createSheet("Demoras")
        val headers = listOf("Fecha/hora", "WP", "Código", "Descripción", "Parada", "Notas", "GPS")
        writeHeader(sheet, 0, headers, styles)
        var r = 1
        events.filter { it.hasDelay() }.forEach { e ->
            val codes = e.delayCodes?.split("/")?.filter { it.isNotBlank() && it != "AD" } ?: listOf("DEMORA")
            codes.forEach { code ->
                val row = sheet.createRow(r++)
                val values = listOf(
                    dtf.format(Date(e.timestamp)),
                    displayWaypoints[e.eventId]?.stopId?.toString() ?: "",
                    code,
                    if (code == "O") e.otherDelayDesc.orDash() else delayLabel(code),
                    e.stopName.orDash(),
                    e.notes.orDash(),
                    simpleGps(e.stopLat, e.stopLon, e.stopAccM)
                )
                values.forEachIndexed { c, v -> row.createCell(c).setCellValue(v); row.getCell(c).cellStyle = styles.value }
            }
        }
        applyColumnWidths(sheet, intArrayOf(20, 10, 12, 24, 24, 30, 30))
    }

    private fun createRecorridoSheet(wb: Workbook, styles: Styles, points: List<TrackPoint>) {
        val sheet = wb.createSheet("Recorrido GPS")
        val headers = listOf("Fecha/hora", "Latitud", "Longitud", "Precisión m")
        writeHeader(sheet, 0, headers, styles)
        points.sortedBy { it.timeMs }.forEachIndexed { idx, p ->
            val row = sheet.createRow(idx + 1)
            val values = listOf(dtf.format(Date(p.timeMs)), p.lat, p.lon, p.accM)
            values.forEachIndexed { c, v -> row.createCell(c).setCellValue(v.toString()); row.getCell(c).cellStyle = styles.value }
        }
        applyColumnWidths(sheet, intArrayOf(20, 18, 18, 14))
    }

    private fun writeHeader(sheet: Sheet, rowIndex: Int, headers: List<String>, styles: Styles) {
        val row = sheet.createRow(rowIndex)
        headers.forEachIndexed { c, h -> row.createCell(c).setCellValue(h); row.getCell(c).cellStyle = styles.header }
        sheet.createFreezePane(0, rowIndex + 1)
    }

    private fun applyColumnWidths(sheet: Sheet, widths: IntArray) {
        widths.forEachIndexed { index, chars -> sheet.setColumnWidth(index, chars.coerceIn(8, 60) * 256) }
    }

    private class Styles(wb: Workbook) {
        val brand: CellStyle = wb.createCellStyle().apply {
            alignment = HorizontalAlignment.CENTER
            fillForegroundColor = IndexedColors.DARK_BLUE.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            val f = wb.createFont(); f.bold = true; f.color = IndexedColors.WHITE.index; f.fontHeightInPoints = 20; setFont(f)
        }
        val title: CellStyle = wb.createCellStyle().apply {
            alignment = HorizontalAlignment.CENTER
            val f = wb.createFont(); f.bold = true; f.fontHeightInPoints = 15; setFont(f)
        }
        val section: CellStyle = wb.createCellStyle().apply {
            fillForegroundColor = IndexedColors.GREY_25_PERCENT.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            val f = wb.createFont(); f.bold = true; f.fontHeightInPoints = 12; setFont(f)
        }
        val header: CellStyle = wb.createCellStyle().apply {
            fillForegroundColor = IndexedColors.DARK_BLUE.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            val f = wb.createFont(); f.bold = true; f.color = IndexedColors.WHITE.index; setFont(f)
            borderBottom = BorderStyle.THIN
        }
        val value: CellStyle = wb.createCellStyle().apply { borderBottom = BorderStyle.HAIR }
    }

    private data class DisplayWaypoint(val stopId: Int, val startId: Int)

    private fun buildDisplayWaypoints(events: List<StopEvent>): Map<Long, DisplayWaypoint> {
        var next = 1
        return events.associate { e ->
            val boundary = e.isTripBoundaryFlag()
            val stopId = next
            val startId = if (boundary) stopId else stopId + 1
            next += if (boundary) 1 else 2
            e.eventId to DisplayWaypoint(stopId, startId)
        }
    }

    private fun StopEvent.isTripBoundaryFlag(): Boolean {
        if (!stopType.equals("BANDERA", true)) return false
        val combined = listOfNotNull(stopName, delayCodes).joinToString("/").uppercase(Locale("es", "MX"))
        return combined.contains("AD/INICIO") || combined.contains("AD/FINAL")
    }

    private fun StopEvent.hasDelay(): Boolean {
        val type = stopType.uppercase(Locale("es", "MX"))
        val codes = delayCodes.orEmpty().uppercase(Locale("es", "MX"))
        return !delayCodes.isNullOrBlank() || type in setOf("DEMORA", "BANDERA", "DELAY") || codes.contains("CONG")
    }

    private fun clientEventType(e: StopEvent): String {
        if (e.isTripBoundaryFlag()) return e.stopName ?: e.delayCodes ?: "BANDERA"
        val up = e.paxMenUp + e.paxWomenUp
        val down = e.paxMenDown + e.paxWomenDown
        val hasDelay = e.hasDelay()
        return when {
            (up > 0 || down > 0) && hasDelay -> "ASD + DEMORA"
            up > 0 && down > 0 -> "ASD"
            up > 0 -> "ASCENSO"
            down > 0 -> "DESCENSO"
            hasDelay -> "DEMORA"
            else -> e.stopType
        }
    }

    private fun String?.orDash(): String = this?.takeIf { it.isNotBlank() } ?: "-"
    private fun simpleGps(lat: Double, lon: Double, acc: Double, status: String = ""): String {
        if (status == "GPS_PENDING" || (lat == 0.0 && lon == 0.0)) return "GPS PENDIENTE"
        return "${"%.6f".format(Locale.US, lat)}, ${"%.6f".format(Locale.US, lon)} ±${acc.roundToInt()}m"
    }
    private fun avgAccuracyText(points: List<TrackPoint>): String = points.map { it.accM }.filter { it > 0.0 && it < 9999.0 }.average().takeIf { !it.isNaN() }?.let { "±${it.roundToInt()}m" } ?: "-"
    private fun gpsClientQuality(points: List<TrackPoint>): String {
        val avg = points.map { it.accM }.filter { it > 0.0 && it < 9999.0 }.average().takeIf { !it.isNaN() } ?: return "-"
        return when {
            avg <= 8.0 -> "Excelente"
            avg <= 15.0 -> "Buena"
            avg <= 25.0 -> "Usable"
            else -> "Débil"
        }
    }
    private fun formatDistance(m: Double): String = if (m < 1000.0) "${m.roundToInt()} m" else "${"%.2f".format(Locale.US, m / 1000.0)} km"
    private fun delayLabel(code: String): String = when (code) {
        "AD" -> "Ascenso / descenso"
        "C" -> "Congestión"
        "S" -> "Semaforización"
        "TM" -> "Tráfico mixto"
        "CND" -> "Condición de vía"
        "VI" -> "Vuelta izquierda"
        "VD" -> "Vuelta derecha"
        "PP" -> "Pase peatonal"
        "CONG" -> "Congestión"
        else -> code
    }
    private fun distanceMeters(points: List<TrackPoint>): Double {
        val sorted = points.sortedBy { it.timeMs }
        return sorted.zipWithNext().sumOf { (a, b) -> haversineMeters(a.lat, a.lon, b.lat, b.lon) }
    }
    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }
}
