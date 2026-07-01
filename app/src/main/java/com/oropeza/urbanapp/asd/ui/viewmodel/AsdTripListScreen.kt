package com.oropeza.urbanapp.asd.ui.viewmodel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oropeza.urbanapp.asd.AsdGraph
import com.oropeza.urbanapp.asd.data.local.AsdTripSyncStatus
import com.oropeza.urbanapp.asd.data.local.Trip
import com.oropeza.urbanapp.asd.location.TrackingService
import com.oropeza.urbanapp.core.runtime.UrbanRuntime
import com.oropeza.urbanapp.BuildConfig
import com.oropeza.urbanapp.core.events.UrbanEventFactory
import com.oropeza.urbanapp.core.events.UrbanEventTypes
import com.oropeza.urbanapp.ui.components.*
import com.oropeza.urbanapp.ui.theme.LocalAforaColors
import com.oropeza.urbanapp.ui.theme.LocalAforaTypography
import com.oropeza.urbanapp.ui.theme.UrbanAppTheme
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.tooling.preview.Preview

class AsdTripListVM : ViewModel() {
    val trips = AsdGraph.repo.tripsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val pendingSyncCount = UrbanRuntime.syncStatus().pendingSyncCountFlow()
    val failedSyncCount = UrbanRuntime.syncStatus().failedSyncCountFlow()

    private val _activeTripToRecover = MutableStateFlow<Trip?>(null)
    val activeTripToRecover: StateFlow<Trip?> = _activeTripToRecover.asStateFlow()

    init {
        checkForRecovery()
    }

    fun checkForRecovery() {
        viewModelScope.launch {
            val active = AsdGraph.repo.getActiveTripOnce()
            if (active != null && !TrackingService.isRunning) {
                _activeTripToRecover.value = active
                UrbanRuntime.publishEvent(UrbanEventFactory.platform(
                    UrbanEventTypes.RECOVERY_REQUIRED,
                    null,
                    mapOf("tripId" to active.tripId)
                ))
            } else {
                _activeTripToRecover.value = null
            }
        }
    }

    fun resumeTracking(context: android.content.Context, tripId: Long) {
        if (TrackingService.isRunning) return
        
        val intent = android.content.Intent(context, TrackingService::class.java).apply {
            action = TrackingService.ACTION_START
            putExtra(TrackingService.EXTRA_TRIP_ID, tripId)
        }
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }

        _activeTripToRecover.value = null
        
        viewModelScope.launch {
            UrbanRuntime.publishEvent(UrbanEventFactory.platform(
                UrbanEventTypes.RECOVERY_RESUMED,
                null,
                mapOf("tripId" to tripId)
            ))
        }
    }

    fun syncStatusFlow(tripId: Long) = AsdGraph.repo.getTripSyncStatusFlow(tripId)
}

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
    val activeTripToRecover by vm.activeTripToRecover.collectAsState()
    val context = LocalContext.current

    AsdTripListContent(
        trips = trips,
        pendingSyncCount = pendingCount,
        failedSyncCount = failedCount,
        activeTripToRecover = activeTripToRecover,
        onNewTrip = onNewTrip,
        onOpenTrip = onOpenTrip,
        onOpenMap = onOpenMap,
        onBackHome = onBackHome,
        onResumeTracking = { id -> vm.resumeTracking(context, id) },
        getSyncStatusFlow = { id -> vm.syncStatusFlow(id) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AsdTripListContent(
    trips: List<Trip>,
    pendingSyncCount: Int,
    failedSyncCount: Int,
    activeTripToRecover: Trip?,
    onNewTrip: () -> Unit,
    onOpenTrip: (Long) -> Unit,
    onOpenMap: (Long) -> Unit,
    onBackHome: () -> Unit,
    onResumeTracking: (Long) -> Unit,
    getSyncStatusFlow: (Long) -> Flow<AsdTripSyncStatus>
) {
    val context = LocalContext.current
    val colors = LocalAforaColors.current
    val typography = LocalAforaTypography.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    
    var showDiag by remember { mutableStateOf(false) }
    var diagText by remember { mutableStateOf("") }

    Scaffold(
        containerColor = colors.Background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.Surface,
                    titleContentColor = colors.Secondary
                ),
                title = { Text("LEVANTAMIENTOS", style = typography.Headline, fontWeight = FontWeight.Black) },
                navigationIcon = {
                    TextButton(onClick = onBackHome) { 
                        Text("INICIO", color = colors.Primary, fontWeight = FontWeight.Bold) 
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                           diagText = UrbanRuntime.diagnosticsText(context)
                           showDiag = true
                        }
                    }) {
                        Text("ESTADO", style = typography.Label, color = colors.Secondary.copy(alpha = 0.6f))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewTrip,
                containerColor = colors.Primary,
                contentColor = colors.OnPrimary,
                shape = MaterialTheme.shapes.extraSmall
            ) { 
                Icon(Icons.Default.Add, contentDescription = "Nuevo Levantamiento")
            }
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
        ) {
            
            // Operational Recovery Banner
            activeTripToRecover?.let { trip ->
                AforaOperationalCard(
                    modifier = Modifier.padding(16.dp),
                    containerColor = colors.Danger.copy(alpha = 0.05f),
                    borderAlpha = 0.4f
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "ATENCIÓN: RASTREO DETENIDO",
                            style = typography.Title,
                            color = colors.Danger,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            "Hay un levantamiento activo (${trip.routeName}) que no está registrando datos. Reanude para no perder información.",
                            style = typography.BodySmall,
                            color = colors.Secondary.copy(alpha = 0.8f)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = { onResumeTracking(trip.tripId) },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.Danger),
                                shape = MaterialTheme.shapes.extraSmall,
                                modifier = Modifier.height(44.dp)
                            ) {
                                Text("REANUDAR LEVANTAMIENTO", style = typography.Label, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { onOpenTrip(trip.tripId) },
                                shape = MaterialTheme.shapes.extraSmall,
                                modifier = Modifier.height(44.dp),
                                border = BorderStroke(1.dp, colors.Outline)
                            ) {
                                Text("VER DETALLES", style = typography.Label, color = colors.Secondary)
                            }
                        }
                    }
                }
            }

            CloudSyncStatusCard(
                pendingCount = pendingSyncCount,
                failedCount = failedSyncCount,
                onSyncNow = {
                    scope.launch {
                        val isOnline = UrbanRuntime.diagnostics(context).isNetworkAvailable
                        if (!isOnline) {
                            snackbarHostState.showSnackbar("Sin conexión, el respaldo queda pendiente")
                            return@launch
                        }
                        val res = UrbanRuntime.syncNow(context)
                        if (res.isSuccess) {
                            snackbarHostState.showSnackbar("Tus datos están seguros ✅")
                        } else {
                            snackbarHostState.showSnackbar("Error al sincronizar con la nube")
                        }
                    }
                }
            )

            if (showDiag) {
                AlertDialog(
                    onDismissRequest = { showDiag = false },
                    title = { Text("ESTADO DEL EQUIPO", style = typography.Title) },
                    text = {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            Text(diagText, style = typography.Data)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { 
                            clipboardManager.setText(AnnotatedString(diagText))
                            scope.launch { snackbarHostState.showSnackbar("Copiado al portapapeles") }
                        }) { Text("COPIAR", fontWeight = FontWeight.Bold) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDiag = false }) { Text("CERRAR") }
                    }
                )
            }

            if (trips.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            "NO HAY LEVANTAMIENTOS",
                            style = typography.Title,
                            color = colors.Secondary.copy(alpha = 0.4f),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Inicia un nuevo levantamiento usando el botón inferior.",
                            style = typography.BodySmall,
                            color = colors.Secondary.copy(alpha = 0.4f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        AforaSectionHeader("HISTORIAL RECIENTE")
                    }
                    items(trips) { trip ->
                        TripCard(
                            trip = trip,
                            syncStatusFlow = getSyncStatusFlow(trip.tripId),
                            onClick = { onOpenTrip(trip.tripId) },
                            onOpenMap = { onOpenMap(trip.tripId) }
                        )
                    }
                }
            }
            
            AforaMetadataRow(
                label = "VERSIÓN",
                value = "v${BuildConfig.VERSION_NAME}",
                modifier = Modifier.padding(16.dp),
                valueColor = colors.Secondary.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
private fun CloudSyncStatusCard(
    pendingCount: Int,
    failedCount: Int,
    onSyncNow: () -> Unit
) {
    val colors = LocalAforaColors.current
    val typography = LocalAforaTypography.current
    
    AforaOperationalCard(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        containerColor = colors.Surface
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("RESPALDO EN LA NUBE", style = typography.Label, fontWeight = FontWeight.Bold, color = colors.Secondary.copy(alpha = 0.6f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("PENDIENTES", style = typography.Label, color = if (pendingCount > 0) colors.Warning else colors.Secondary.copy(alpha = 0.4f))
                    Text(
                        "$pendingCount", 
                        style = typography.Headline, 
                        fontWeight = FontWeight.Black,
                        color = if (pendingCount > 0) colors.Warning else colors.Secondary
                    )
                }
                
                if (failedCount > 0) {
                    Column {
                        Text("CON ERROR", style = typography.Label, color = colors.Danger)
                        Text("$failedCount", style = typography.Headline, fontWeight = FontWeight.Black, color = colors.Danger)
                    }
                }

                Button(
                    onClick = onSyncNow,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.Primary),
                    shape = MaterialTheme.shapes.extraSmall,
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("SINCRONIZAR AHORA", style = typography.Label, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun TripCard(
    trip: Trip,
    syncStatusFlow: Flow<AsdTripSyncStatus>,
    onClick: () -> Unit,
    onOpenMap: () -> Unit
) {
    val colors = LocalAforaColors.current
    val typography = LocalAforaTypography.current
    val syncStatus by syncStatusFlow.collectAsState(initial = AsdTripSyncStatus.NOT_QUEUED)

    val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val start = fmt.format(Date(trip.startTime))
    val end = trip.endTime?.let { fmt.format(Date(it)) } ?: "EN CURSO"

    AforaOperationalCard(
        modifier = Modifier.clickable { onClick() }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    trip.routeName.uppercase(), 
                    style = typography.Title, 
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.Secondary,
                    modifier = Modifier.weight(1f)
                )
                
                if (syncStatus != AsdTripSyncStatus.NOT_QUEUED) {
                    SyncStatusChip(syncStatus)
                }
            }
            
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AforaMetadataRow(label = "UNIDAD", value = "ECO ${trip.vehicleEco ?: "-"} • ${trip.plateNumber ?: "-"}")
                AforaMetadataRow(label = "INICIO", value = start)
                AforaMetadataRow(label = "FIN", value = end, valueColor = if (trip.endTime == null) colors.Primary else colors.Secondary)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onClick,
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = MaterialTheme.shapes.extraSmall,
                    border = BorderStroke(1.dp, colors.Outline)
                ) {
                    Text("DETALLES", style = typography.Label, color = colors.Secondary, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onOpenMap,
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = MaterialTheme.shapes.extraSmall,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.Secondary)
                ) {
                    Text("MAPA", style = typography.Label, color = colors.Surface, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SyncStatusChip(status: AsdTripSyncStatus) {
    val colors = LocalAforaColors.current
    val (text, color) = when (status) {
        AsdTripSyncStatus.PENDING -> "PENDIENTE" to colors.Warning
        AsdTripSyncStatus.IN_PROGRESS -> "ENVIANDO" to colors.Primary
        AsdTripSyncStatus.SYNCED -> "GUARDADO" to colors.Success
        AsdTripSyncStatus.FAILED -> "ERROR" to colors.Danger
        AsdTripSyncStatus.PARTIAL -> "PARCIAL" to Color(0xFF9C27B0)
        else -> "DESC" to Color.Gray
    }

    AforaStatusChip(text = text, color = color)
}

@Preview(showBackground = true)
@Composable
fun AsdTripListScreenPreview() {
    UrbanAppTheme {
        AsdTripListContent(
            trips = listOf(
                Trip(tripId = 1, routeName = "RECOLECCIÓN CENTRO", direction = "SUR", startTime = System.currentTimeMillis() - 3600000, vehicleEco = "E-101", plateNumber = "ABC-123", planningRouteId = "R1"),
                Trip(tripId = 2, routeName = "RUTA PERIFÉRICO", direction = "NORTE", startTime = System.currentTimeMillis() - 7200000, endTime = System.currentTimeMillis() - 3600000, vehicleEco = "E-202", plateNumber = "XYZ-789", planningRouteId = "R2")
            ),
            pendingSyncCount = 1,
            failedSyncCount = 0,
            activeTripToRecover = null,
            onNewTrip = {},
            onOpenTrip = {},
            onOpenMap = {},
            onBackHome = {},
            onResumeTracking = {},
            getSyncStatusFlow = { flowOf(AsdTripSyncStatus.SYNCED) }
        )
    }
}
