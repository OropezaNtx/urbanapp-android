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
import org.apache.poi.ss.usermodel.Workbook
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
        createResumenSheet(wb, styles, trip, events, trackPoints)
        createEventosSheet(wb, styles, events)
        createDemorasSheet(wb, styles, events)
        createRecorridoSheet(wb, styles, trackPoints)
        val os = context.contentResolver.openOutputStream(uri)
        requireNotNull(os) { "No se pudo abrir OutputStream para: $uri" }
        os.use { wb.write(it) }
        wb.close()
    }

    private fun createResumenSheet(wb: Workbook, styles: Styles, trip: Trip, events: List<StopEvent>, trackPoints: List<TrackPoint>) {
        val sheet = wb.createSheet("Resumen")
        var r = 0
        sheet.createRow(r++).apply {
            createCell(0).setCellValue("URBAN APP - REPORTE ASD")
            getCell(0).cellStyle = styles.title
        }
        r++
        fun item(label: String, value: String) {
            val row = sheet.createRow(r++)
            row.createCell(0).setCellValue(label)
            row.createCell(1).setCellValue(value)
            row.getCell(0).cellStyle = styles.header
            row.getCell(1).cellStyle = styles.value
        }
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
        item("Eventos", events.size.toString())
        item("Ascensos", events.sumOf { it.paxMenUp + it.paxWomenUp }.toString())
        item("Descensos", events.sumOf { it.paxMenDown + it.paxWomenDown }.toString())
        item("Demoras", events.count { it.hasDelay() }.toString())
        item("Puntos GPS", trackPoints.size.toString())
        item("Distancia GPS", formatDistance(distanceMeters(trackPoints)))
        item("Precisión promedio", avgAccuracyText(trackPoints))
        item("Observaciones", trip.notes.orDash())
        sheet.setColumnWidth(0, 24 * 256)
        sheet.setColumnWidth(1, 42 * 256)
    }

    private fun createEventosSheet(wb: Workbook, styles: Styles, events: List<StopEvent>) {
        val sheet = wb.createSheet("Eventos ASD")
        val headers = listOf("Fecha/hora", "Hora", "Tipo", "Parada", "H suben", "M suben", "H bajan", "M bajan", "Suben", "Bajan", "A bordo", "Demoras", "Observaciones", "GPS")
        writeHeader(sheet, 0, headers, styles)
        var onboard = 0
        events.sortedBy { it.timestamp }.forEachIndexed { idx, e ->
            val up = e.paxMenUp + e.paxWomenUp
            val down = e.paxMenDown + e.paxWomenDown
            onboard = (onboard + up - down).coerceAtLeast(0)
            val row = sheet.createRow(idx + 1)
            val values = listOf(
                dtf.format(Date(e.timestamp)),
                tf.format(Date(e.timestamp)),
                e.stopType,
                e.stopName.orDash(),
                e.paxMenUp,
                e.paxWomenUp,
                e.paxMenDown,
                e.paxWomenDown,
                up,
                down,
                onboard,
                e.delayCodes.orDash(),
                e.notes.orDash(),
                simpleGps(e.stopLat, e.stopLon, e.stopAccM)
            )
            values.forEachIndexed { c, v -> row.createCell(c).setCellValue(v.toString()); row.getCell(c).cellStyle = styles.value }
        }
        autosize(sheet, headers.size)
    }

    private fun createDemorasSheet(wb: Workbook, styles: Styles, events: List<StopEvent>) {
        val sheet = wb.createSheet("Demoras")
        val headers = listOf("Fecha/hora", "Código", "Descripción", "Parada", "Notas", "GPS")
        writeHeader(sheet, 0, headers, styles)
        var r = 1
        events.sortedBy { it.timestamp }.filter { it.hasDelay() }.forEach { e ->
            val codes = e.delayCodes?.split("/")?.filter { it.isNotBlank() } ?: listOf("DEMORA")
            codes.forEach { code ->
                val row = sheet.createRow(r++)
                val values = listOf(
                    dtf.format(Date(e.timestamp)),
                    code,
                    if (code == "O") e.otherDelayDesc.orDash() else delayLabel(code),
                    e.stopName.orDash(),
                    e.notes.orDash(),
                    simpleGps(e.stopLat, e.stopLon, e.stopAccM)
                )
                values.forEachIndexed { c, v -> row.createCell(c).setCellValue(v); row.getCell(c).cellStyle = styles.value }
            }
        }
        autosize(sheet, headers.size)
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
        autosize(sheet, headers.size)
    }

    private fun writeHeader(sheet: org.apache.poi.ss.usermodel.Sheet, rowIndex: Int, headers: List<String>, styles: Styles) {
        val row = sheet.createRow(rowIndex)
        headers.forEachIndexed { c, h -> row.createCell(c).setCellValue(h); row.getCell(c).cellStyle = styles.header }
    }

    private fun autosize(sheet: org.apache.poi.ss.usermodel.Sheet, cols: Int) {
        for (i in 0 until cols) {
            sheet.autoSizeColumn(i)
            val width = sheet.getColumnWidth(i).coerceAtMost(42 * 256).coerceAtLeast(12 * 256)
            sheet.setColumnWidth(i, width)
        }
    }

    private class Styles(wb: Workbook) {
        val title: CellStyle = wb.createCellStyle().apply {
            alignment = HorizontalAlignment.CENTER
            val f = wb.createFont(); f.bold = true; f.fontHeightInPoints = 16; setFont(f)
        }
        val header: CellStyle = wb.createCellStyle().apply {
            fillForegroundColor = IndexedColors.DARK_BLUE.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            val f = wb.createFont(); f.bold = true; f.color = IndexedColors.WHITE.index; setFont(f)
            borderBottom = BorderStyle.THIN
        }
        val value: CellStyle = wb.createCellStyle().apply { borderBottom = BorderStyle.HAIR }
    }

    private fun StopEvent.hasDelay(): Boolean = !delayCodes.isNullOrBlank() || stopType.uppercase(Locale("es", "MX")) in setOf("DEMORA", "BANDERA", "DELAY")
    private fun String?.orDash(): String = this?.takeIf { it.isNotBlank() } ?: "-"
    private fun simpleGps(lat: Double, lon: Double, acc: Double): String = if (lat != 0.0 || lon != 0.0) "${"%.6f".format(Locale.US, lat)}, ${"%.6f".format(Locale.US, lon)} ±${acc.roundToInt()}m" else "GPS pendiente"
    private fun avgAccuracyText(points: List<TrackPoint>): String = points.map { it.accM }.filter { it > 0.0 && it < 9999.0 }.average().takeIf { !it.isNaN() }?.let { "±${it.roundToInt()}m" } ?: "-"
    private fun formatDistance(m: Double): String = if (m < 1000.0) "${m.roundToInt()} m" else "${"%.2f".format(Locale.US, m / 1000.0)} km"
    private fun delayLabel(code: String): String = when (code) {
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
