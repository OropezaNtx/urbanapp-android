package com.oropeza.urbanapp.fov.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FovNewSessionScreen(
    onCreated: (Long) -> Unit,
    onBack: () -> Unit,
    vm: FovVm = viewModel()
) {
    var estacion by remember { mutableStateOf("") }
    var ubicacion by remember { mutableStateOf("") }
    var sentido by remember { mutableStateOf("") }

    var esFs by remember { mutableStateOf("") }
    var supervisor by remember { mutableStateOf("") }
    var aforador by remember { mutableStateOf("") }

    // MVP: dateDayMs = hoy (00:00 no estricto; luego lo refinamos)
    val dateDayMs = remember { System.currentTimeMillis() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nueva sesión FOV") },
                navigationIcon = { IconButton(onClick = onBack) { Text("←") } }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(estacion, { estacion = it }, label = { Text("Estación") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(ubicacion, { ubicacion = it }, label = { Text("Ubicación") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(sentido, { sentido = it }, label = { Text("Sentido") }, modifier = Modifier.fillMaxWidth())

            OutlinedTextField(esFs, { esFs = it }, label = { Text("ES/FS") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(supervisor, { supervisor = it }, label = { Text("Supervisor") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(aforador, { aforador = it }, label = { Text("Aforador") }, modifier = Modifier.fillMaxWidth())

            Button(
                onClick = {
                    vm.createSession(
                        estacion = estacion,
                        ubicacion = ubicacion,
                        sentido = sentido,
                        dateDayMs = dateDayMs,
                        esFs = esFs,
                        supervisor = supervisor,
                        aforador = aforador,
                        onCreated = onCreated
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = estacion.isNotBlank() && ubicacion.isNotBlank() && sentido.isNotBlank()
            ) {
                Text("Iniciar sesión")
            }
        }
    }
}
