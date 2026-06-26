package com.oropeza.urbanapp.asd.ui.viewmodel

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oropeza.urbanapp.asd.AsdGraph
import com.oropeza.urbanapp.asd.data.local.AsdTripSyncStatus
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

    fun syncStatusFlow(tripId: Long) = AsdGraph.repo.getTripSyncStatusFlow(tripId)
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("ASD - Viajes") },
                navigationIcon = {
                    TextButton(onClick = onBackHome) { Text("Home") }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                           diagText = UrbanRuntime.diagnosticsText(context)
                           showDiag = true
                        }
                    }) {
                        Text("DIAG", style = MaterialTheme.typography.labelSmall)
                    }
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
                        val res = UrbanRuntime.syncNow(context)
                        if (res.isSuccess) {
                            snackbarHostState.showSnackbar("Sincronización terminada ✅")
                        } else {
                            snackbarHostState.showSnackbar("Error al sincronizar")
                        }
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
                    title = { Text("Diagnósticos de Plataforma") },
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
                        vm = vm,
                        onClick = { onOpenTrip(trip.tripId) },
                        onOpenMap = { onOpenMap(trip.tripId) }
                    )
                }
            }
            
            Text(
                text = "v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
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
        modifier = Modifier.padding(12.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Estado nube", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp)) {
                    Text("ID: $shortId", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp).clickable { onShowDiagnostics() }, style = MaterialTheme.typography.labelSmall)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column {
                    Text("Pendientes", style = MaterialTheme.typography.labelSmall)
                    Text("$pendingCount", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("Fallidos", style = MaterialTheme.typography.labelSmall)
                    Text("$failedCount", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = if (failedCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = onSyncNow) {
                    Text("Sincronizar ahora")
                }
            }
        }
    }
}

@Composable
private fun TripCard(
    trip: Trip,
    vm: AsdTripListVM,
    onClick: () -> Unit,
    onOpenMap: () -> Unit
) {
    val syncStatus by vm.syncStatusFlow(trip.tripId).collectAsState(initial = AsdTripSyncStatus.NOT_QUEUED)

    val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale("es", "MX")) }
    val start = fmt.format(Date(trip.startTime))
    val end = trip.endTime?.let { fmt.format(Date(it)) } ?: "EN CURSO"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(trip.routeName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                
                if (syncStatus != AsdTripSyncStatus.NOT_QUEUED) {
                    SyncStatusChip(syncStatus)
                }
            }
            Text(
                "No. ${trip.routeNumber?.toString() ?: "-"} • ${trip.direction}  •  ${trip.company ?: "-"}  •  Eco: ${trip.vehicleEco ?: "-"}  •  Placa: ${trip.plateNumber ?: "-"}",
                style = MaterialTheme.typography.bodySmall
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

@Composable
private fun SyncStatusChip(status: AsdTripSyncStatus) {
    val (text, color) = when (status) {
        AsdTripSyncStatus.PENDING -> "PENDIENTE" to Color(0xFFFFB300)
        AsdTripSyncStatus.IN_PROGRESS -> "SINCRONIZANDO" to Color(0xFF2979FF)
        AsdTripSyncStatus.SYNCED -> "SINCRONIZADO" to Color(0xFF35D36B)
        AsdTripSyncStatus.FAILED -> "ERROR" to Color(0xFFF44336)
        AsdTripSyncStatus.PARTIAL -> "PARCIAL" to Color(0xFF9C27B0)
        else -> "DESCONOCIDO" to Color.Gray
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("☁", color = color, fontSize = 10.sp, modifier = Modifier.offset(y = (-1).dp))
            Text(
                text = text,
                color = color,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
