package com.oropeza.urbanapp.cc.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oropeza.urbanapp.cc.viewmodel.CcNewSessionVM
import com.oropeza.urbanapp.asd.data.local.CcSession
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CcNewSessionScreen(
    onCreated: (Long) -> Unit,
    onBack: () -> Unit,
    vm: CcNewSessionVM = viewModel()
) {
    val scope = rememberCoroutineScope()

    var planningId by remember { mutableStateOf("") }
    var base by remember { mutableStateOf("CENTRO") }
    var direction by remember { mutableStateOf("IDA") } // ✅ IDA/REGRESO

    var locationName by remember { mutableStateOf("") }
    var aforador by remember { mutableStateOf("") }

    var terminalOrigin by remember { mutableStateOf("") }
    var terminalDestination by remember { mutableStateOf("") }

    var companyName by remember { mutableStateOf("") }
    var derrotero by remember { mutableStateOf("") }

    val today00 = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    var dateDayMs by remember { mutableStateOf(today00) }

    val canCreate = remember(planningId, locationName, aforador, terminalOrigin, terminalDestination, companyName, derrotero) {
        planningId.isNotBlank() &&
                locationName.isNotBlank() &&
                aforador.isNotBlank() &&
                terminalOrigin.isNotBlank() &&
                terminalDestination.isNotBlank() &&
                companyName.isNotBlank() &&
                derrotero.isNotBlank()
    }

    fun applyTemplate(t: CcSession) {
        // No copiamos planningId ni aforador por default (para evitar errores)
        base = t.base
        direction = t.direction
        locationName = t.locationName
        terminalOrigin = t.terminalOrigin
        terminalDestination = t.terminalDestination
        companyName = t.companyName
        derrotero = t.derrotero
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nueva sesión CC") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Atrás") } }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier.padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            OutlinedButton(
                onClick = {
                    scope.launch {
                        val last = vm.getLatestCcSession()
                        if (last != null) applyTemplate(last)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Usar última sesión como plantilla") }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = planningId,
                    onValueChange = { planningId = it },
                    label = { Text("ID (planeación)") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Column(Modifier.weight(1f)) {
                    Text("Base", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = base == "CENTRO", onClick = { base = "CENTRO" }, label = { Text("CENTRO") })
                        FilterChip(selected = base == "PERIFERIA", onClick = { base = "PERIFERIA" }, label = { Text("PERIFERIA") })
                    }
                }
            }

            Column {
                Text("Sentido", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = direction == "IDA", onClick = { direction = "IDA" }, label = { Text("IDA") })
                    FilterChip(selected = direction == "REGRESO", onClick = { direction = "REGRESO" }, label = { Text("REGRESO") })
                }
            }

            OutlinedTextField(value = locationName, onValueChange = { locationName = it }, label = { Text("Ubicación") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = aforador, onValueChange = { aforador = it }, label = { Text("Aforador") }, modifier = Modifier.fillMaxWidth())

            OutlinedTextField(value = terminalOrigin, onValueChange = { terminalOrigin = it }, label = { Text("Terminal Origen") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = terminalDestination, onValueChange = { terminalDestination = it }, label = { Text("Terminal Destino") }, modifier = Modifier.fillMaxWidth())

            OutlinedTextField(value = companyName, onValueChange = { companyName = it }, label = { Text("Empresa") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = derrotero, onValueChange = { derrotero = it }, label = { Text("Derrotero") }, modifier = Modifier.fillMaxWidth())

            Button(
                onClick = {
                    scope.launch {
                        val id = vm.createCcSession(
                            CcSession(
                                planningId = planningId.trim(),
                                base = base,
                                direction = direction,
                                locationName = locationName.trim(),
                                aforador = aforador.trim(),
                                dateDayMs = dateDayMs,
                                terminalOrigin = terminalOrigin.trim(),
                                terminalDestination = terminalDestination.trim(),
                                companyName = companyName.trim(),
                                derrotero = derrotero.trim()
                            )
                        )
                        onCreated(id)
                    }
                },
                enabled = canCreate,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Crear sesión") }
        }
    }
}
