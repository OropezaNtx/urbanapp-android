package com.oropeza.urbanapp.asd.ui.viewmodel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oropeza.urbanapp.asd.AsdGraph
import com.oropeza.urbanapp.asd.data.local.TrackPoint
import com.oropeza.urbanapp.core.map.UrbanMapPoint
import com.oropeza.urbanapp.core.map.UrbanMapScreen
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class AsdMapVm : ViewModel() {
    fun tripFlow(tripId: Long) = AsdGraph.repo.tripFlow(tripId)
    fun trackPointsFlow(tripId: Long) = AsdGraph.repo.trackPointsFlow(tripId)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AsdMapScreen(
    tripId: Long,
    onBack: () -> Unit,
    vm: AsdMapVm = viewModel()
) {
    val trip by vm.tripFlow(tripId).collectAsState(initial = null)
    val trackPoints by vm.trackPointsFlow(tripId).collectAsState(initial = emptyList())

    val validTrackPoints = remember(trackPoints) {
        trackPoints
            .filter { it.lat != 0.0 && it.lon != 0.0 }
            .sortedBy { it.timeMs }
    }

    val points = remember(validTrackPoints) {
        validTrackPoints.map { point -> point.toUrbanMapPoint() }
    }

    val metrics = remember(validTrackPoints) {
        AsdMapMetrics.from(validTrackPoints)
    }

    val startPoint = remember(points) {
        points.firstOrNull()?.copy(title = "Inicio ASD")
    }

    val endPoint = remember(points) {
        points.lastOrNull()?.copy(title = "Ultimo punto ASD")?.takeIf { points.size >= 2 }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(trip?.routeName ?: "Mapa ASD") },
                navigationIcon = { IconButton(onClick = onBack) { Text("←") } }
            )
        }
    ) { pad ->
        Box(Modifier.padding(pad)) {
            UrbanMapScreen(
                points = points,
                startPoint = startPoint,
                endPoint = endPoint,
                showPointMarkers = false,
                emptyMessage = "Este viaje aun no tiene trackpoints con GPS valido."
            )

            Card(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                Column(Modifier.padding(10.dp)) {
                    Text("Resumen ASD", style = MaterialTheme.typography.titleSmall)
                    Text("Puntos: ${metrics.pointCount}", style = MaterialTheme.typography.bodySmall)
                    Text("Distancia: ${metrics.distanceText}", style = MaterialTheme.typography.bodySmall)
                    Text("Duracion: ${metrics.durationText}", style = MaterialTheme.typography.bodySmall)
                    Text("Precision prom: ${metrics.accuracyText}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun TrackPoint.toUrbanMapPoint(): UrbanMapPoint {
    return UrbanMapPoint(
        id = "track-$id",
        title = "Track GPS",
        subtitle = "$provider | acc ${accM}m",
        lat = lat,
        lon = lon,
        accuracyM = accM,
        status = provider,
        module = "ASD_TRACK",
        timestampMs = timeMs,
        metadata = mapOf("tripId" to tripId.toString())
    )
}

private data class AsdMapMetrics(
    val pointCount: Int,
    val distanceText: String,
    val durationText: String,
    val accuracyText: String
) {
    companion object {
        fun from(points: List<TrackPoint>): AsdMapMetrics {
            val distanceM = points.zipWithNext().sumOf { (a, b) ->
                haversineMeters(a.lat, a.lon, b.lat, b.lon)
            }
            val durationMs = if (points.size >= 2) {
                (points.last().timeMs - points.first().timeMs).coerceAtLeast(0L)
            } else {
                0L
            }
            val avgAcc = points.map { it.accM }.filter { it > 0.0 && it < 9999.0 }.averageOrNull()
            return AsdMapMetrics(
                pointCount = points.size,
                distanceText = distanceText(distanceM),
                durationText = durationText(durationMs),
                accuracyText = avgAcc?.let { "${it.roundToInt()}m" } ?: "-"
            )
        }

        private fun distanceText(distanceM: Double): String {
            return when {
                distanceM <= 0.0 -> "-"
                distanceM < 1000.0 -> "${distanceM.roundToInt()}m"
                else -> String.format(Locale("es", "MX"), "%.2fkm", distanceM / 1000.0)
            }
        }

        private fun durationText(durationMs: Long): String {
            if (durationMs <= 0L) return "-"
            val totalSec = durationMs / 1000L
            val h = totalSec / 3600L
            val m = (totalSec % 3600L) / 60L
            val s = totalSec % 60L
            return if (h > 0L) "${h}h ${m}m" else "${m}m ${s}s"
        }

        private fun List<Double>.averageOrNull(): Double? {
            return if (isEmpty()) null else average()
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
}
