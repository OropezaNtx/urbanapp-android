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
import kotlin.math.roundToLong

@Composable
fun UrbanMapScreen(
    points: List<UrbanMapPoint>,
    modifier: Modifier = Modifier,
    emptyMessage: String = "No hay puntos con ubicación válida.",
    showRouteLine: Boolean = true,
    onPointClick: (UrbanMapPoint) -> Unit = {}
) {
    val validPoints = remember(points) {
        points
            .filter { it.hasValidCoordinates }
            .sortedWith(compareBy<UrbanMapPoint> { it.timestampMs ?: Long.MAX_VALUE }.thenBy { it.id })
    }

    if (validPoints.isEmpty()) {
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

    LaunchedEffect(routePositions) {
        if (routePositions.size == 1) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(routePositions.first(), 17f)
            )
        } else {
            val builder = LatLngBounds.Builder()
            routePositions.forEach { builder.include(it) }
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngBounds(builder.build(), 120)
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

            markerGroups.forEach { group ->
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
                        selectedPoint = representative
                        onPointClick(representative)
                        false
                    }
                )
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
                Text(
                    "Lat: ${validPoints.first().lat} | Lon: ${validPoints.first().lon}",
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
    return when (point.status?.uppercase()) {
        "OK", "GPS", "FUSED" -> BitmapDescriptorFactory.HUE_GREEN
        "LOW_ACCURACY", "APPROX", "NETWORK" -> BitmapDescriptorFactory.HUE_YELLOW
        "NO_FIX", "INVALID" -> BitmapDescriptorFactory.HUE_RED
        else -> BitmapDescriptorFactory.HUE_RED
    }
}