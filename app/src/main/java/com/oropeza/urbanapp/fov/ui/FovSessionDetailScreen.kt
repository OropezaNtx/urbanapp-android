package com.oropeza.urbanapp.fov.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oropeza.urbanapp.asd.data.local.FovPoiCatalogRow
import com.oropeza.urbanapp.asd.data.local.FovRouteMaster

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FovSessionDetailScreen(
    sessionId: Long,
    onBack: () -> Unit,
    vm: FovSessionVm = viewModel()
) {
    val session by vm.repo.sessionFlow(sessionId).collectAsState(initial = null)
    val selectedId by vm.selectedObservableId.collectAsState()
    val observations by vm.repo.observationsFlow(sessionId).collectAsState(initial = emptyList())

    val s = session ?: run {
        Scaffold(
            topBar = { TopAppBar(title = { Text("FOV") }) }
        ) { pad ->
            Box(Modifier.padding(pad).padding(16.dp)) {
                Text("Cargando sesión…")
            }
        }
        return
    }

    val isClosed = s.endedAt != null
    val poiCatalog by vm.repo.poiCatalogFlow(s.poiKey).collectAsState(initial = emptyList())

    var poiSearch by remember { mutableStateOf("") }
    var poiResults by remember { mutableStateOf<List<FovPoiCatalogRow>>(emptyList()) }

    LaunchedEffect(poiSearch, s.poiKey) {
        poiResults = if (poiSearch.trim().length >= 2) {
            vm.repo.searchPoiCatalog(s.poiKey, poiSearch)
        } else {
            emptyList()
        }
    }

    val showingSearch = poiSearch.trim().length >= 2

    var eco by remember { mutableStateOf("") }
    var placa by remember { mutableStateOf("") }
    var ocupacion by remember { mutableStateOf("") }
    var tipoVehiculo by remember { mutableStateOf("") }
    var descTipoVehiculo by remember { mutableStateOf("") }
    var obs by remember { mutableStateOf("") }

    var showAddCatalog by remember { mutableStateOf(false) }
    var showCloseConfirm by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var infoMsg by remember { mutableStateOf<String?>(null) }

    val masterCache = remember { mutableStateMapOf<String, FovRouteMaster>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FOV: ${s.estacion} | ${s.ubicacion} | ${s.sentido}") },
                navigationIcon = { IconButton(onClick = onBack) { Text("←") } },
                actions = {
                    if (!isClosed) {
                        TextButton(onClick = { showCloseConfirm = true }) {
                            Text("Cerrar")
                        }
                    }
                }
            )
        }
    ) { pad ->
        val scroll = rememberScrollState()
        Column(
            Modifier
                .padding(pad)
                .verticalScroll(scroll)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Estado de sesión", style = MaterialTheme.typography.titleMedium)
                    AssistChip(
                        onClick = {},
                        label = { Text(if (isClosed) "CERRADA" else "ABIERTA") }
                    )
                    Text("POI: ${s.poiKey}", style = MaterialTheme.typography.bodySmall)
                    Text("Registros capturados: ${observations.size}", style = MaterialTheme.typography.bodySmall)
                    if (isClosed) {
                        Text("Esta sesión ya no acepta nuevas capturas.", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            infoMsg?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            errorMsg?.let { Text("⚠ $it", color = MaterialTheme.colorScheme.error) }

            Text("Ruta observable (Catálogo del POI)", style = MaterialTheme.typography.titleMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { showAddCatalog = true },
                    enabled = !isClosed
                ) { Text("No está en catálogo") }
                Text("Seleccionado: ${selectedId ?: "—"}", modifier = Modifier.padding(top = 10.dp))
            }

            OutlinedTextField(
                value = poiSearch,
                onValueChange = { poiSearch = it },
                label = { Text("Buscar en catálogo del POI (RUTA / Empresa / Derrotero)") },
                enabled = !isClosed,
                modifier = Modifier.fillMaxWidth()
            )

            if (poiCatalog.isEmpty()) {
                Text("Catálogo vacío para este POI. Agrega o asigna una ruta.")
            } else if (!showingSearch) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    poiCatalog.take(6).forEach { item ->
                        val master = masterCache[item.routeUid]

                        LaunchedEffect(item.routeUid) {
                            if (masterCache[item.routeUid] == null) {
                                masterCache[item.routeUid] = vm.repo.getMaster(item.routeUid)
                            }
                        }

                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                if (!isClosed) vm.setSelectedObservableId(item.observableId)
                            }
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Text("ID ${item.observableId}", style = MaterialTheme.typography.titleSmall)
                                if (master == null) {
                                    Text("Cargando ruta…", style = MaterialTheme.typography.bodySmall)
                                } else {
                                    Text(master.ruta, style = MaterialTheme.typography.bodySmall)
                                    Text(master.numeroRutaEmpresa, style = MaterialTheme.typography.bodySmall)
                                    Text(master.derroteroLetrero, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }

                    if (poiCatalog.size > 6) {
                        Text("Catálogo: ${poiCatalog.size} items (usa el buscador arriba).")
                    }
                }
            } else {
                if (poiResults.isEmpty()) {
                    Text("Sin resultados para \"$poiSearch\".", style = MaterialTheme.typography.bodySmall)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        poiResults.take(50).forEach { row ->
                            ElevatedCard(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    if (!isClosed) vm.setSelectedObservableId(row.observableId)
                                }
                            ) {
                                Column(Modifier.padding(10.dp)) {
                                    Text("ID ${row.observableId}", style = MaterialTheme.typography.titleSmall)
                                    Text(row.ruta, style = MaterialTheme.typography.bodySmall)
                                    Text(row.numeroRutaEmpresa, style = MaterialTheme.typography.bodySmall)
                                    Text(row.derroteroLetrero, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }

            Divider()

            Text("Captura rápida", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(eco, { eco = it }, label = { Text("N° Económico") }, enabled = !isClosed, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(placa, { placa = it }, label = { Text("Placa (o S/P)") }, enabled = !isClosed, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(ocupacion, { ocupacion = it }, label = { Text("Grado de Ocupación") }, enabled = !isClosed, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(tipoVehiculo, { tipoVehiculo = it }, label = { Text("Tipo de Vehículo") }, enabled = !isClosed, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(descTipoVehiculo, { descTipoVehiculo = it }, label = { Text("Descriptor Tipo de Vehículo") }, enabled = !isClosed, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(obs, { obs = it }, label = { Text("Observaciones") }, enabled = !isClosed, modifier = Modifier.fillMaxWidth())

            Button(
                onClick = {
                    vm.addObservation(
                        sessionId = sessionId,
                        eco = eco,
                        placa = placa,
                        ocupacion = ocupacion,
                        tipoVehiculo = tipoVehiculo,
                        descTipoVehiculo = descTipoVehiculo,
                        observaciones = obs,
                        onSaved = {
                            eco = ""
                            placa = ""
                            ocupacion = ""
                            tipoVehiculo = ""
                            descTipoVehiculo = ""
                            obs = ""
                            errorMsg = null
                            infoMsg = "Registro guardado."
                        },
                        onError = { msg -> errorMsg = msg }
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isClosed && selectedId != null
            ) { Text("Guardar y continuar") }

            if (!isClosed) {
                OutlinedButton(
                    onClick = { showCloseConfirm = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Cerrar sesión FOV") }
            }

            Divider()

            Text("Registros (${observations.size})", style = MaterialTheme.typography.titleMedium)

            observations.takeLast(8).reversed().forEach { r ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (!isClosed) {
                            eco = r.eco.orEmpty()
                            placa = r.placa.orEmpty()
                            ocupacion = r.gradoOcupacion.orEmpty()
                            tipoVehiculo = r.tipoVehiculo.orEmpty()
                            descTipoVehiculo = r.descTipoVehiculo.orEmpty()
                            obs = r.observaciones.orEmpty()
                            vm.setSelectedObservableId(r.observableId)
                        }
                    }
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text("Folio: ${r.folio} | #${r.seqInSession}", style = MaterialTheme.typography.titleSmall)
                        Text("ID ${r.observableId} | ${r.ruta}", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "Eco: ${r.eco ?: "-"} | Placa: ${r.placa ?: "-"} | Ocup: ${r.gradoOcupacion ?: "-"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        if (showCloseConfirm) {
            AlertDialog(
                onDismissRequest = { showCloseConfirm = false },
                title = { Text("Cerrar sesión FOV") },
                text = { Text("¿Seguro que quieres cerrar esta sesión? Después ya no se podrán agregar registros.") },
                confirmButton = {
                    Button(
                        onClick = {
                            vm.endSession(
                                sessionId = sessionId,
                                onDone = {
                                    showCloseConfirm = false
                                    errorMsg = null
                                    infoMsg = "Sesión FOV cerrada."
                                },
                                onError = { msg ->
                                    showCloseConfirm = false
                                    errorMsg = msg
                                }
                            )
                        }
                    ) { Text("Sí, cerrar") }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showCloseConfirm = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }

        if (showAddCatalog) {
            var tab by remember { mutableStateOf(0) }
            var q by remember { mutableStateOf("") }
            var results by remember { mutableStateOf<List<FovRouteMaster>>(emptyList()) }
            var rutaNew by remember { mutableStateOf("") }
            var empresaNew by remember { mutableStateOf("") }
            var derroteroNew by remember { mutableStateOf("") }

            LaunchedEffect(q) {
                results = if (q.trim().length >= 2) vm.searchMaster(q) else emptyList()
            }

            AlertDialog(
                onDismissRequest = { showAddCatalog = false },
                confirmButton = {},
                dismissButton = { OutlinedButton(onClick = { showAddCatalog = false }) { Text("Cerrar") } },
                title = { Text("Ruta no contemplada") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        TabRow(selectedTabIndex = tab) {
                            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Buscar en biblioteca") })
                            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Crear nueva") })
                        }

                        if (tab == 0) {
                            OutlinedTextField(
                                value = q,
                                onValueChange = { q = it },
                                label = { Text("Buscar (RUTA / Empresa / Derrotero)") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (results.isEmpty()) {
                                Text(
                                    "Sin resultados (escribe al menos 2 caracteres). Si no existe, créala en la pestaña 'Crear nueva'.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            } else {
                                results.take(8).forEach { r ->
                                    ElevatedCard(
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = {
                                            vm.assignExistingMasterToPoi(
                                                poiKey = s.poiKey,
                                                routeUid = r.routeUid,
                                                onDone = {
                                                    showAddCatalog = false
                                                    errorMsg = null
                                                },
                                                onError = { msg -> errorMsg = msg }
                                            )
                                        }
                                    ) {
                                        Column(Modifier.padding(10.dp)) {
                                            Text(r.ruta, style = MaterialTheme.typography.titleSmall)
                                            Text(r.numeroRutaEmpresa, style = MaterialTheme.typography.bodySmall)
                                            Text(r.derroteroLetrero, style = MaterialTheme.typography.bodySmall)
                                            Text("Toca para asignar a este POI", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        } else {
                            OutlinedTextField(rutaNew, { rutaNew = it }, label = { Text("RUTA") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(empresaNew, { empresaNew = it }, label = { Text("Numero de Ruta/Empresa") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(derroteroNew, { derroteroNew = it }, label = { Text("Derrotero/Letrero") }, modifier = Modifier.fillMaxWidth())

                            Button(
                                onClick = {
                                    vm.createNewMasterAndAssign(
                                        poiKey = s.poiKey,
                                        ruta = rutaNew,
                                        empresa = empresaNew,
                                        derrotero = derroteroNew,
                                        createdBy = s.supervisor ?: s.aforador,
                                        onDone = {
                                            showAddCatalog = false
                                            rutaNew = ""
                                            empresaNew = ""
                                            derroteroNew = ""
                                            errorMsg = null
                                        },
                                        onError = { msg -> errorMsg = msg }
                                    )
                                },
                                enabled = rutaNew.isNotBlank() && empresaNew.isNotBlank() && derroteroNew.isNotBlank(),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Crear y asignar a este POI") }
                        }
                    }
                }
            )
        }
    }
}
