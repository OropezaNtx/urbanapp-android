package com.oropeza.urbanapp.asd.ui.viewmodel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oropeza.urbanapp.asd.AsdGraph
import com.oropeza.urbanapp.asd.data.local.Trip
import com.oropeza.urbanapp.asd.sync.AsdCatalogFirestoreSync
import com.oropeza.urbanapp.core.runtime.UrbanRuntime
import com.oropeza.urbanapp.BuildConfig
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.material3.ExperimentalMaterial3Api
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class AsdTripListVM : ViewModel() {
    val trips = AsdGraph.repo.tripsFlow.stateIn(
        scope = CoroutineScope(Dispatchers.Main),
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val pendingSyncCount = UrbanRuntime.syncStatus().pendingSyncCountFlow()
    val failedSyncCount = UrbanRuntime.syncStatus().failedSyncCountFlow()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AsdTripListScreen(
    onNewTrip: () -> Unit,
    onOpenTrip: (Long) -> Unit,
    onOpenMap: (Long) -> Unit,
    onBackHome: () -> Unit,
    vm: AsdTripListVM = viewModel()
) {
    val trips by vm.trips.collectAsState()
    val pendingCount by vm.pendingSyncCount.collectAsState(initial = 0)
    val failedCount by vm.failedSyncCount.collectAsState(initial = 0)
    val context = androidx.compose.ui.platform.LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val shortId = remember { UrbanRuntime.getShortInstallationId(context) }
    val clipboardManager = LocalClipboardManager.current
    
    var showDiag by remember { mutableStateOf(false) }
    var diagText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        runCatching {
            AsdCatalogFirestoreSync.checkVersionAndSyncIfNeeded()
        }
        // ✅ UrbanRuntime: Initial cloud identity and heartbeat
        runCatching {
            UrbanRuntime.syncInstallation(context)
            UrbanRuntime.syncHeartbeat(context)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("ASD - Viajes") },
                navigationIcon = {
                    TextButton(onClick = onBackHome) { Text("Home") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewTrip) { Text("+") }
        }
    ) { pad ->
        Column(modifier = Modifier.padding(pad).fillMaxSize()) {
            CloudSyncStatusCard(
                shortId = shortId,
                pendingCount = pendingCount,
                failedCount = failedCount,
                onSyncNow = {
                    scope.launch {
                        UrbanRuntime.syncHeartbeat(context)
                        snackbarHostState.showSnackbar("Sincronización solicitada")
                    }
                },
                onShowDiagnostics = {
                    scope.launch {
                        diagText = UrbanRuntime.diagnosticsText(context)
                        showDiag = true
                    }
                }
            )

            if (showDiag) {
                AlertDialog(
                    onDismissRequest = { showDiag = false },
                    title = { Text("Diagnóstico de Plataforma") },
                    text = { 
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            Text(diagText, style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { 
                            clipboardManager.setText(AnnotatedString(diagText))
                            scope.launch { snackbarHostState.showSnackbar("Copiado al portapapeles") }
                        }) { Text("COPIAR") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDiag = false }) { Text("CERRAR") }
                    }
                )
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(trips) { trip ->
                    TripCard(
                        trip = trip,
                        onClick = { onOpenTrip(trip.tripId) },
                        onOpenMap = { onOpenMap(trip.tripId) }
                    )
                }
            }
            
            Text(
                text = "v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier
                    .padding(8.dp)
                    .align(androidx.compose.ui.Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun CloudSyncStatusCard(
    shortId: String,
    pendingCount: Int,
    failedCount: Int,
    onSyncNow: () -> Unit,
    onShowDiagnostics: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text("Estado nube", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                TextButton(onClick = onShowDiagnostics) {
                    Text(
                        "ID: $shortId",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column {
                    Text("Pendientes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    Text(pendingCount.toString(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.ExtraBold)
                }
                Column {
                    Text("Fallidos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    Text(
                        failedCount.toString(),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (failedCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onSyncNow,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Sincronizar ahora", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun TripCard(
    trip: Trip,
    onClick: () -> Unit,
    onOpenMap: () -> Unit
) {
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale("es", "MX")) }
    val start = fmt.format(Date(trip.startTime))
    val end = trip.endTime?.let { fmt.format(Date(it)) } ?: "EN CURSO"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(trip.routeName, style = MaterialTheme.typography.titleMedium)
            Text(
                "No. ${trip.routeNumber?.toString() ?: "-"} • ${trip.direction}  •  ${trip.company ?: "-"}  •  Eco: ${trip.vehicleEco ?: "-"}  •  Placa: ${trip.plateNumber ?: "-"}"
            )
            Text("Inicio: $start  •  Fin: $end", style = MaterialTheme.typography.bodySmall)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onClick) {
                    Text("Detalle")
                }
                Button(onClick = onOpenMap) {
                    Text("Mapa")
                }
            }
        }
    }
}
