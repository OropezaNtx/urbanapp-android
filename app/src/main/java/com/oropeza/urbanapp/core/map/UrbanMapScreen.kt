package com.oropeza.urbanapp.core.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
            val builder = LatLngBounds.Builder()
            validPoints.forEach { point ->
                builder.include(LatLng(point.lat, point.lon))
            }
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngBounds(builder.build(), 120)
            )
        }
    }

    GoogleMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState
    ) {
        validPoints.forEach { point ->
            Marker(
                state = MarkerState(position = LatLng(point.lat, point.lon)),
                title = point.title,
                snippet = point.subtitle,
                onClick = {
                    onPointClick(point)
                    false
                }
            )
        }
    }
}
