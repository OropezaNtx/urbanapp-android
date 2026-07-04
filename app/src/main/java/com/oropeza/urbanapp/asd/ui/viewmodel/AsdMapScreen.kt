package com.oropeza.urbanapp.asd.ui.viewmodel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oropeza.urbanapp.asd.AsdGraph
import com.oropeza.urbanapp.asd.data.local.StopEvent
import com.oropeza.urbanapp.asd.data.local.TrackPoint
import com.oropeza.urbanapp.asd.location.LatLng
import com.oropeza.urbanapp.asd.location.PolylineSmoother
import com.oropeza.urbanapp.asd.location.engine.TrackPointQuality
import com.oropeza.urbanapp.core.map.UrbanMapPoint
import com.oropeza.urbanapp.core.map.UrbanMapScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt


data class AsdMapData(
    val mapPoints: List<UrbanMapPoint>,
    val eventMarkers: List<UrbanMapPoint>,
    val metrics: AsdMapMetrics,
    val timelineItems: List<String>,
    val startPoint: UrbanMapPoint?,
    val endPoint: UrbanMapPoint?,
    val locatePoint: UrbanMapPoint?
)

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
    var mapData by remember { mutableStateOf<AsdMapData?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(trackPoints, stopEvents, trip) {
        if (trackPoints.isEmpty()) {
            isLoading = false
            mapData = null
            return@LaunchedEffect
        }

        withContext(Dispatchers.Default) {
            val orderedAll = trackPoints.sortedBy { it.timeMs }
            val clean = TrackPointQuality.cleanRoutePoints(orderedAll)
            if (clean.isEmpty()) {
                mapData = AsdMapData(
                    mapPoints = emptyList(),
                    eventMarkers = stopEvents.sortedBy { it.timestamp }
                        .flatMap { it.toUrbanMapWaypointPoints() },
                    metrics = AsdMapMetrics.from(orderedAll, emptyList(), stopEvents, 0),
                    timelineItems = buildAsdTimeline(trip?.startTime, stopEvents, trip?.endTime),
                    startPoint = null,
                    endPoint = null,
                    locatePoint = null
                )
                isLoading = false
                return@withContext
            }

            val raw = clean.map { it.toUrbanMapPoint() }
            val mapP = clean.toSmoothedMapPoints()
            val events = stopEvents.sortedBy { it.timestamp }
                .flatMap { it.toUrbanMapWaypointPoints() }
                .sortedWith(compareBy<UrbanMapPoint> { it.timestampMs ?: Long.MAX_VALUE }.thenBy { it.id })

            val mets = AsdMapMetrics.from(orderedAll, clean, stopEvents, mapP.size)
            val timeline = buildAsdTimeline(trip?.startTime, stopEvents, trip?.endTime)
            val start = raw.firstOrNull()?.copy(title = "INICIO LEVANTAMIENTO ASD", status = "START")
            val end = raw.lastOrNull()?.copy(title = "FIN / ÚLTIMO PUNTO LIMPIO ASD", status = "END")?.takeIf { raw.size >= 2 }
            val locate = raw.lastOrNull()?.copy(
                id = "asd-current-location",
                title = "UBICACIÓN ACTUAL / ÚLTIMO GPS LIMPIO",
                subtitle = "${raw.lastOrNull()?.subtitle ?: ""}"
            )

            mapData = AsdMapData(mapP, events, mets, timeline, start, end, locate)
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        trip?.routeName?.uppercase(Locale("es", "MX")) ?: "MAPA ASD",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val currentData = mapData
            if (isLoading || currentData == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFF35D36B))
                        Spacer(Modifier.height(12.dp))
                        Text("PROCESANDO RUTA...", style = MaterialTheme.typography.labelMedium)
                    }
                }
            } else {
                UrbanMapScreen(
                    points = currentData.mapPoints,
                    eventMarkers = currentData.eventMarkers,
                    startPoint = currentData.startPoint,
                    endPoint = currentData.endPoint,
                    focusPoint = currentData.locatePoint,
                    focusRequestKey = locateRequestKey,
                    showPointMarkers = false,
                    emptyMessage = "ESTE VIAJE AÚN NO TIENE TRACKPOINTS LIMPIOS PARA DIBUJAR."
                )
                PremiumGpsDashboard(currentData.metrics, Modifier.align(Alignment.TopStart).padding(12.dp))
                AsdMapLegend(Modifier.align(Alignment.TopEnd).padding(12.dp))
                Button(
                    enabled = currentData.locatePoint != null,
                    onClick = { locateRequestKey++ },
                    modifier = Modifier.align(Alignment.TopCenter).padding(12.dp)
                ) { Text("UBICARME") }
                AsdTimelineCard(currentData.timelineItems, Modifier.align(Alignment.BottomEnd).padding(12.dp))
                Card(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                    Column(Modifier.padding(10.dp)) {
                        Text("RESUMEN ASD", style = MaterialTheme.typography.titleSmall)
                        Text("PUNTOS TOTALES: ${currentData.metrics.pointCount}", style = MaterialTheme.typography.bodySmall)
                        Text("DIBUJADOS: ${currentData.metrics.displayedPointCount}", style = MaterialTheme.typography.bodySmall)
                        Text("DISTANCIA LIMPIA: ${currentData.metrics.distanceText}", style = MaterialTheme.typography.bodySmall)
                        Text("DURACIÓN: ${currentData.metrics.durationText}", style = MaterialTheme.typography.bodySmall)
                        Text("PRECISIÓN PROM: ${currentData.metrics.accuracyText}", style = MaterialTheme.typography.bodySmall)
                        Text("EVENTOS: ${currentData.metrics.eventCount}", style = MaterialTheme.typography.bodySmall)
                        Text("ASCENSOS: ${currentData.metrics.boardingCount}  DESCENSOS: ${currentData.metrics.alightingCount}", style = MaterialTheme.typography.bodySmall)
                        Text("DEMORAS: ${currentData.metrics.delayCount}", style = MaterialTheme.typography.bodySmall)
                        Text("PAX +${currentData.metrics.boardingPax} / -${currentData.metrics.alightingPax}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun PremiumGpsDashboard(metrics: AsdMapMetrics, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(10.dp)) {
            Text(metrics.gpsStatusText, style = MaterialTheme.typography.titleSmall)
            Text("${metrics.accuracyText} · ${metrics.modeText} · ${metrics.coverageText}", style = MaterialTheme.typography.bodySmall)
            Text("Puntos: ${metrics.pointCount} · Kalman ${metrics.kalmanText}", style = MaterialTheme.typography.bodySmall)
            Text("Still lock: ${metrics.stillLockText} · Clean: ${metrics.cleanText}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AsdMapLegend(modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(10.dp)) {
            Text("LEYENDA", style = MaterialTheme.typography.titleSmall)
            Text("🔵 RUTA LIMPIA", style = MaterialTheme.typography.bodySmall)
            Text("🟢 INICIO LEVANTAMIENTO", style = MaterialTheme.typography.bodySmall)
            Text("🔴 FIN / ÚLTIMO PUNTO LIMPIO", style = MaterialTheme.typography.bodySmall)
            Text("🔵 EVENTO ASD", style = MaterialTheme.typography.bodySmall)
            Text("🟠 DEMORA", style = MaterialTheme.typography.bodySmall)
            Text("🟣 ASD + DEMORA", style = MaterialTheme.typography.bodySmall)
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
    if (size < 3) return map { it.toUrbanMapPoint() }
    val raw = map { LatLng(it.filteredLat, it.filteredLon) }
    val smooth = PolylineSmoother.movingAverage(raw, window = 3)
    return smooth.mapIndexed { index, point ->
        val source = this[index.coerceAtMost(lastIndex)]
        source.toUrbanMapPoint().copy(
            id = "smooth-${source.id}-$index",
            lat = point.lat,
            lon = point.lon,
            title = "RUTA LIMPIA SMOOTH",
            subtitle = "${TrackPointQuality.auditLabel(source)} | ACC ${source.accM.roundToInt()}M | PUNTO ${index + 1}/${size}"
        )
    }
}

private fun TrackPoint.toUrbanMapPoint(): UrbanMapPoint = UrbanMapPoint(
    id = "track-$id",
    title = "TRACK GPS LIMPIO",
    subtitle = "${TrackPointQuality.auditLabel(this)} | ACC ${accM.roundToInt()}M",
    lat = filteredLat,
    lon = filteredLon,
    accuracyM = accM,
    status = sampleStatus,
    module = "ASD_TRACK",
    timestampMs = timeMs,
    metadata = mapOf(
        "tripId" to tripId.toString(),
        "sampleStatus" to sampleStatus,
        "qualityStatus" to qualityStatus,
        "geometryStatus" to geometryStatus,
        "filterStatus" to filterStatus
    )
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
    BOARDING("ASD"), ALIGHTING("ASD"), DELAY("DEMORA"), COMBINED("ASD_DEMORA"), OTHER("OTHER")
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

data class AsdMapMetrics(
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
    val accuracyText: String,
    val gpsStatusText: String,
    val modeText: String,
    val coverageText: String,
    val kalmanText: String,
    val stillLockText: String,
    val cleanText: String
) {
    companion object {
        fun from(allPoints: List<TrackPoint>, cleanPoints: List<TrackPoint>, events: List<StopEvent>, displayedPointCount: Int): AsdMapMetrics {
            val distanceM = cleanPoints.zipWithNext().sumOf { (a, b) -> haversineMeters(a.filteredLat, a.filteredLon, b.filteredLat, b.filteredLon) }
            val durationMs = if (allPoints.size >= 2) (allPoints.last().timeMs - allPoints.first().timeMs).coerceAtLeast(0L) else 0L
            val avgAcc = cleanPoints.map { it.accM }.filter { it > 0.0 && it < 9999.0 }.averageOrNull()
            val last = allPoints.lastOrNull()
            val lastAcc = last?.accM ?: avgAcc ?: 9999.0
            val kalmanCount = allPoints.count { it.filterStatus == "kalman" }
            val stillCount = allPoints.count { it.engineMode == "STILL" }
            return AsdMapMetrics(
                pointCount = allPoints.size,
                displayedPointCount = displayedPointCount,
                eventCount = events.size,
                boardingCount = events.count { it.hasBoarding() },
                alightingCount = events.count { it.hasAlighting() },
                delayCount = events.count { it.hasDelay() },
                boardingPax = events.sumOf { it.paxMenUp + it.paxWomenUp },
                alightingPax = events.sumOf { it.paxMenDown + it.paxWomenDown },
                distanceText = distanceText(distanceM),
                durationText = durationText(durationMs),
                accuracyText = avgAcc?.let { "±${it.roundToInt()}M" } ?: "-",
                gpsStatusText = gpsStatusText(last?.sampleStatus, lastAcc),
                modeText = last?.engineMode?.takeIf { it.isNotBlank() } ?: "TRACK",
                coverageText = "Clean ${percentText(cleanPoints.size, allPoints.size)}",
                kalmanText = percentText(kalmanCount, allPoints.size),
                stillLockText = percentText(stillCount, allPoints.size),
                cleanText = percentText(cleanPoints.size, allPoints.size)
            )
        }

        private fun gpsStatusText(sampleStatus: String?, accM: Double): String = when {
            sampleStatus == "NO_FIX" -> "🔴 SIN FIX"
            sampleStatus == "STALE" -> "🟠 GPS STALE"
            accM <= 8.0 -> "🟢 GPS EXCELENTE"
            accM <= 15.0 -> "🟢 GPS BUENO"
            accM <= 25.0 -> "🟡 GPS USABLE"
            else -> "🔴 GPS DÉBIL"
        }

        private fun percentText(count: Int, total: Int): String = if (total <= 0) "-" else "${((count.toDouble() / total.toDouble()) * 100.0).roundToInt()}%"

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
            return 2 * r * atan2(sqrt(a), sqrt(1 - a))
        }
    }
}
