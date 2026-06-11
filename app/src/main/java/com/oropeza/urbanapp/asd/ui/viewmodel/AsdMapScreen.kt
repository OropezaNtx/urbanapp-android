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
        trackPoints.filter { isValidCoordinate(it.lat, it.lon) }.sortedBy { it.timeMs }
    }
    val rawPoints = remember(validTrackPoints) { validTrackPoints.map { it.toUrbanMapPoint() } }
    val mapPoints = remember(validTrackPoints) { validTrackPoints.toSmoothedMapPoints() }
    val eventMarkers = remember(stopEvents) {
        stopEvents
            .sortedBy { it.timestamp }
            .flatMap { it.toUrbanMapWaypointPoints() }
            .sortedWith(compareBy<UrbanMapPoint> { it.timestampMs ?: Long.MAX_VALUE }.thenBy { it.id })
    }
    val metrics = remember(validTrackPoints, stopEvents, mapPoints) {
        AsdMapMetrics.from(validTrackPoints, stopEvents, mapPoints.size)
    }
    val timelineItems = remember(trip, stopEvents) { buildAsdTimeline(trip?.startTime, stopEvents, trip?.endTime) }
    val startPoint = remember(rawPoints) { rawPoints.firstOrNull()?.copy(title = "INICIO RECORRIDO ASD") }
    val endPoint = remember(rawPoints) { rawPoints.lastOrNull()?.copy(title = "ÚLTIMO PUNTO ASD")?.takeIf { rawPoints.size >= 2 } }
    val locatePoint = remember(rawPoints) {
        rawPoints.lastOrNull()?.copy(
            id = "asd-current-location",
            title = "UBICACIÓN ACTUAL / ÚLTIMO GPS",
            subtitle = "ÚLTIMO PUNTO REGISTRADO DEL RECORRIDO"
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(trip?.routeName?.uppercase(Locale("es", "MX")) ?: "MAPA ASD") },
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
                emptyMessage = "ESTE VIAJE AÚN NO TIENE TRACKPOINTS CON GPS VÁLIDO."
            )
            AsdMapLegend(Modifier.align(Alignment.TopEnd).padding(12.dp))
            Button(
                enabled = locatePoint != null,
                onClick = { locateRequestKey++ },
                modifier = Modifier.align(Alignment.TopCenter).padding(12.dp)
            ) { Text("UBICARME") }
            AsdTimelineCard(timelineItems, Modifier.align(Alignment.BottomEnd).padding(12.dp))
            Card(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                Column(Modifier.padding(10.dp)) {
                    Text("RESUMEN ASD", style = MaterialTheme.typography.titleSmall)
                    Text("PUNTOS: ${metrics.pointCount}", style = MaterialTheme.typography.bodySmall)
                    Text("DIBUJADOS: ${metrics.displayedPointCount}", style = MaterialTheme.typography.bodySmall)
                    Text("DISTANCIA: ${metrics.distanceText}", style = MaterialTheme.typography.bodySmall)
                    Text("DURACIÓN: ${metrics.durationText}", style = MaterialTheme.typography.bodySmall)
                    Text("PRECISIÓN PROM: ${metrics.accuracyText}", style = MaterialTheme.typography.bodySmall)
                    Text("EVENTOS: ${metrics.eventCount}", style = MaterialTheme.typography.bodySmall)
                    Text("ASCENSOS: ${metrics.boardingCount}  DESCENSOS: ${metrics.alightingCount}", style = MaterialTheme.typography.bodySmall)
                    Text("DEMORAS: ${metrics.delayCount}", style = MaterialTheme.typography.bodySmall)
                    Text("PAX +${metrics.boardingPax} / -${metrics.alightingPax}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun AsdMapLegend(modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(10.dp)) {
            Text("LEYENDA", style = MaterialTheme.typography.titleSmall)
            Text("🔵 RUTA", style = MaterialTheme.typography.bodySmall)
            Text("🟢 INICIO RECORRIDO", style = MaterialTheme.typography.bodySmall)
            Text("🔴 FIN / ÚLTIMO PUNTO", style = MaterialTheme.typography.bodySmall)
            Text("🟣 WP INICIO REGISTRO", style = MaterialTheme.typography.bodySmall)
            Text("🟣 WP CIERRE REGISTRO", style = MaterialTheme.typography.bodySmall)
            Text("AD = ASCENSO / DESCENSO", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AsdTimelineCard(items: List<String>, modifier: Modifier = Modifier) {
    if (items.isEmpty()) return
    Card(modifier = modifier) {
        Column(Modifier.padding(10.dp)) {
            Text("SECUENCIA", style = MaterialTheme.typography.titleSmall)
            items.take(6).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (items.size > 6) Text("+${items.size - 6} EVENTOS MÁS", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun buildAsdTimeline(startTime: Long?, events: List<StopEvent>, endTime: Long?): List<String> {
    val items = mutableListOf<Pair<Long, String>>()
    startTime?.let { items.add(it to "${formattedTime(it)} INICIO") }
    events.forEach { items.add(it.timestamp to "${formattedTime(it.timestamp)} ${it.timelineLabel()}") }
    endTime?.let { items.add(it to "${formattedTime(it)} FIN") }
    return items.sortedBy { it.first }.map { it.second }
}

private fun StopEvent.timelineLabel(): String {
    val place = stopName?.takeIf { it.isNotBlank() }?.let { " - $it" } ?: ""
    return "${displayLabel()}$place"
}

private fun List<TrackPoint>.toSmoothedMapPoints(): List<UrbanMapPoint> {
    if (isEmpty()) return emptyList()
    if (size < 5) return map { it.toUrbanMapPoint() }
    val raw = map { LatLng(it.lat, it.lon) }
    val smooth = PolylineSmoother.movingAverage(raw, window = 3)
    val simplified = PolylineSmoother.douglasPeucker(smooth, epsilonMeters = 2.5)
    return simplified.mapIndexed { index, point ->
        val source = this[index.coerceAtMost(lastIndex)]
        source.toUrbanMapPoint().copy(id = "smooth-${source.id}-$index", lat = point.lat, lon = point.lon, title = "RUTA SUAVIZADA")
    }
}

private fun TrackPoint.toUrbanMapPoint(): UrbanMapPoint = UrbanMapPoint(
    id = "track-$id",
    title = "TRACK GPS",
    subtitle = "$provider | ACC ${accM}M",
    lat = lat,
    lon = lon,
    accuracyM = accM,
    status = provider,
    module = "ASD_TRACK",
    timestampMs = timeMs,
    metadata = mapOf("tripId" to tripId.toString())
)

private fun StopEvent.toUrbanMapWaypointPoints(): List<UrbanMapPoint> {
    val points = mutableListOf<UrbanMapPoint>()
    val category = eventCategory()
    if (isValidCoordinate(stopLat, stopLon)) {
        points += UrbanMapPoint(
            id = "event-$eventId-wp-inicio-$waypointStopId",
            title = "WP $waypointStopId INICIO REGISTRO • ${displayLabel()}",
            subtitle = waypointSubtitle(isClose = false),
            lat = stopLat,
            lon = stopLon,
            accuracyM = stopAccM,
            status = category.mapStatus,
            module = "ASD_EVENT_START",
            timestampMs = stopTime.takeIf { it > 0L } ?: timestamp,
            metadata = mapOf("tripId" to tripId.toString(), "wp" to waypointStopId.toString(), "fase" to "INICIO")
        )
    }
    if (isValidCoordinate(startLat, startLon) && startTime > 0L && waypointStartId != waypointStopId) {
        points += UrbanMapPoint(
            id = "event-$eventId-wp-cierre-$waypointStartId",
            title = "WP $waypointStartId CIERRE REGISTRO • ${displayLabel()}",
            subtitle = waypointSubtitle(isClose = true),
            lat = startLat,
            lon = startLon,
            accuracyM = startAccM,
            status = category.mapStatus,
            module = "ASD_EVENT_CLOSE",
            timestampMs = startTime,
            metadata = mapOf("tripId" to tripId.toString(), "wp" to waypointStartId.toString(), "fase" to "CIERRE")
        )
    }
    return points
}

private fun isValidCoordinate(lat: Double, lon: Double): Boolean {
    if (!lat.isFinite() || !lon.isFinite()) return false
    if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return false
    if (lat == 0.0 && lon == 0.0) return false
    return true
}

private fun StopEvent.waypointSubtitle(isClose: Boolean): String {
    val paxUp = paxMenUp + paxWomenUp
    val paxDown = paxMenDown + paxWomenDown
    val time = if (isClose) startTime else stopTime.takeIf { it > 0L } ?: timestamp
    val acc = if (isClose) startAccM else stopAccM
    return buildList {
        add(if (isClose) "CIERRE: ${formattedTime(time)}" else "INICIO: ${formattedTime(time)}")
        if (paxUp > 0) add("SUBEN: $paxUp (H:$paxMenUp M:$paxWomenUp)")
        if (paxDown > 0) add("BAJAN: $paxDown (H:$paxMenDown M:$paxWomenDown)")
        if (!delayCodes.isNullOrBlank()) add("DEMORA: $delayCodes")
        if (!otherDelayDesc.isNullOrBlank()) add(otherDelayDesc)
        if (!notes.isNullOrBlank()) add("NOTAS: $notes")
        add(locationStatus)
        add("GPS ±${acc.roundToInt()}M")
    }.joinToString(" | ")
}

private fun formattedTime(timeMs: Long): String = SimpleDateFormat("HH:mm:ss", Locale("es", "MX")).format(Date(timeMs))

private enum class AsdEventCategory(val mapStatus: String) {
    BOARDING("ASCENSO"), ALIGHTING("DESCENSO"), DELAY("BANDERA"), COMBINED("BANDERA"), OTHER("OTHER")
}

private fun StopEvent.hasBoarding(): Boolean = paxMenUp + paxWomenUp > 0
private fun StopEvent.hasAlighting(): Boolean = paxMenDown + paxWomenDown > 0
private fun StopEvent.hasDelay(): Boolean {
    val type = stopType.trim().uppercase(Locale("es", "MX"))
    return !delayCodes.isNullOrBlank() || type in setOf("DEMORA", "BANDERA", "DELAY")
}

private fun StopEvent.eventCategory(): AsdEventCategory {
    val type = stopType.trim().uppercase(Locale("es", "MX"))
    val hasPax = hasBoarding() || hasAlighting()
    val hasDelay = hasDelay()
    return when {
        hasPax && hasDelay -> AsdEventCategory.COMBINED
        type in setOf("ASCENSO", "SUBE", "BOARDING") || hasBoarding() -> AsdEventCategory.BOARDING
        type in setOf("DESCENSO", "BAJA", "ALIGHTING") || hasAlighting() -> AsdEventCategory.ALIGHTING
        hasDelay -> AsdEventCategory.DELAY
        else -> AsdEventCategory.OTHER
    }
}

private fun StopEvent.displayLabel(): String {
    val hasUp = hasBoarding()
    val hasDown = hasAlighting()
    val hasDelay = hasDelay()
    return when {
        (hasUp || hasDown) && hasDelay -> "ASD + DEMORA"
        hasUp && hasDown -> "ASD"
        hasUp -> "ASCENSO"
        hasDown -> "DESCENSO"
        hasDelay -> "DEMORA"
        else -> stopType.uppercase(Locale("es", "MX"))
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
            val distanceM = points.zipWithNext().sumOf { (a, b) -> haversineMeters(a.lat, a.lon, b.lat, b.lon) }
            val durationMs = if (points.size >= 2) (points.last().timeMs - points.first().timeMs).coerceAtLeast(0L) else 0L
            val avgAcc = points.map { it.accM }.filter { it > 0.0 && it < 9999.0 }.averageOrNull()
            return AsdMapMetrics(
                pointCount = points.size,
                displayedPointCount = displayedPointCount,
                eventCount = events.size,
                boardingCount = events.count { it.hasBoarding() },
                alightingCount = events.count { it.hasAlighting() },
                delayCount = events.count { it.hasDelay() },
                boardingPax = events.sumOf { it.paxMenUp + it.paxWomenUp },
                alightingPax = events.sumOf { it.paxMenDown + it.paxWomenDown },
                distanceText = distanceText(distanceM),
                durationText = durationText(durationMs),
                accuracyText = avgAcc?.let { "${it.roundToInt()}M" } ?: "-"
            )
        }
        private fun distanceText(distanceM: Double): String = when {
            distanceM <= 0.0 -> "-"
            distanceM < 1000.0 -> "${distanceM.roundToInt()}M"
            else -> String.format(Locale("es", "MX"), "%.2fKM", distanceM / 1000.0)
        }
        private fun durationText(durationMs: Long): String {
            if (durationMs <= 0L) return "-"
            val totalSec = durationMs / 1000L
            val h = totalSec / 3600L
            val m = (totalSec % 3600L) / 60L
            val s = totalSec % 60L
            return if (h > 0L) "${h}H ${m}M" else "${m}M ${s}S"
        }
        private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()
        private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6_371_000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return r * c
        }
    }
}
