package com.oropeza.urbanapp.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenDashboard: () -> Unit,
    onOpenAsd: () -> Unit,
    onOpenCc: () -> Unit,
    onOpenFov: () -> Unit
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("UrbanApp") }) }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Pantalla principal", style = MaterialTheme.typography.titleLarge)
            Text("Selecciona un módulo:")

            Button(onClick = onOpenDashboard, modifier = Modifier.fillMaxWidth()) {
                Text("Dashboard Operativo")
            }

            OutlinedButton(onClick = onOpenAsd, modifier = Modifier.fillMaxWidth()) {
                Text("Ascensos y Descensos (ASD)")
            }

            OutlinedButton(onClick = onOpenCc, modifier = Modifier.fillMaxWidth()) {
                Text("Cierres de Circuito (CC)")
            }

            OutlinedButton(onClick = onOpenFov, modifier = Modifier.fillMaxWidth()) {
                Text("FOV (Frecuencia Observable)")
            }
        }
    }
}
