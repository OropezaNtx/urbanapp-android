package com.oropeza.urbanapp.fov.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.oropeza.urbanapp.asd.data.local.FovObservation
import com.oropeza.urbanapp.asd.data.local.FovSession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun FovSessionSummaryCard(
    session: FovSession,
    observations: List<FovObservation>,
    modifier: Modifier = Modifier
) {
    val summary = remember(session, observations) {
        FovSessionSummary.from(session, observations)
    }

    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Resumen de sesión", style = MaterialTheme.typography.titleMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryItem("Registros", summary.totalObservations.toString(), Modifier.weight(1f))
                SummaryItem("Rutas", summary.uniqueRoutes.toString(), Modifier.weight(1f))
                SummaryItem("GPS válido", summary.validGpsText, Modifier.weight(1f))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryItem("Inicio", summary.firstTime.ifBlank { "—" }, Modifier.weight(1f))
                SummaryItem("Último", summary.lastTime.ifBlank { "—" }, Modifier.weight(1f))
                SummaryItem("Duración", summary.durationText, Modifier.weight(1f))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryItem("Distancia", summary.distanceText, Modifier.weight(1f))
                SummaryItem("Precisión", summary.averageAccuracyText, Modifier.weight(1f))
                SummaryItem("Calidad", summary.qualityLabel, Modifier.weight(1f))
            }

            Text("POI: ${session.poiKey}", style = MaterialTheme.typography.bodySmall)
            Text("Estado: ${summary.sessionStatus}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SummaryItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

private data class FovSessionSummary(
    val totalObservations: Int,
    val uniqueRoutes: Int,
    val validGps: Int,
    val validGpsText: String,
    val firstTime: String,
    val lastTime: String,
    val durationText: String,
    val distanceText: String,
    val averageAccuracyText: String,
    val qualityLabel: String,
    val sessionStatus: String
) {
    companion object {
        private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale("es", "MX"))

        fun from(session: FovSession, observations: List<FovObservation>): FovSessionSummary {
            val sorted = observations.sortedBy { it.timeMs }
            val validGpsObservations = sorted.filter { it.lat != 0.0 && it.lon != 0.0 && it.locationStatus != "NO_FIX" }
            val first = sorted.firstOrNull()?.timeMs
            val last = sorted.lastOrNull()?.timeMs
            val validGps = validGpsObservations.size
            val uniqueRoutes = observations.map { it.routeUid }.distinct().size
            val gpsPercent = if (observations.isNotEmpty()) ((validGps.toDouble() / observations.size) * 100.0).roundToInt() else 0
            val avgAcc = validGpsObservations.map { it.accM }.filter { it > 0.0 && it < 9999.0 }.averageOrNull()
            val distanceM = validGpsObservations.zipWithNext().sumOf { (a, b) ->
                haversineMeters(a.lat, a.lon, b.lat, b.lon)
            }

            return FovSessionSummary(
                totalObservations = observations.size,
                uniqueRoutes = uniqueRoutes,
                validGps = validGps,
                validGpsText = "$validGps ($gpsPercent%)",
                firstTime = first?.let { timeFormat.format(Date(it)) }.orEmpty(),
                lastTime = last?.let { timeFormat.format(Date(it)) }.orEmpty(),
                durationText = durationText(first, last),
                distanceText = distanceText(distanceM),
                averageAccuracyText = avgAcc?.let { "±${it.roundToInt()}m" } ?: "—",
                qualityLabel = qualityLabel(gpsPercent, avgAcc),
                sessionStatus = if (session.endedAt == null) "ABIERTA" else "CERRADA"
            )
        }

        private fun durationText(first: Long?, last: Long?): String {
            if (first == null || last == null || last < first) return "—"
            val minutes = (last - first) / 60_000L
            val hours = minutes / 60L
            val remainingMinutes = minutes % 60L
            return if (hours > 0) "${hours}h ${remainingMinutes}m" else "${remainingMinutes}m"
        }

        private fun distanceText(distanceM: Double): String {
            return when {
                distanceM <= 0.0 -> "—"
                distanceM < 1000.0 -> "${distanceM.roundToInt()}m"
                else -> String.format(Locale("es", "MX"), "%.2fkm", distanceM / 1000.0)
            }
        }

        private fun qualityLabel(gpsPercent: Int, avgAcc: Double?): String {
            return when {
                gpsPercent >= 90 && (avgAcc ?: 9999.0) <= 15.0 -> "Alta"
                gpsPercent >= 70 && (avgAcc ?: 9999.0) <= 30.0 -> "Media"
                gpsPercent > 0 -> "Baja"
                else -> "Sin GPS"
            }
        }

        private fun List<Double>.averageOrNull(): Double? {
            return if (isEmpty()) null else average()
        }

        private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6_371_000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a =
                sin(dLat / 2) * sin(dLat / 2) +
                        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                        sin(dLon / 2) * sin(dLon / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return r * c
        }
    }
}
