package com.oropeza.urbanapp.asd.location

import com.oropeza.urbanapp.asd.data.local.TrackPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class GpsAuditDiagnostics(
    val provider: String,
    val filter: String,
    val mode: String,
    val armState: String,
    val quality: String,
    val decision: String,
    val isKalman: Boolean,
    val isStillLock: Boolean
) {
    companion object {
        fun parse(providerText: String?): GpsAuditDiagnostics {
            val parts = providerText.orEmpty().split("+").map { it.trim() }.filter { it.isNotEmpty() }
            val provider = parts.getOrNull(0).orEmpty().ifBlank { "unknown" }
            val filter = parts.firstOrNull { it.equals("kalman", true) || it.equals("raw", true) }.orEmpty().ifBlank { "unknown" }
            val mode = parts.firstOrNull { it in setOf("ACQUIRE", "TRACK", "STILL") }.orEmpty().ifBlank { "UNKNOWN" }
            val armState = parts.firstOrNull { it in setOf("ARMED", "QUICK") }.orEmpty().ifBlank { "UNKNOWN" }
            val quality = parts.firstOrNull { it in setOf("EXCELLENT", "GOOD", "USABLE", "POOR", "INVALID") }.orEmpty().ifBlank { "UNKNOWN" }
            val decision = parts.firstOrNull { it in setOf("ACCEPT", "SMOOTH", "HOLD", "REJECT", "STILL_LOCK") }.orEmpty().ifBlank { "UNKNOWN" }
            return GpsAuditDiagnostics(
                provider = provider,
                filter = filter,
                mode = mode,
                armState = armState,
                quality = quality,
                decision = decision,
                isKalman = filter.equals("kalman", true),
                isStillLock = decision.equals("STILL_LOCK", true)
            )
        }
    }
}

data class GpsAuditSummary(
    val totalPoints: Int,
    val excellentPoints: Int,
    val goodPoints: Int,
    val usablePoints: Int,
    val poorPoints: Int,
    val acceptedPoints: Int,
    val smoothedPoints: Int,
    val stillLockPoints: Int,
    val kalmanPoints: Int,
    val trackPoints: Int,
    val stillPoints: Int,
    val avgAccuracyM: Double?,
    val bestAccuracyM: Double?,
    val worstAccuracyM: Double?,
    val totalDistanceM: Double,
    val durationMs: Long,
    val avgIntervalMs: Long?
) {
    val coverageText: String
        get() = if (totalPoints <= 0) "-" else "${(((excellentPoints + goodPoints + usablePoints).toDouble() / totalPoints) * 100.0).roundToInt()}%"

    companion object {
        fun from(points: List<TrackPoint>): GpsAuditSummary {
            val sorted = points.sortedBy { it.timeMs }
            val diagnostics = sorted.map { GpsAuditDiagnostics.parse(it.provider) }
            val acc = sorted.map { it.accM }.filter { it > 0.0 && it < 9999.0 }
            val intervals = sorted.zipWithNext().map { (a, b) -> (b.timeMs - a.timeMs).coerceAtLeast(0L) }.filter { it > 0L }
            return GpsAuditSummary(
                totalPoints = sorted.size,
                excellentPoints = diagnostics.count { it.quality == "EXCELLENT" },
                goodPoints = diagnostics.count { it.quality == "GOOD" },
                usablePoints = diagnostics.count { it.quality == "USABLE" },
                poorPoints = diagnostics.count { it.quality == "POOR" },
                acceptedPoints = diagnostics.count { it.decision == "ACCEPT" },
                smoothedPoints = diagnostics.count { it.decision == "SMOOTH" },
                stillLockPoints = diagnostics.count { it.decision == "STILL_LOCK" },
                kalmanPoints = diagnostics.count { it.isKalman },
                trackPoints = diagnostics.count { it.mode == "TRACK" },
                stillPoints = diagnostics.count { it.mode == "STILL" },
                avgAccuracyM = acc.averageOrNull(),
                bestAccuracyM = acc.minOrNull(),
                worstAccuracyM = acc.maxOrNull(),
                totalDistanceM = sorted.zipWithNext().sumOf { (a, b) -> haversineMeters(a.lat, a.lon, b.lat, b.lon) },
                durationMs = if (sorted.size >= 2) (sorted.last().timeMs - sorted.first().timeMs).coerceAtLeast(0L) else 0L,
                avgIntervalMs = intervals.averageLongOrNull()
            )
        }

        private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()
        private fun List<Long>.averageLongOrNull(): Long? = if (isEmpty()) null else average().roundToInt().toLong()

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
}
