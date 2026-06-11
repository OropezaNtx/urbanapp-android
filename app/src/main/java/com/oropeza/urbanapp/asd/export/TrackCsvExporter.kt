package com.oropeza.urbanapp.asd.export

import android.content.Context
import android.net.Uri
import com.oropeza.urbanapp.asd.data.local.TrackPoint
import com.oropeza.urbanapp.asd.location.GpsProviderDiagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TrackCsvExporter {

    private val dtf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("es", "MX"))

    private fun fmtDateTime(ms: Long): String = dtf.format(Date(ms))

    private fun escape(v: Any?): String {
        val s = v?.toString() ?: ""
        return if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            "\"${s.replace("\"", "\"\"")}\""
        } else s
    }

    suspend fun exportTrackPointsCsv(
        context: Context,
        uri: Uri,
        points: List<TrackPoint>
    ) = withContext(Dispatchers.IO) {
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
            "gps_mode",
            "gps_filter",
            "gps_state",
            "provider_raw",
            "provider"
        )

        val rows = ordered.mapIndexed { index, p ->
            val previous = ordered.getOrNull(index - 1)
            val deltaSec = previous?.let { ((p.timeMs - it.timeMs).coerceAtLeast(0L) / 1000.0) } ?: 0.0
            val diag = GpsProviderDiagnostics.parse(p.provider)

            listOf(
                p.tripId,
                p.timeMs,
                fmtDateTime(p.timeMs),
                deltaSec,
                p.lat,
                p.lon,
                p.accM,
                diag.quality.ifBlank { qualityFromAccuracy(p.accM) },
                diag.mode,
                diag.filter,
                diag.state,
                diag.providerRaw,
                p.provider
            )
        }

        val os = context.contentResolver.openOutputStream(uri)
        requireNotNull(os) { "No se pudo abrir OutputStream para: $uri" }
        os.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
        os.bufferedWriter(Charsets.UTF_8).use { out ->
            out.appendLine(headers.joinToString(","))
            rows.forEach { row -> out.appendLine(row.joinToString(",") { escape(it) }) }
        }
    }

    private fun qualityFromAccuracy(accM: Double): String = when {
        accM <= 10.0 -> "EXCELLENT"
        accM <= 25.0 -> "GOOD"
        accM <= 45.0 -> "USABLE"
        else -> "POOR"
    }
}
