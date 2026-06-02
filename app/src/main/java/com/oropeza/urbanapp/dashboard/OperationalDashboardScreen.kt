package com.oropeza.urbanapp.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperationalDashboardScreen(
    onBack: () -> Unit,
    vm: OperationalDashboardVm = viewModel()
) {
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Operación") }) }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Resumen operativo local",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                "Indicadores calculados desde la base local del dispositivo.",
                style = MaterialTheme.typography.bodySmall
            )

            ModuleCard(
                title = "FOV",
                items = listOf(
                    "Sesiones" to state.fovSessions.toString(),
                    "Abiertas" to state.fovOpenSessions.toString(),
                    "Registros" to state.fovObservations.toString(),
                    "GPS válido" to "${state.fovGpsValidPercent}%"
                )
            )

            ModuleCard(
                title = "ASD",
                items = listOf(
                    "Viajes" to state.asdTrips.toString(),
                    "Abiertos" to state.asdOpenTrips.toString(),
                    "Eventos" to state.asdEvents.toString(),
                    "Trackpoints" to state.asdTrackPoints.toString()
                )
            )

            ModuleCard(
                title = "Cierre de Circuito",
                items = listOf(
                    "Sesiones" to state.ccSessions.toString(),
                    "Abiertas" to state.ccOpenSessions.toString(),
                    "Eventos" to state.ccEvents.toString()
                )
            )

            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Volver")
            }
        }
    }
}

@Composable
private fun ModuleCard(
    title: String,
    items: List<Pair<String, String>>
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            items.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowItems.forEach { (label, value) ->
                        Column(modifier = Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.labelSmall)
                            Text(value, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                    if (rowItems.size == 1) {
                        Column(modifier = Modifier.weight(1f)) {}
                    }
                }
            }
        }
    }
}
