package com.oropeza.urbanapp.core.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

@Composable
fun UrbanMapScreen(
    points: List<UrbanMapPoint>,
    modifier: Modifier = Modifier,
    emptyMessage: String = "No hay puntos con ubicación válida.",
    onPointClick: (UrbanMapPoint) -> Unit = {}
) {
    val validPoints = remember(points) {
        points.filter { it.hasValidCoordinates }
    }

    if (validPoints.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Card(Modifier.padding(16.dp)) {
                Text(
                    text = emptyMessage,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        return
    }

    val cameraPositionState = rememberCameraPositionState()
    var selectedPoint by remember { mutableStateOf<UrbanMapPoint?>(null) }

    LaunchedEffect(validPoints) {
        val first = validPoints.first()
        if (validPoints.size == 1) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(first.lat, first.lon),
                    17f
                )
            )
        } else {
            val distinctPositions = validPoints
                .map { LatLng(it.lat, it.lon) }
                .distinctBy { "${it.latitude},${it.longitude}" }

            if (distinctPositions.size == 1) {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(
                        distinctPositions.first(),
                        17f
                    )
                )
            } else {
                val builder = LatLngBounds.Builder()
                distinctPositions.forEach { builder.include(it) }
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngBounds(builder.build(), 120)
                )
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState
        ) {
            validPoints.forEach { point ->
                Marker(
                    state = MarkerState(position = LatLng(point.lat, point.lon)),
                    title = point.title,
                    snippet = point.subtitle,
                    onClick = {
                        selectedPoint = point
                        onPointClick(point)
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
                Text(
                    text = "Puntos: ${validPoints.size}",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "Lat: ${validPoints.first().lat} | Lon: ${validPoints.first().lon}",
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
                    point.accuracyM?.let { Text("Precisión: ±${it}m", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}
