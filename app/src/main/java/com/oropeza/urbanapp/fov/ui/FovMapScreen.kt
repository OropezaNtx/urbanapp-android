package com.oropeza.urbanapp.fov.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
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

    val points = remember(observations) {
        observations
            .filter { it.lat != 0.0 && it.lon != 0.0 && it.locationStatus != "NO_FIX" }
            .map { obs ->
                UrbanMapPoint(
                    id = obs.folio,
                    title = "${obs.seqInSession}. ${obs.ruta}",
                    subtitle = "${obs.numeroRutaEmpresa} | ${obs.locationStatus} | ±${obs.accM}m",
                    lat = obs.lat,
                    lon = obs.lon,
                    accuracyM = obs.accM,
                    status = obs.locationStatus,
                    module = "FOV",
                    timestampMs = obs.timeMs,
                    metadata = mapOf(
                        "folio" to obs.folio,
                        "observableId" to obs.observableId.toString(),
                        "routeUid" to obs.routeUid,
                        "eco" to obs.eco.orEmpty(),
                        "placa" to obs.placa.orEmpty(),
                        "ocupacion" to obs.gradoOcupacion.orEmpty(),
                        "provider" to obs.provider
                    )
                )
            }
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
                emptyMessage = "Esta sesión aún no tiene observaciones FOV con GPS válido."
            )
        }
    }
}
