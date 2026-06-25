package com.oropeza.urbanapp.cc.ui

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oropeza.urbanapp.cc.viewmodel.CcDetailVM
import com.oropeza.urbanapp.asd.data.local.CcEvent
import com.oropeza.urbanapp.asd.data.local.CcSession
import com.oropeza.urbanapp.asd.export.CcExcelExporter
import com.oropeza.urbanapp.asd.export.CsvExporter
import com.oropeza.urbanapp.asd.location.LocationFix
import com.oropeza.urbanapp.asd.location.LocationProvider
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private fun gpsQuality(accM: Double): String = when {
    accM <= 10.0 -> "OK"
    accM <= 25.0 -> "USABLE"
    else -> "MALO"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CcSessionDetailScreen(
    sessionId: Long,
    onBack: () -> Unit,
    vm: CcDetailVM = viewModel()
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val gps = remember { LocationProvider(context) }

    val session by vm.ccSessionFlow(sessionId).collectAsState(initial = null)
    val events by vm.ccEventsFlow(sessionId).collectAsState(initial = emptyList())
    val snackbarHostState = remember { SnackbarHostState() }

    // ===== Permisos GPS =====
    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {}

    fun requestPermsIfNeeded(): Boolean {
        if (!gps.hasPermission()) {
            permLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return false
        }
        return true
    }

    // ===== Export CSV =====
    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val s = session ?: return@launch
            CsvExporter.exportCcLayout(context, uri, s, events)
            snackbarHostState.showSnackbar("CC exportado ✅")
        }
    }

    // ===== Export XLSX =====
    val exportXlsxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val s = session ?: return@launch
            CcExcelExporter.exportCcXlsx(context, uri, s, events)
            snackbarHostState.showSnackbar("Excel exportado ✅")
        }
    }

    // ===== Menú ⋮ + terminar sesión =====
    var menuOpen by remember { mutableStateOf(false) }
    var showEndDialog by remember { mutableStateOf(false) }

    // ============================================================
    // ✅ GPS CACHE INTERNO (no UI)
    // ============================================================
    var cachedFix by remember { mutableStateOf<LocationFix?>(null) }
    var cachedFixAtMs by remember { mutableStateOf(0L) }

    val s = session
    val isClosed = s?.endedAt != null

    LaunchedEffect(s?.sessionId, isClosed) {
        cachedFix = null
        cachedFixAtMs = 0L

        if (s == null) return@LaunchedEffect
        if (isClosed) return@LaunchedEffect
        if (!gps.hasPermission()) return@LaunchedEffect

        gps.locationUpdates(
            intervalMs = 1200L,
            minUpdateMs = 600L,
            minDistanceM = 0f,
            maxWaitTimeMs = 0L,
            highAccuracy = true
        ).collect { loc ->
            val fix = gps.locationToFixWithStatus(
                loc = loc,
                targetAccM = 10.0,
                fallbackAccM = 25.0
            )
            cachedFix = fix
            cachedFixAtMs = System.currentTimeMillis()
        }
    }

    suspend fun pickFixFast(): LocationFix {
        val now = System.currentTimeMillis()
        val cached = cachedFix
        val age = now - cachedFixAtMs
        if (cached != null && age <= 4_000L) return cached

        return gps.getBestFixForEvent(
            targetAccM = 10.0,
            fallbackAccM = 25.0,
            timeoutMs = 2_000L,
            highAccuracy = true
        )
    }

    // ============================================================
    // ✅ Estado del panel (HOISTED)
    // - Se limpia después de guardar
    // - Se rellena al tocar un evento
    // ============================================================
    var mode by remember { mutableStateOf("LLEGADA") }

    var plate by remember { mutableStateOf("") }
    var eco by remember { mutableStateOf("") }
    var vehicleType by remember { mutableStateOf("") }
    var paxStr by remember { mutableStateOf("0") }
    var luggageStr by remember { mutableStateOf("0") }

    // Si hay eventos, NO cambiamos el modo automáticamente. (tu regla: 1 observador por sentido)
    // Solo lo cambia el usuario o tocar un registro.

    fun clearPanelKeepMode(base: String) {
        plate = ""
        eco = ""
        vehicleType = ""
        paxStr = "0"
        luggageStr = "0"
        // modo se conserva
    }

    fun prefillFromEventAndFlipMode(e: CcEvent, base: String) {
        plate = e.plate ?: ""
        eco = e.eco ?: ""
        vehicleType = e.vehicleType ?: ""
        paxStr = e.pax.toString()
        luggageStr = (e.luggageCount ?: 0).toString()

        mode = if (e.eventType == "LLEGADA") "SALIDA" else "LLEGADA"
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("CC • Sesión") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Atrás") } },
                actions = {
                    TextButton(
                        onClick = {
                            val ss = session ?: return@TextButton
                            val name = "CC_${ss.planningId}_${ss.derrotero}_${ss.base}_${ss.direction}.csv"
                                .replace(" ", "_")
                                .replace("/", "-")
                            exportCsvLauncher.launch(name)
                        }
                    ) { Text("CSV") }

                    TextButton(
                        onClick = {
                            val ss = session ?: return@TextButton
                            val name = "CC_${ss.planningId}_${ss.derrotero}_${ss.base}_${ss.direction}.xlsx"
                                .replace(" ", "_")
                                .replace("/", "-")
                            exportXlsxLauncher.launch(name)
                        }
                    ) { Text("Excel") }

                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menú")
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Terminar sesión") },
                                enabled = session != null && !isClosed,
                                onClick = {
                                    menuOpen = false
                                    showEndDialog = true
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier.padding(pad).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (session == null) {
                Text("Cargando sesión…")
                return@Column
            }

            val ss = session!!
            val baseUpper = ss.base.uppercase(Locale.ROOT)

            SessionHeader(ss)

            if (isClosed) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Sesión TERMINADA", style = MaterialTheme.typography.titleMedium)
                        Text("Captura bloqueada. Puedes exportar cuando quieras.")
                    }
                }
            } else {
                if (requestPermsIfNeeded()) {
                    CcQuickCapturePanel(
                        base = ss.base,
                        mode = mode,
                        onModeChange = { mode = it },

                        plate = plate,
                        onPlateChange = { plate = it },
                        eco = eco,
                        onEcoChange = { eco = it },
                        vehicleType = vehicleType,
                        onVehicleTypeChange = { vehicleType = it },
                        paxStr = paxStr,
                        onPaxChange = { paxStr = it },
                        luggageStr = luggageStr,
                        onLuggageChange = { luggageStr = it }
                    ) { draft ->
                        scope.launch {
                            val fix = pickFixFast()
                            val seq = vm.nextCcSeq(ss.sessionId)

                            vm.addCcEvent(
                                CcEvent(
                                    sessionId = ss.sessionId,
                                    seqInSession = seq,
                                    eventType = draft.eventType,
                                    timeMs = draft.timeMs,
                                    timeIsManual = draft.timeIsManual,
                                    plate = draft.plate.ifBlank { null },
                                    eco = draft.eco.ifBlank { null },
                                    vehicleType = draft.vehicleType.ifBlank { null },
                                    pax = draft.pax,
                                    luggageCount = draft.luggage,
                                    lat = fix.lat,
                                    lon = fix.lon,
                                    accM = fix.accM,
                                    provider = fix.provider,
                                    fixTime = fix.fixTime,
                                    locationStatus = fix.status
                                )
                            )

                            // ✅ Limpiar panel, mantener modo
                            clearPanelKeepMode(baseUpper)
                        }
                    }
                }
            }

            Divider()
            Text("Eventos (${events.size})", style = MaterialTheme.typography.titleMedium)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(events) { e ->
                    EventRow(
                        e = e,
                        onClick = {
                            // ✅ Rellenar panel + modo contrario
                            prefillFromEventAndFlipMode(e, baseUpper)
                        }
                    )
                }
            }
        }
    }

    if (showEndDialog && session != null) {
        EndSessionDialog(
            onDismiss = { showEndDialog = false },
            onConfirm = {
                scope.launch {
                    vm.endCcSession(session!!.sessionId)
                    snackbarHostState.showSnackbar("Sesión terminada ✅")
                }
                showEndDialog = false
            }
        )
    }
}

@Composable
private fun EndSessionDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    val ok = text.trim().uppercase(Locale.ROOT) == "TERMINAR"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Terminar sesión") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Esto cerrará la sesión y BLOQUEARÁ la captura.")
                Text("Para confirmar, escribe: TERMINAR")
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Escribe TERMINAR") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { Button(enabled = ok, onClick = onConfirm) { Text("Sí, terminar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun SessionHeader(s: CcSession) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("ID ${s.planningId} • ${s.base} • ${s.direction}", style = MaterialTheme.typography.titleMedium)
            Text("${s.derrotero} • ${s.companyName}")
            Text("${s.terminalOrigin} → ${s.terminalDestination}")
            Text("${s.locationName} • ${s.aforador}")
        }
    }
}

@Composable
private fun EventRow(
    e: CcEvent,
    onClick: () -> Unit
) {
    val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale("es", "MX")) }
    val q = gpsQuality(e.accM)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("#${e.seqInSession} • ${e.eventType} • ${fmt.format(Date(e.timeMs))}", style = MaterialTheme.typography.titleSmall)
            Text("Placa: ${e.plate ?: "-"} • Eco: ${e.eco ?: "-"} • Tipo: ${e.vehicleType ?: "-"}")
            Text("Pax: ${e.pax} • Maletero: ${e.luggageCount ?: "-"}")
            Text("GPS: ${"%.0f".format(e.accM)}m • $q • ${e.locationStatus}")
            if (e.timeIsManual) Text("Hora: MANUAL")
        }
    }
}
