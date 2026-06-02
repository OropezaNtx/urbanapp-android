package com.oropeza.urbanapp.fov.ui

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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oropeza.urbanapp.asd.data.local.FovObservation
import com.oropeza.urbanapp.core.map.UrbanMapPoint
import com.oropeza.urbanapp.core.map.UrbanMapScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FovMapScreen(
    sessionId: Long,
    onBack: () -> Unit,
    vm: FovSessionVm = viewModel()
) {
    val session by vm.repo.sessionFlow(sessionId).collectAsState(initial = null)
    val observations by vm.repo.observationsFlow(sessionId).collectAsState(initial = emptyList())

    val title = session?.let { "Mapa FOV: ${it.estacion}" } ?: "Mapa FOV"

    val validObservations = remember(observations) {
        observations
            .filter { it.lat != 0.0 && it.lon != 0.0 && it.locationStatus != "NO_FIX" }
            .sortedBy { it.timeMs }
    }

    val points = remember(validObservations) {
        validObservations.map { obs -> obs.toUrbanMapPoint() }
    }

    val startPoint = remember(points) {
        points.firstOrNull()?.copy(
            id = "start-${points.first().id}",
            title = "Inicio FOV",
            subtitle = points.first().subtitle
        )
    }

    val endPoint = remember(points) {
        points.lastOrNull()?.copy(
            id = "end-${points.last().id}",
            title = "Fin FOV",
            subtitle = points.last().subtitle
        )?.takeIf { points.size >= 2 }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←") }
                }
            )
        }
    ) { pad ->
        Box(Modifier.padding(pad)) {
            UrbanMapScreen(
                points = points,
                startPoint = startPoint,
                endPoint = endPoint,
                emptyMessage = "Esta sesión aún no tiene observaciones FOV con GPS válido."
            )
        }
    }
}

private fun FovObservation.toUrbanMapPoint(): UrbanMapPoint {
    return UrbanMapPoint(
        id = folio,
        title = "$seqInSession. $ruta",
        subtitle = "$numeroRutaEmpresa | $locationStatus | ±${accM}m",
        lat = lat,
        lon = lon,
        accuracyM = accM,
        status = locationStatus,
        module = "FOV",
        timestampMs = timeMs,
        metadata = mapOf(
            "folio" to folio,
            "observableId" to observableId.toString(),
            "routeUid" to routeUid,
            "eco" to eco.orEmpty(),
            "placa" to placa.orEmpty(),
            "ocupacion" to gradoOcupacion.orEmpty(),
            "provider" to provider
        )
    )
}
