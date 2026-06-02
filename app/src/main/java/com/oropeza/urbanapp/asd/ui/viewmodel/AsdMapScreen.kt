package com.oropeza.urbanapp.asd.ui.viewmodel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oropeza.urbanapp.asd.AsdGraph
import com.oropeza.urbanapp.asd.data.local.TrackPoint
import com.oropeza.urbanapp.core.map.UrbanMapPoint
import com.oropeza.urbanapp.core.map.UrbanMapScreen

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

    val points = remember(trackPoints) {
        trackPoints
            .filter { it.lat != 0.0 && it.lon != 0.0 }
            .sortedBy { it.timeMs }
            .map { point -> point.toUrbanMapPoint() }
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
                emptyMessage = "Este viaje aun no tiene trackpoints con GPS valido."
            )
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
