package com.oropeza.urbanapp.asd.ui.viewmodel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oropeza.urbanapp.asd.AsdGraph
import com.oropeza.urbanapp.asd.data.local.StopEvent
import com.oropeza.urbanapp.asd.data.local.TrackPoint
import com.oropeza.urbanapp.asd.location.LatLng
import com.oropeza.urbanapp.asd.location.PolylineSmoother
import com.oropeza.urbanapp.core.map.UrbanMapPoint
import com.oropeza.urbanapp.core.map.UrbanMapScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class AsdMapVm : ViewModel() {
    fun tripFlow(tripId: Long) = AsdGraph.repo.tripFlow(tripId)
    fun trackPointsFlow(tripId: Long) = AsdGraph.repo.trackPointsFlow(tripId)
    fun stopsFlow(tripId: Long) = AsdGraph.repo.stopsFlow(tripId)
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
    val stopEvents by vm.stopsFlow(tripId).collectAsState(initial = emptyList())

    var locateRequestKey by remember { mutableIntStateOf(0) }

    val validTrackPoints = remember(trackPoints) {
        trackPoints
            .filter { it.lat != 0.0 && it.lon != 0.0 }
            .sortedBy { it.timeMs }
    }

    val rawPoints = remember(validTrackPoints) {
        validTrackPoints.map { point -> point.toUrbanMapPoint() }
    }

    val mapPoints = remember(validTrackPoints) {
        validTrackPoints.toSmoothedMapPoints()
    }

    val eventMarkers = remember(stopEvents) {
        stopEvents
            .filter { it.stopLat != 0.0 && it.stopLon != 0.0 }
            .sortedBy { it.timestamp }
            .map { event -> event.toUrbanMapPoint() }
    }

    val metrics = remember(validTrackPoints, stopEvents, mapPoints) {
        AsdMapMetrics.from(
            points = validTrackPoints,
            events = stopEvents,
            displayedPointCount = mapPoints.size
        )
    }

    val timelineItems = remember(trip, stopEvents) {
        buildAsdTimeline(
            startTime = trip?.startTime,
            events = stopEvents,
            endTime = trip?.endTime
        )
    }

    val startPoint = remember(rawPoints) {
        rawPoints.firstOrNull()?.copy(title = "Inicio ASD")
    }

    val endPoint = remember(rawPoints) {
        rawPoints.lastOrNull()?.copy(title = "Último punto ASD")?.takeIf { rawPoints.size >= 2 }
    }

    val locatePoint = remember(rawPoints) {
        rawPoints.lastOrNull()?.copy(
            id = "asd-current-location",
            title = "Ubicación actual / último GPS",
            subtitle = "Último punto registrado del recorrido"
        )
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
                points = mapPoints,
                eventMarkers = eventMarkers,
                startPoint = startPoint,
                endPoint = endPoint,
                focusPoint = locatePoint,
                focusRequestKey = locateRequestKey,
                showPointMarkers = false,
                emptyMessage = "Este viaje aún no tiene trackpoints con GPS válido."
            )

            AsdMapLegend(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            )

            Button(
                enabled = locatePoint != null,
                onClick = { locateRequestKey++ },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(12.dp)
            ) {
                Text("Ubicarme")
            }

            AsdTimelineCard(
                items = timelineItems,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
            )

            Card(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                Column(Modifier.padding(10.dp)) {
                    Text("Resumen ASD", style = MaterialTheme.typography.titleSmall)
                    Text("Puntos: ${metrics.pointCount}", style = MaterialTheme.typography.bodySmall)
                    Text("Dibujados: ${metrics.displayedPointCount}", style = MaterialTheme.typography.bodySmall)
                    Text("Distancia: ${metrics.distanceText}", style = MaterialTheme.typography.bodySmall)
                    Text("Duración: ${metrics.durationText}", style = MaterialTheme.typography.bodySmall)
                    Text("Precisión prom: ${metrics.accuracyText}", style = MaterialTheme.typography.bodySmall)
                    Text("Eventos: ${metrics.eventCount}", style = MaterialTheme.typography.bodySmall)
                    Text("Ascensos: ${metrics.boardingCount}  Descensos: ${metrics.alightingCount}", style = MaterialTheme.typography.bodySmall)
                    Text("Demoras: ${metrics.delayCount}", style = MaterialTheme.typography.bodySmall)
                    Text("Pax +${metrics.boardingPax} / -${metrics.alightingPax}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun AsdMapLegend(modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(10.dp)) {
            Text("Leyenda", style = MaterialTheme.typography.titleSmall)
            Text("🔵 Ruta", style = MaterialTheme.typography.bodySmall)
            Text("🟢 Inicio", style = MaterialTheme.typography.bodySmall)
            Text("🔴 Fin", style = MaterialTheme.typography.bodySmall)
            Text("🔷 Ascenso", style = MaterialTheme.typography.bodySmall)
            Text("🟠 Descenso", style = MaterialTheme.typography.bodySmall)
            Text("🟣 Demora", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AsdTimelineCard(
    items: List<String>,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    Card(modifier = modifier) {
        Column(Modifier.padding(10.dp)) {
            Text("Secuencia", style = MaterialTheme.typography.titleSmall)
            items.take(6).forEach { item ->
                Text(item, style = MaterialTheme.typography.bodySmall)
            }
            if (items.size > 6) {
                Text("+${items.size - 6} eventos más", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun buildAsdTimeline(
    startTime: Long?,
    events: List<StopEvent>,
    endTime: Long?
): List<String> {
    val items = mutableListOf<Pair<Long, String>>()

    startTime?.let { items.add(it to "${formattedTime(it)} Inicio") }
    events.forEach { event ->
        items.add(event.timestamp to "${formattedTime(event.timestamp)} ${event.timelineLabel()}")
    }
    endTime?.let { items.add(it to "${formattedTime(it)} Fin") }

    return items.sortedBy { it.first }.map { it.second }
}

private fun StopEvent.timelineLabel(): String {
    val label = when (eventCategory()) {
        AsdEventCategory.BOARDING -> "Ascenso"
        AsdEventCategory.ALIGHTING -> "Descenso"
        AsdEventCategory.DELAY -> "Demora"
        AsdEventCategory.OTHER -> stopType.uppercase()
    }
    val place = stopName?.takeIf { it.isNotBlank() }?.let { " - $it" } ?: ""
    return "$label$place"
}

private fun List<TrackPoint>.toSmoothedMapPoints(): List<UrbanMapPoint> {
    if (isEmpty()) return emptyList()
    if (size < 5) return map { it.toUrbanMapPoint() }

    val raw = map { LatLng(it.lat, it.lon) }
    val smooth = PolylineSmoother.movingAverage(raw, window = 3)
    val simplified = PolylineSmoother.douglasPeucker(smooth, epsilonMeters = 2.5)
    val simplifiedKeys = simplified.mapIndexed { index, point -> index to point }

    return simplifiedKeys.map { (index, point) ->
        val source = this[index.coerceAtMost(lastIndex)]
        source.toUrbanMapPoint().copy(
            id = "smooth-${source.id}-$index",
            lat = point.lat,
            lon = point.lon,
            title = "Ruta suavizada"
        )
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

private fun StopEvent.toUrbanMapPoint(): UrbanMapPoint {
    val category = eventCategory()
    val label = when (category) {
        AsdEventCategory.BOARDING -> "Ascenso"
        AsdEventCategory.ALIGHTING -> "Descenso"
        AsdEventCategory.DELAY -> "Demora"
        AsdEventCategory.OTHER -> stopType.uppercase()
    }
    return UrbanMapPoint(
        id = "event-$eventId",
        title = "$label ${stopName ?: ""}".trim(),
        subtitle = eventSubtitle(),
        lat = stopLat,
        lon = stopLon,
        accuracyM = stopAccM,
        status = category.mapStatus,
        module = "ASD_EVENT",
        timestampMs = timestamp,
        metadata = mapOf(
            "tripId" to tripId.toString(),
            "stopType" to stopType,
            "count" to count.toString(),
            "time" to formattedTime(timestamp)
        )
    )
}

private fun StopEvent.eventSubtitle(): String {
    val paxUp = paxMenUp + paxWomenUp
    val paxDown = paxMenDown + paxWomenDown
    val parts = buildList {
        add("Hora: ${formattedTime(timestamp)}")
        if (paxUp > 0) add("Suben: $paxUp (H:$paxMenUp M:$paxWomenUp)")
        if (paxDown > 0) add("Bajan: $paxDown (H:$paxMenDown M:$paxWomenDown)")
        if (!delayCodes.isNullOrBlank()) add("Demora: $delayCodes")
        if (!otherDelayDesc.isNullOrBlank()) add(otherDelayDesc)
        if (!notes.isNullOrBlank()) add("Notas: $notes")
        add(locationStatus)
        add("GPS ±${stopAccM.roundToInt()}m")
    }
    return parts.joinToString(" | ")
}

private fun formattedTime(timeMs: Long): String {
    return SimpleDateFormat("HH:mm:ss", Locale("es", "MX")).format(Date(timeMs))
}

private enum class AsdEventCategory(val mapStatus: String) {
    BOARDING("ASCENSO"),
    ALIGHTING("DESCENSO"),
    DELAY("BANDERA"),
    OTHER("OTHER")
}

private fun StopEvent.eventCategory(): AsdEventCategory {
    val type = stopType.trim().uppercase()
    val hasBoarding = paxMenUp + paxWomenUp > 0
    val hasAlighting = paxMenDown + paxWomenDown > 0
    val hasDelay = !delayCodes.isNullOrBlank() || type in setOf("DEMORA", "BANDERA", "DELAY")

    return when {
        type in setOf("ASCENSO", "SUBE", "BOARDING") || hasBoarding -> AsdEventCategory.BOARDING
        type in setOf("DESCENSO", "BAJA", "ALIGHTING") || hasAlighting -> AsdEventCategory.ALIGHTING
        hasDelay -> AsdEventCategory.DELAY
        else -> AsdEventCategory.OTHER
    }
}

private data class AsdMapMetrics(
    val pointCount: Int,
    val displayedPointCount: Int,
    val eventCount: Int,
    val boardingCount: Int,
    val alightingCount: Int,
    val delayCount: Int,
    val boardingPax: Int,
    val alightingPax: Int,
    val distanceText: String,
    val durationText: String,
    val accuracyText: String
) {
    companion object {
        fun from(points: List<TrackPoint>, events: List<StopEvent>, displayedPointCount: Int): AsdMapMetrics {
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
                displayedPointCount = displayedPointCount,
                eventCount = events.size,
                boardingCount = events.count { it.eventCategory() == AsdEventCategory.BOARDING },
                alightingCount = events.count { it.eventCategory() == AsdEventCategory.ALIGHTING },
                delayCount = events.count { it.eventCategory() == AsdEventCategory.DELAY },
                boardingPax = events.sumOf { it.paxMenUp + it.paxWomenUp },
                alightingPax = events.sumOf { it.paxMenDown + it.paxWomenDown },
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
