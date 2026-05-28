package com.oropeza.urbanapp.fov.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oropeza.urbanapp.asd.data.local.DbProvider
import com.oropeza.urbanapp.fov.importer.FovExcelImporter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FovSessionListScreen(
    onNew: () -> Unit,
    onOpen: (Long) -> Unit,
    onBackHome: () -> Unit,
    vm: FovSessionVm = viewModel()
) {
    // ✅ Ahora consumimos sesiones + conteo de catálogo
    val sessions by vm.sessions.collectAsState(initial = emptyList())

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult

        scope.launch {
            try {
                val db = DbProvider.getInstance(context.applicationContext)
                val importer = FovExcelImporter(context.applicationContext, db)
                val result = importer.importFromXlsx(uri)

                val msg =
                    "Import OK ✅ masters=${result.mastersUpserted}, " +
                            "poiItems=${result.poiItemsUpserted}, " +
                            "leídas=${result.rowsRead}, omitidas=${result.rowsSkipped}, " +
                            "colisiones=${result.collisionsResolved}"

                snackbarHostState.showSnackbar(msg)
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Error importando Excel: ${e.message ?: "desconocido"}")
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("FOV - Sesiones") },
                navigationIcon = { IconButton(onClick = onBackHome) { Text("←") } },
                actions = {
                    TextButton(
                        onClick = {
                            importLauncher.launch(
                                arrayOf(
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                    "application/octet-stream"
                                )
                            )
                        }
                    ) { Text("Importar Excel") }

                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onNew) { Text("Nueva") }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (sessions.isEmpty()) {
                Text("No hay sesiones aún.")
            } else {
                sessions.forEach { row ->
                    val s = row.s
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(s.sessionId) }
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "${s.estacion} | ${s.ubicacion} | ${s.sentido}",
                                style = MaterialTheme.typography.titleMedium
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("POI: ${s.poiKey}", style = MaterialTheme.typography.bodySmall)
                                Text("Catálogo: ${row.catalogCount} rutas", style = MaterialTheme.typography.bodySmall)
                            }

                            Text("Creada: ${s.createdAt}", style = MaterialTheme.typography.bodySmall)
                            if (s.endedAt != null) Text("Cerrada", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
