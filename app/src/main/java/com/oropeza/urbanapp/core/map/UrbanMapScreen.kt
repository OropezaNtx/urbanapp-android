package com.oropeza.urbanapp.core.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.*
import com.oropeza.urbanapp.BuildConfig
import kotlin.math.roundToLong

@Composable
fun UrbanMapScreen(
    points: List<UrbanMapPoint>,
    modifier: Modifier = Modifier,
    emptyMessage: String = "No hay puntos con ubicación válida.",
    showRouteLine: Boolean = true,
    showPointMarkers: Boolean = true,
    eventMarkers: List<UrbanMapPoint> = emptyList(),
    startPoint: UrbanMapPoint? = null,
    endPoint: UrbanMapPoint? = null,
    focusPoint: UrbanMapPoint? = null,
    focusRequestKey: Int = 0,
    onPointClick: (UrbanMapPoint) -> Unit = {}
) {
    val isMapsApiKeyConfigured = BuildConfig.MAPS_API_KEY.isNotBlank()

    val validPoints = remember(points) {
        points
            .filter { it.hasValidCoordinates }
            .sortedWith(compareBy<UrbanMapPoint> { it.timestampMs ?: Long.MAX_VALUE }.thenBy { it.id })
    }

    val validEventMarkers = remember(eventMarkers) {
        eventMarkers
            .filter { it.hasValidCoordinates }
            .sortedWith(compareBy<UrbanMapPoint> { it.timestampMs ?: Long.MAX_VALUE }.thenBy { it.id })
    }

    val validStartPoint = remember(startPoint) { startPoint?.takeIf { it.hasValidCoordinates } }
    val validEndPoint = remember(endPoint) { endPoint?.takeIf { it.hasValidCoordinates } }
    val validFocusPoint = remember(focusPoint) { focusPoint?.takeIf { it.hasValidCoordinates } }

    val allCameraPoints = remember(validPoints, validEventMarkers, validStartPoint, validEndPoint) {
        buildList {
            addAll(validPoints)
            addAll(validEventMarkers)
            validStartPoint?.let { add(it) }
            validEndPoint?.let { add(it) }
        }
    }

    if (allCameraPoints.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Card(Modifier.padding(16.dp)) {
                Text(emptyMessage, modifier = Modifier.padding(16.dp))
            }
        }
        return
    }

    val cameraPositionState = rememberCameraPositionState()
    var selectedPoint by remember { mutableStateOf<UrbanMapPoint?>(null) }

    val routePositions = remember(validPoints) {
        validPoints.map { LatLng(it.lat, it.lon) }
            .distinctBy { coordinateKey(it.latitude, it.longitude) }
    }

    val cameraPositions = remember(allCameraPoints) {
        allCameraPoints.map { LatLng(it.lat, it.lon) }
            .distinctBy { coordinateKey(it.latitude, it.longitude) }
    }

    val markerGroups = remember(validPoints) {
        validPoints.groupBy { coordinateKey(it.lat, it.lon) }
            .values
            .map { group ->
                UrbanMapMarkerGroup(
                    position = LatLng(group.first().lat, group.first().lon),
                    points = group
                )
            }
    }

    val eventGroups = remember(validEventMarkers) {
        validEventMarkers.groupBy { coordinateKey(it.lat, it.lon) }
            .values
            .map { group ->
                UrbanMapMarkerGroup(
                    position = LatLng(group.first().lat, group.first().lon),
                    points = group
                )
            }
    }

    LaunchedEffect(cameraPositions) {
        if (cameraPositions.size == 1) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(cameraPositions.first(), 17f)
            )
        } else {
            val builder = LatLngBounds.Builder()
            cameraPositions.forEach { builder.include(it) }
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngBounds(builder.build(), 120)
            )
        }
    }

    LaunchedEffect(focusRequestKey, validFocusPoint) {
        validFocusPoint?.let { point ->
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(LatLng(point.lat, point.lon), 18f)
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState
        ) {
            if (showRouteLine && routePositions.size >= 2) {
                Polyline(
                    points = routePositions,
                    color = Color(0xFF1565C0),
                    width = 8f
                )
            }

            if (showPointMarkers) {
                RenderMarkerGroups(
                    groups = markerGroups,
                    onSelected = { selectedPoint = it },
                    onPointClick = onPointClick
                )
            }

            RenderMarkerGroups(
                groups = eventGroups,
                onSelected = { selectedPoint = it },
                onPointClick = onPointClick
            )

            validStartPoint?.let { point ->
                SpecialMarker(
                    point = point,
                    title = "Inicio",
                    hue = BitmapDescriptorFactory.HUE_GREEN,
                    onClick = {
                        selectedPoint = point
                        onPointClick(point)
                    }
                )
            }

            validEndPoint?.let { point ->
                SpecialMarker(
                    point = point,
                    title = "Fin",
                    hue = BitmapDescriptorFactory.HUE_RED,
                    onClick = {
                        selectedPoint = point
                        onPointClick(point)
                    }
                )
            }
        }

        if (!isMapsApiKeyConfigured) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(12.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "Google Maps sin API Key",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        "Agrega MAPS_API_KEY en local.properties y recompila.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Card(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
        ) {
            Column(Modifier.padding(10.dp)) {
                Text("Puntos: ${validPoints.size}", style = MaterialTheme.typography.titleSmall)
                Text("Ubicaciones: ${markerGroups.size}", style = MaterialTheme.typography.bodySmall)
                if (validEventMarkers.isNotEmpty()) {
                    Text("Eventos: ${validEventMarkers.size}", style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "Lat: ${allCameraPoints.first().lat} | Lon: ${allCameraPoints.first().lon}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        selectedPoint?.let { point ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(point.title, style = MaterialTheme.typography.titleSmall)
                    point.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Text("Lat: ${point.lat}", style = MaterialTheme.typography.bodySmall)
                    Text("Lon: ${point.lon}", style = MaterialTheme.typography.bodySmall)
                    point.accuracyM?.let {
                        Text("Precisión: ±${it}m", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderMarkerGroups(
    groups: List<UrbanMapMarkerGroup>,
    onSelected: (UrbanMapPoint) -> Unit,
    onPointClick: (UrbanMapPoint) -> Unit
) {
    groups.forEach { group ->
        val representative = group.points.first()
        Marker(
            state = MarkerState(position = group.position),
            title = if (group.points.size == 1) representative.title else "${group.points.size} registros aquí",
            snippet = if (group.points.size == 1) {
                representative.subtitle
            } else {
                group.points.take(3).joinToString("\n") { it.title }
            },
            icon = BitmapDescriptorFactory.defaultMarker(markerHueFor(representative)),
            onClick = {
                onSelected(representative)
                onPointClick(representative)
                false
            }
        )
    }
}

@Composable
private fun SpecialMarker(
    point: UrbanMapPoint,
    title: String,
    hue: Float,
    onClick: () -> Unit
) {
    Marker(
        state = MarkerState(position = LatLng(point.lat, point.lon)),
        title = title,
        snippet = point.subtitle,
        icon = BitmapDescriptorFactory.defaultMarker(hue),
        onClick = {
            onClick()
            false
        }
    )
}

private data class UrbanMapMarkerGroup(
    val position: LatLng,
    val points: List<UrbanMapPoint>
)

private fun coordinateKey(lat: Double, lon: Double): String {
    val roundedLat = (lat * 1_000_000.0).roundToLong()
    val roundedLon = (lon * 1_000_000.0).roundToLong()
    return "$roundedLat,$roundedLon"
}

private fun markerHueFor(point: UrbanMapPoint): Float {
    return when (point.status?.uppercase(Locale.ROOT)) {
        "START", "INICIO", "OK", "GPS", "FUSED", "FIX_OK" -> BitmapDescriptorFactory.HUE_GREEN
        "END", "FIN", "FINAL", "NO_FIX", "INVALID" -> BitmapDescriptorFactory.HUE_RED
        "ASCENSO", "DESCENSO", "ASD", "EVENTO", "AD" -> BitmapDescriptorFactory.HUE_AZURE
        "DEMORA", "DELAY", "BANDERA" -> BitmapDescriptorFactory.HUE_ORANGE
        "COMBINED", "ASD_DEMORA" -> BitmapDescriptorFactory.HUE_VIOLET
        "LOW_ACCURACY", "APPROX", "NETWORK", "FIX_USABLE" -> BitmapDescriptorFactory.HUE_YELLOW
        else -> BitmapDescriptorFactory.HUE_RED
    }
}
