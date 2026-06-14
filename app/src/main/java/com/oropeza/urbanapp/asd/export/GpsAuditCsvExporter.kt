package com.oropeza.urbanapp.asd.export

import android.content.Context
import android.net.Uri
import com.oropeza.urbanapp.asd.data.local.TrackPoint
import com.oropeza.urbanapp.asd.location.GpsAuditDiagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object GpsAuditCsvExporter {
    private const val GAP_THRESHOLD_SEC = 10.0
    private val dtf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("es", "MX"))

    suspend fun export(context: Context, uri: Uri, points: List<TrackPoint>) = withContext(Dispatchers.IO) {
        val ordered = points.sortedBy { it.timeMs }
        val headers = listOf(
            "trip_id",
            "time_ms",
            "fecha_hora",
            "segment_id",
            "is_segment_start",
            "is_time_gap",
            "delta_seg",
            "dist_prev_m",
            "lat",
            "lon",
            "accuracy_m",
            "provider_base",
            "gps_filter",
            "gps_mode",
            "gps_arm_state",
            "gps_quality",
            "gps_decision",
            "is_kalman",
            "is_still_lock",
            "provider_raw"
        )
        var segmentId = 1
        val rows = ordered.mapIndexed { index, p ->
            val previous = ordered.getOrNull(index - 1)
            val deltaSec = previous?.let { ((p.timeMs - it.timeMs).coerceAtLeast(0L) / 1000.0) } ?: 0.0
            val isTimeGap = index > 0 && deltaSec > GAP_THRESHOLD_SEC
            if (isTimeGap) segmentId += 1
            val isSegmentStart = index == 0 || isTimeGap
            val distPrevM = if (isSegmentStart) 0.0 else previous?.let { haversineMeters(it.lat, it.lon, p.lat, p.lon) } ?: 0.0
            val diag = GpsAuditDiagnostics.parse(p.provider)
            listOf(
                p.tripId,
                p.timeMs,
                dtf.format(Date(p.timeMs)),
                segmentId,
                isSegmentStart,
                isTimeGap,
                String.format(Locale.US, "%.2f", deltaSec),
                String.format(Locale.US, "%.2f", distPrevM),
                p.lat,
                p.lon,
                p.accM,
                diag.provider,
                diag.filter,
                diag.mode,
                diag.armState,
                diag.quality,
                diag.decision,
                diag.isKalman,
                diag.isStillLock,
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

    private fun escape(value: Any?): String {
        val s = value?.toString() ?: ""
        return if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            "\"${s.replace("\"", "\"\"")}\""
        } else s
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
