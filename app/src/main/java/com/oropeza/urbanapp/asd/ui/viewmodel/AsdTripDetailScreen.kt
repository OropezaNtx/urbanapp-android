package com.oropeza.urbanapp.asd.ui.viewmodel

import android.util.Log
import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oropeza.urbanapp.asd.AsdGraph
import com.oropeza.urbanapp.asd.data.local.AsdTripSyncStatus
import com.oropeza.urbanapp.asd.data.local.AsdTripSyncDiagnostics
import com.oropeza.urbanapp.asd.data.local.StopEvent
import com.oropeza.urbanapp.asd.data.local.TrackPoint
import com.oropeza.urbanapp.asd.data.local.Trip
import com.oropeza.urbanapp.asd.export.*
import com.oropeza.urbanapp.asd.location.*
import com.oropeza.urbanapp.asd.domain.trip.TripDomain
import com.oropeza.urbanapp.asd.presentation.trip.TripActions
import com.oropeza.urbanapp.asd.sync.AsdCloudSyncWorker
import com.oropeza.urbanapp.core.events.UrbanEventFactory
import com.oropeza.urbanapp.core.events.UrbanEventTypes
import com.oropeza.urbanapp.core.runtime.UrbanRuntime
import com.oropeza.urbanapp.ui.components.*
import com.oropeza.urbanapp.ui.theme.LocalAforaColors
import com.oropeza.urbanapp.ui.theme.LocalAforaTypography
import com.oropeza.urbanapp.ui.theme.UrbanAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import com.oropeza.urbanapp.asd.export.TrackCsvExporter

class AsdTripDetailVM : ViewModel() {
    private val actions = TripActions()
    private val useCases = TripDomain.useCases

    fun tripFlow(tripId: Long) = AsdGraph.repo.tripFlow(tripId)
    fun stopsFlow(tripId: Long) = AsdGraph.repo.stopsFlow(tripId)
    fun trackLastPointFlow(tripId: Long): Flow<TrackPoint?> = AsdGraph.repo.trackLastPointFlow(tripId)
    fun trackCountFlow(tripId: Long): Flow<Int> = AsdGraph.repo.trackCountFlow(tripId)
    fun trackPointsFlow(tripId: Long): Flow<List<TrackPoint>> = AsdGraph.repo.trackPointsFlow(tripId)
    fun observersFlow(role: String) = AsdGraph.repo.activeAsdPeopleByRoleFlow(role)
    val vehicleTypesFlow = AsdGraph.repo.activeAsdVehicleTypesFlow()
    val syncPendingCount = AsdGraph.repo.syncQueuePendingCountFlow()
    val lastSyncTime = AsdGraph.repo.lastSyncTimeFlow()
    fun tripSyncStatusFlow(tripId: Long) = AsdGraph.repo.getTripSyncStatusFlow(tripId)
    fun tripSyncDiagnosticsFlow(tripId: Long) = AsdGraph.repo.getTripSyncDiagnosticsFlow(tripId)
    suspend fun getTripOnce(tripId: Long) = AsdGraph.repo.getTripOnce(tripId)
    suspend fun getStopsOnce(tripId: Long) = AsdGraph.repo.getStopsOnce(tripId)
    suspend fun getTrackPointsOnce(tripId: Long) = AsdGraph.repo.getTrackPointsOnce(tripId)
    suspend fun getTrackPointsBetweenOnce(tripId: Long, fromMs: Long, toMs: Long) = AsdGraph.repo.getTrackPointsBetweenOnce(tripId, fromMs, toMs)

    suspend fun updateTripHeader(tripId: Long, routeName: String, company: String?, vehicleEco: String?, direction: String, routeNumber: Int?, esFs: String?, baseStart: String?, baseEnd: String?, plateNumber: String?, vehicleType: String?, seatCapacity: Int?, notes: String?, aforador: String?, supervisor: String?, deviceNumber: String?, observerSex: String?): Boolean =
        useCases.updateHeader(tripId, routeName, company, vehicleEco, direction, routeNumber, esFs, baseStart, baseEnd, plateNumber, vehicleType, seatCapacity, notes, aforador, supervisor, deviceNumber, observerSex).isSuccess

    suspend fun export(context: Context, tripId: Long, type: String, uri: Uri): Boolean =
        actions.export(context, tripId, type, uri)

    suspend fun addStopDetailed(tripId: Long, stopType: String, stopTimeMs: Long, startTimeMs: Long, stopName: String?, notes: String?, menUp: Int, womenUp: Int, menDown: Int, womenDown: Int, hasLuggage: Boolean, delayCodes: String?, otherDelayDesc: String?, stopFix: LocationFix, startFix: LocationFix = stopFix) {
        useCases.addStop(tripId, stopType, stopTimeMs, startTimeMs, stopName, notes, menUp, womenUp, menDown, womenDown, hasLuggage, delayCodes, otherDelayDesc, stopFix, startFix)
    }

    suspend fun endTripWithFix(tripId: Long, fix: LocationFix): Boolean =
        actions.closeTrip(tripId, fix).isSuccess
}

private fun formatElapsed(totalSec: Long): String { val h = totalSec / 3600; val m = (totalSec % 3600) / 60; val s = totalSec % 60; return if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AsdTripDetailScreen(tripId: Long, onBack: () -> Unit, onOpenMap: (Long) -> Unit, vm: AsdTripDetailVM = viewModel()) {
    val context = LocalContext.current; val gps = remember { LocationProvider(context) }; val scope = rememberCoroutineScope()
    val trip by vm.tripFlow(tripId).collectAsState(initial = null)
    val stops by vm.stopsFlow(tripId).collectAsState(initial = emptyList())
    val lastPoint by vm.trackLastPointFlow(tripId).collectAsState(initial = null)
    val pointCount by vm.trackCountFlow(tripId).collectAsState(initial = 0)
    val trackPoints by vm.trackPointsFlow(tripId).collectAsState(initial = emptyList())
    val syncStatus by vm.tripSyncStatusFlow(tripId).collectAsState(initial = AsdTripSyncStatus.NOT_QUEUED)
    val syncDiagnostics by vm.tripSyncDiagnosticsFlow(tripId).collectAsState(initial = AsdTripSyncDiagnostics(0,0,0,0,0,0,null,null,null))
    val pendingSyncCount by vm.syncPendingCount.collectAsState(initial = 0)
    val lastSyncTimeMs by vm.lastSyncTime.collectAsState(initial = null)
    val runtimeGps by TrackingService.runtimeGpsState.collectAsState()
    fun sTS() { if (TrackingService.isRunning || !gps.hasPermission()) return; val intent = Intent(context, TrackingService::class.java).apply { action = TrackingService.ACTION_START; putExtra(TrackingService.EXTRA_TRIP_ID, tripId) }; if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent) }
    fun stopTS() { if (!TrackingService.isRunning) return; context.startService(Intent(context, TrackingService::class.java).apply { action = TrackingService.ACTION_STOP }) }
    AsdTripDetailContent(tripId, trip, stops, lastPoint, pointCount, trackPoints, syncStatus, syncDiagnostics, pendingSyncCount, lastSyncTimeMs, runtimeGps, onBack, onOpenMap, { t, st, stt, n, nt, mu, wu, md, wd, l, c, d, sf, stf -> scope.launch { vm.addStopDetailed(tripId, t, st, stt, n, nt, mu, wu, md, wd, l, c, d, sf, stf) } }, { f -> vm.endTripWithFix(tripId, f) }, { r, c, e, d, num, ef, bs, be, p, v, ca, nts, af, s, dev, sex -> vm.updateTripHeader(tripId, r, c, e, d, num, ef, bs, be, p, v, ca, nts, af, s, dev, sex) }, { type, uri -> vm.export(context, tripId, type, uri) }, gps, vm, { sTS() }, { stopTS() })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AsdTripDetailContent(tripId: Long, trip: Trip?, stops: List<StopEvent>, lastPoint: TrackPoint?, pointCount: Int, trackPoints: List<TrackPoint>, syncStatus: AsdTripSyncStatus, syncDiagnostics: AsdTripSyncDiagnostics, pendingSyncCount: Int, lastSyncTimeMs: Long?, runtimeGps: TrackingService.Companion.RuntimeGpsState, onBack: () -> Unit, onOpenMap: (Long) -> Unit, onAddStop: (String, Long, Long, String?, String?, Int, Int, Int, Int, Boolean, String?, String?, LocationFix, LocationFix) -> Unit, onEndTrip: suspend (LocationFix) -> Boolean, onUpdateHeader: suspend (String, String?, String?, String, Int?, String?, String?, String?, String?, String?, Int?, String?, String?, String?, String?, String?) -> Boolean, onExport: suspend (String, Uri) -> Boolean, gps: LocationProvider, vm: AsdTripDetailVM, startTS: () -> Unit, stopTS: () -> Unit) {
    val context = LocalContext.current; val colors = LocalAforaColors.current; val typography = LocalAforaTypography.current; val haptic = LocalHapticFeedback.current; val scope = rememberCoroutineScope()
    fun triggerHaptic() = haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    val stopsSummary by remember(stops) { derivedStateOf { AsdDemoSummary.from(stops, pointCount) } }
    val summary = remember(stopsSummary, pointCount) { stopsSummary.copy(trackPoints = pointCount) }
    val fmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()) }
    var menUp by rememberSaveable { mutableStateOf(0) }; var womenUp by rememberSaveable { mutableStateOf(0) }; var menDown by rememberSaveable { mutableStateOf(0) }; var womenDown by rememberSaveable { mutableStateOf(0) }; var selectedDelayCodes by rememberSaveable { mutableStateOf(setOf<String>()) }; var otherDelayDesc by rememberSaveable { mutableStateOf("") }; var hasLuggage by rememberSaveable { mutableStateOf(false) }; var stopName by rememberSaveable { mutableStateOf("") }; var notes by rememberSaveable { mutableStateOf("") }; var activeDelayStartMs by rememberSaveable { mutableStateOf(0L) }; var activeDelayLat by rememberSaveable { mutableStateOf(0.0) }; var activeDelayLon by rememberSaveable { mutableStateOf(0.0) }; var activeDelayAltM by rememberSaveable { mutableStateOf(0.0) }; var activeDelayAccM by rememberSaveable { mutableStateOf(0.0) }; var activeDelayProvider by rememberSaveable { mutableStateOf("") }; var activeDelayFixTime by rememberSaveable { mutableStateOf(0L) }; var activeDelayStatus by rememberSaveable { mutableStateOf("GPS_PENDING") }
    var tickMs by remember { mutableStateOf(System.currentTimeMillis()) }; var loadingGps by remember { mutableStateOf(false) }; var gpsMsg by remember { mutableStateOf<String?>(null) }; var snackbarHostState = remember { SnackbarHostState() }; var showCloseTripConfirm by remember { mutableStateOf(false) }; var showEditHeader by remember { mutableStateOf(false) }; var distanceKm by remember { mutableStateOf<Double?>(null) }; var distanceLoading by remember { mutableStateOf(false) }
    val isDelayActive = activeDelayStartMs > 0L; LaunchedEffect(trip?.endTime) { if (trip?.endTime == null) { while (true) { tickMs = System.currentTimeMillis(); kotlinx.coroutines.delay(1000L) } } }
    LaunchedEffect(tripId, trip?.endTime) {
        if (trip?.endTime == null) startTS() else stopTS()
    }
    fun currentFix(now: Long): LocationFix { val p = lastPoint; val ageMs = p?.let { now - it.timeMs } ?: Long.MAX_VALUE; val isRecent = p != null && ageMs <= 15_000L; val isAccurate = p != null && p.accM <= 45.0; val isValid = p != null && p.lat != 0.0 && p.lon != 0.0; return if (p != null && isValid && isRecent && isAccurate) LocationFix(p.lat, p.lon, p.accM, p.altM, p.provider, p.timeMs, "FIX_USABLE") else LocationFix(0.0, 0.0, 0.0, 0.0, "pending", now, "GPS_PENDING") }
    fun ensureActiveDelay() { if (isDelayActive || trip?.endTime != null) return; val now = System.currentTimeMillis(); val fix = currentFix(now); activeDelayStartMs = now; activeDelayLat = fix.lat; activeDelayLon = fix.lon; activeDelayAltM = fix.altM; activeDelayAccM = fix.accM; activeDelayProvider = fix.provider; activeDelayFixTime = fix.fixTime; activeDelayStatus = fix.status }
    fun resetCapture() { menUp = 0; womenUp = 0; menDown = 0; womenDown = 0; selectedDelayCodes = emptySet(); otherDelayDesc = ""; hasLuggage = false; stopName = ""; notes = ""; activeDelayStartMs = 0L }
    fun validateCapture(s: AsdDemoSummary, t: Trip?): String? { if ((menUp + womenUp + menDown + womenDown == 0) && selectedDelayCodes.isEmpty() && otherDelayDesc.isBlank()) return "Registro vacío."; val protM = if (t?.observerSex == "H") 1 else 0; val protW = if (t?.observerSex == "M") 1 else 0; if (menDown > (s.menOnBoard + menUp - protM).coerceAtLeast(0)) return "No se puede bajar al operador (H)."; if (womenDown > (s.womenOnBoard + womenUp - protW).coerceAtLeast(0)) return "No se puede bajar a la operadora (M)."; return null }
    fun saveEvent() {
    val err = validateCapture(summary, trip)
    if (err != null) {
        scope.launch { snackbarHostState.showSnackbar(err) }
        return
    }

    val now = System.currentTimeMillis()
    val endFix = currentFix(now)
    val startFix = LocationFix(
        activeDelayLat,
        activeDelayLon,
        activeDelayAccM,
        activeDelayAltM,
        activeDelayProvider.ifBlank { "pending" },
        if (activeDelayFixTime > 0L) activeDelayFixTime else now,
        activeDelayStatus
    )

    scope.launch {
        loadingGps = true
        gpsMsg = if (endFix.status == "GPS_PENDING") {
            "Guardando localmente; ubicación pendiente…"
        } else {
            "Guardando registro…"
        }
        try {
            vm.addStopDetailed(
                tripId,
                if (menUp + womenUp + menDown + womenDown > 0) "ASD" else "DEMORA",
                activeDelayStartMs,
                now,
                stopName.trim(),
                notes.trim(),
                menUp,
                womenUp,
                menDown,
                womenDown,
                hasLuggage,
                selectedDelayCodes.joinToString("/").ifBlank { null },
                otherDelayDesc.trim().ifBlank { null },
                startFix,
                endFix
            )
            resetCapture()
            snackbarHostState.showSnackbar(
                if (endFix.status == "GPS_PENDING")
                    "Registro guardado localmente; GPS pendiente ✅"
                else
                    "Registro guardado ✅"
            )
        } catch (t: Throwable) {
            snackbarHostState.showSnackbar("No se pudo guardar el registro: ${t.message ?: "error desconocido"}")
        } finally {
            loadingGps = false
            gpsMsg = null
        }
    }
}

    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let { scope.launch { onExport("CSV", it) } } }; val exportXlsx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { it?.let { scope.launch { onExport("CLIENT", it) } } }
    val exportTrackCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let { scope.launch { onExport("TRACK", it) } } }
    val exportGpsAuditCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let { scope.launch { onExport("GPS_AUDIT", it) } } }
    val exportGpx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { it?.let { scope.launch { onExport("GPX", it) } } }
    val exportKml = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.google-earth.kml+xml")) { it?.let { scope.launch { onExport("KML", it) } } }
    val exportGarminT = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { it?.let { scope.launch { onExport("GARMIN_T", it) } } }
    val exportGarminW = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { it?.let { scope.launch { onExport("GARMIN_W", it) } } }
    val vehicleTypesCatalog by vm.vehicleTypesFlow.collectAsState(initial = emptyList()); val capacityApplies = if (vehicleTypesCatalog.isNotEmpty()) vehicleTypesCatalog.any { it.name.equals(trip?.vehicleType, ignoreCase = true) && it.capacityApplies } else trip?.vehicleType?.uppercase()?.trim() in listOf("COMBI", "VAN", "SPRINTER"); val captureOnBoard = (summary.onBoard + menUp + womenUp - menDown - womenDown).coerceAtLeast(0); val exceedsCapacity = capacityApplies && trip?.seatCapacity != null && captureOnBoard > trip.seatCapacity

    Scaffold(containerColor = colors.Background, snackbarHost = { SnackbarHost(snackbarHostState) }, topBar = { TopAppBar(colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.Surface, titleContentColor = colors.Secondary, navigationIconContentColor = colors.Primary), title = { Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(text = (trip?.routeName ?: "ASD").uppercase(), style = typography.Headline, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)); TextButton(onClick = { triggerHaptic(); onOpenMap(tripId) }) { Text("MAPA", color = colors.Primary, fontWeight = FontWeight.Bold) } } }, navigationIcon = { TextButton(onClick = { triggerHaptic(); onBack() }) { Text("ATRÁS", color = colors.Primary, fontWeight = FontWeight.Bold) } }) }) { pad ->
        if (trip == null) { Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = colors.Primary) }; return@Scaffold }
        val isEnded = trip.endTime != null; val durationSec = ((trip.endTime ?: tickMs) - trip.startTime).coerceAtLeast(0) / 1000
        LazyColumn(modifier = Modifier.padding(pad).fillMaxSize().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { InlineAsdCaptureCard(isEnded, isDelayActive, (tickMs - activeDelayStartMs).coerceAtLeast(0) / 1000, menUp, womenUp, menDown, womenDown, summary.onBoard, summary.menOnBoard, summary.womenOnBoard, (summary.menOnBoard + menUp - (if (trip.observerSex == "H") 1 else 0)).coerceAtLeast(0), (summary.womenOnBoard + womenUp - (if (trip.observerSex == "M") 1 else 0)).coerceAtLeast(0), trip.seatCapacity, exceedsCapacity, selectedDelayCodes, otherDelayDesc, hasLuggage, stopName, notes, lastPoint, { ensureActiveDelay(); menUp = it }, { ensureActiveDelay(); womenUp = it }, { ensureActiveDelay(); menDown = it }, { ensureActiveDelay(); womenDown = it }, { code -> ensureActiveDelay(); selectedDelayCodes = if (selectedDelayCodes.contains(code)) selectedDelayCodes - code else selectedDelayCodes + code }, { ensureActiveDelay(); otherDelayDesc = it }, { ensureActiveDelay(); hasLuggage = it }, { ensureActiveDelay(); stopName = it }, { ensureActiveDelay(); notes = it }, { resetCapture() }, { saveEvent() }, { onOpenMap(tripId) }, { showCloseTripConfirm = true }, trip.observerSex) }
            item { AforaOperationalCard(modifier = Modifier.padding(horizontal = 16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { AforaSectionHeader("TIEMPO DEL LEVANTAMIENTO"); Text(formatElapsed(durationSec), style = typography.Headline, fontWeight = FontWeight.Black, color = colors.Secondary) }; AforaStatusChip(if (isEnded) "FINALIZADO" else "EN TIEMPO REAL", if (isEnded) colors.Danger else colors.Success) } } }
            item { TripHeaderCard(trip.routeName.uppercase(), trip.direction.uppercase(), fmt.format(Date(trip.startTime)), trip.endTime?.let { fmt.format(Date(it)) } ?: "EN CURSO", trip.vehicleEco, trip.plateNumber, trip.seatCapacity, trip.aforador, trip.supervisor, trip.deviceNumber, isEnded, { showEditHeader = true }) }
            item { val lastAgeMs = lastPoint?.let { tickMs - it.timeMs } ?: Long.MAX_VALUE; TrackingStatusCard(lastPoint != null && lastAgeMs < 12000L, lastAgeMs, lastPoint, pointCount, runtimeGps, tickMs, isEnded) }
            item { CloudSyncStatusCard(syncStatus, pendingSyncCount, lastSyncTimeMs, { scope.launch { val res = UrbanRuntime.syncNow(context); if (res.isSuccess) snackbarHostState.showSnackbar("Respaldo finalizado ✅") } }) }
            if (!syncDiagnostics.lastError.isNullOrBlank()) item { AforaInlineAlert("SYNC: ${syncDiagnostics.lastError}${syncDiagnostics.lastFailedPath?.let { " | $it" }.orEmpty()}", colors.Danger) }
            item { DemoSummaryCard(summary) }
            gpsMsg?.let { msg -> item { AforaInlineAlert(message = msg.uppercase(), color = colors.Success) } }
            if (loadingGps) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), color = colors.Success) }
            item { AforaSectionHeader("HISTORIAL DE EVENTOS", Modifier.padding(horizontal = 16.dp)) }
            if (stops.isEmpty()) { item { AforaOperationalCard(modifier = Modifier.padding(horizontal = 16.dp)) { Text("AÚN NO HAY EVENTOS.", modifier = Modifier.padding(16.dp), style = typography.BodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center) } } } else { items(stops.reversed()) { EventCard(it, fmt) } }
            item { DistanceCard(distanceKm, distanceLoading, { distanceLoading = true; scope.launch { distanceKm = withContext(Dispatchers.IO) { distanceMeters(vm.getTrackPointsOnce(tripId)) / 1000.0 }; distanceLoading = false } }, { distanceLoading = true; scope.launch { distanceKm = withContext(Dispatchers.IO) { distanceMeters(vm.getTrackPointsBetweenOnce(tripId, System.currentTimeMillis() - 900000, System.currentTimeMillis())) / 1000.0 }; distanceLoading = false } }) }
            item {
                ExportActionsCard(
                    onExportClientXlsx = { exportXlsx.launch("ASD_${tripId}.xlsx") },
                    onExportCsv = { exportCsv.launch("ASD_${tripId}.csv") },
                    onExportTrack = { exportTrackCsv.launch("TRACK_${tripId}.csv") },
                    onExportGpsAudit = { exportGpsAuditCsv.launch("GPS_AUDIT_${tripId}.csv") },
                    onExportGpx = { exportGpx.launch("TRIP_${tripId}.gpx") },
                    onExportKml = { exportKml.launch("TRIP_${tripId}.kml") },
                    onExportGarminTrack = { exportGarminT.launch("GARMIN_TRACK_${tripId}.gpx") },
                    onExportGarminWaypoints = { exportGarminW.launch("GARMIN_WAYPOINTS_${tripId}.gpx") }
                )
            }
        }
    }
    if (showCloseTripConfirm) { AlertDialog(onDismissRequest = { showCloseTripConfirm = false }, title = { Text("¿CERRAR LEVANTAMIENTO?", style = typography.Title, fontWeight = FontWeight.Black) }, text = { Text("Se generará el reporte final con ${summary.onBoard} pasajeros a bordo.") }, confirmButton = { Button(onClick = { scope.launch { if (onEndTrip(currentFix(System.currentTimeMillis()))) { showCloseTripConfirm = false; snackbarHostState.showSnackbar("Recorrido finalizado ✅"); onBack() } } }, colors = ButtonDefaults.buttonColors(containerColor = colors.Danger)) { Text("CERRAR") } }, dismissButton = { TextButton(onClick = { showCloseTripConfirm = false }) { Text("CANCELAR") } }) }
    if (showEditHeader && trip != null) { EditTripHeaderDialog(trip, { showEditHeader = false }, vm, { r, c, e, d, num, ef, bs, be, p, v, ca, nts, af, s, dev, sex -> scope.launch { onUpdateHeader(r, c, e, d, num, ef, bs, be, p, v, ca, nts, af, s, dev, sex); showEditHeader = false } }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InlineAsdCaptureCard(isEnded: Boolean, isDelayActive: Boolean, activeElapsedSec: Long, menUp: Int, womenUp: Int, menDown: Int, womenDown: Int, currentOnBoard: Int, menOnBoard: Int, womenOnBoard: Int, maxMenDown: Int, maxWomenDown: Int, seatCapacity: Int?, exceedsCapacity: Boolean, selectedDelayCodes: Set<String>, otherDelayDesc: String, hasLuggage: Boolean, stopName: String, notes: String, lastPoint: TrackPoint?, onMenUpChange: (Int) -> Unit, onWomenUpChange: (Int) -> Unit, onMenDownChange: (Int) -> Unit, onWomenDownChange: (Int) -> Unit, onToggleDelayCode: (String) -> Unit, onOtherDelayDescChange: (String) -> Unit, onHasLuggageChange: (Boolean) -> Unit, onStopNameChange: (String) -> Unit, onNotesChange: (String) -> Unit, onCancelDelay: () -> Unit, onSave: () -> Unit, onOpenMap: () -> Unit, onCloseTrip: () -> Unit, observerSex: String? = null) {
    val colors = LocalAforaColors.current; val typography = LocalAforaTypography.current; val haptic = LocalHapticFeedback.current; fun triggerHaptic() = haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    AforaOperationalCard(modifier = Modifier.padding(horizontal = 16.dp), borderAlpha = if(isDelayActive) 0.8f else 0.3f, containerColor = if(isDelayActive) colors.Success.copy(alpha = 0.02f) else colors.Surface) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { AforaHealthIndicator(isHealthy = isDelayActive); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(if (isDelayActive) "CAPTURA ACTIVA" else "LISTO PARA CAPTURAR", style = typography.Title, fontWeight = FontWeight.ExtraBold, color = if(isDelayActive) colors.Success else colors.Secondary); Text(if (isDelayActive) formatElapsed(activeElapsedSec) else "Toque un control para iniciar", style = typography.BodySmall, color = colors.Secondary.copy(alpha = 0.6f)) }; if (isDelayActive) IconButton(onClick = { triggerHaptic(); onCancelDelay() }) { Text("✕", color = colors.Danger, fontWeight = FontWeight.Bold) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { ProMetric("SUBEN", (menUp + womenUp).toString(), Modifier.weight(1f), colors.Success); ProMetric("BAJAN", (menDown + womenDown).toString(), Modifier.weight(1f), colors.Danger); ProMetric("A BORDO", (currentOnBoard + menUp + womenUp - menDown - womenDown).coerceAtLeast(0).toString(), Modifier.weight(1f), isError = exceedsCapacity) }
            if (exceedsCapacity) AforaInlineAlert(message = "Capacidad excedida: ${currentOnBoard + menUp + womenUp - menDown - womenDown} / $seatCapacity", color = colors.Danger)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { Column(Modifier.weight(1f)) { AforaMetadataRow("HOMBRES", "${menOnBoard + menUp - menDown}", valueColor = Color(0xFF3B82F6)) }; Column(Modifier.weight(1f)) { AforaMetadataRow("MUJERES", "${womenOnBoard + womenUp - womenDown}", valueColor = Color(0xFFEC4899)) } }
            PassengerSection("SUBEN", "↑", menUp + womenUp, menUp, womenUp, onMenUpChange, onWomenUpChange, colors.Success, null, null, Color(0xFF3B82F6), Color(0xFFEC4899), observerSex)
            PassengerSection("BAJAN", "↓", menDown + womenDown, menDown, womenDown, onMenDownChange, onWomenDownChange, colors.Danger, maxMenDown, maxWomenDown, Color(0xFF3B82F6), Color(0xFFEC4899), observerSex)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { AforaSectionHeader("DEMORAS"); val items = listOf(Triple("🚦", "S", "S"), Triple("🚗", "C", "C"), Triple("🚌", "TM", "TM"), Triple("🚧", "CND", "CND"), Triple("↩", "VI", "VI"), Triple("↪", "VD", "VD"), Triple("🚶", "PP", "PP"), Triple("⛔", "O", "O")); items.chunked(4).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { row.forEach { (i, n, c) -> DelayTile(i, n, c, selectedDelayCodes.contains(c), Modifier.weight(1f), onToggleDelayCode) } } }; if (selectedDelayCodes.contains("O")) UpperNextTextField(otherDelayDesc, onOtherDelayDescChange, "OTRO") }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { AforaSectionHeader("NOTAS", Modifier.weight(1f)); Switch(checked = hasLuggage, onCheckedChange = { triggerHaptic(); onHasLuggageChange(it) }, colors = SwitchDefaults.colors(checkedThumbColor = colors.Success), modifier = Modifier.scale(0.8f)); Text("BULTO", style = typography.Label) }; UpperNextTextField(stopName, onStopNameChange, "PARADA", true); UpperNextTextField(notes, onNotesChange, "OBSERVACIONES", false) }
            if (!isEnded) AforaPrimaryButton(text = if (isDelayActive) "GUARDAR REGISTRO" else "REGISTRAR PARADA", onClick = { triggerHaptic(); if (isDelayActive) onSave() else onMenUpChange(menUp) })
            if (!isEnded) AforaSecondaryButton(text = "FINALIZAR LEVANTAMIENTO", onClick = { triggerHaptic(); onCloseTrip() }, isOutlined = false)
        }
    }
}

@Composable private fun ProMetric(label: String, value: String, modifier: Modifier = Modifier, color: Color? = null, isError: Boolean = false) { val colors = LocalAforaColors.current; val typography = LocalAforaTypography.current; Surface(modifier = modifier, color = colors.Surface, shape = MaterialTheme.shapes.extraSmall, border = BorderStroke(1.dp, if (isError) colors.Danger else colors.Outline.copy(alpha = 0.2f))) { Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(value, style = typography.Title, fontWeight = FontWeight.Black, color = if(isError) colors.Danger else color ?: colors.Secondary); Text(label, style = typography.Label, color = (if(isError) colors.Danger else color ?: colors.Secondary).copy(alpha = 0.6f), fontWeight = FontWeight.Bold) } } }
@Composable private fun PassengerSection(title: String, icon: String, total: Int, men: Int, women: Int, onMen: (Int) -> Unit, onWomen: (Int) -> Unit, accent: Color, maxM: Int?, maxW: Int?, mCol: Color, fCol: Color, sex: String?) { val typography = LocalAforaTypography.current; Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Row { Text("$icon $title", style = typography.Label, fontWeight = FontWeight.Black, color = accent); Spacer(Modifier.weight(1f)); Text("TOTAL: $total", style = typography.Label, fontWeight = FontWeight.Bold, color = accent) }; Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { PassengerCounterCard("HOMBRES", men, onMen, Modifier.weight(1f), mCol, maxM, false, sex == "H"); PassengerCounterCard("MUJERES", women, onWomen, Modifier.weight(1f), fCol, maxW, true, sex == "M") } } }
@Composable private fun PassengerCounterCard(label: String, value: Int, onChange: (Int) -> Unit, modifier: Modifier, accent: Color, max: Int?, isW: Boolean, isObs: Boolean) { val colors = LocalAforaColors.current; val typography = LocalAforaTypography.current; val isAlert = max != null && max <= 0; Surface(modifier = modifier.heightIn(min = 120.dp), color = if(isAlert) colors.Danger.copy(alpha = 0.05f) else colors.Surface, shape = MaterialTheme.shapes.extraSmall, border = BorderStroke(1.dp, if(isAlert) colors.Danger else colors.Outline.copy(alpha = 0.2f))) { Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Text(label, style = typography.Label, fontWeight = FontWeight.Bold, color = accent.copy(alpha = 0.7f)); Text(value.toString(), style = typography.Headline, fontWeight = FontWeight.Black, color = if(isAlert) colors.Danger else accent); Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { ProCounterButton("-", { onChange(value - 1) }, value > 0 && !isAlert, Modifier.weight(1f)); ProCounterButton("+", { onChange(value + 1) }, max == null || value < max, Modifier.weight(1f)) } } } }
@Composable private fun ProCounterButton(text: String, onClick: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) { val colors = LocalAforaColors.current; val haptic = LocalHapticFeedback.current; OutlinedButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() }, enabled = enabled, modifier = modifier.height(44.dp), contentPadding = PaddingValues(0.dp), border = BorderStroke(1.dp, if(enabled) colors.Primary.copy(alpha = 0.5f) else colors.Disabled), shape = MaterialTheme.shapes.extraSmall) { Text(text, style = LocalAforaTypography.current.Title, fontWeight = FontWeight.Black, color = if(enabled) colors.Primary else colors.Disabled) } }
@Composable private fun DelayTile(icon: String, label: String, code: String, isSel: Boolean, modifier: Modifier, onToggle: (String) -> Unit) { val colors = LocalAforaColors.current; val typography = LocalAforaTypography.current; val haptic = LocalHapticFeedback.current; Surface(onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onToggle(code) }, color = if(isSel) colors.Warning.copy(alpha = 0.05f) else colors.Surface, shape = MaterialTheme.shapes.extraSmall, border = BorderStroke(1.dp, if(isSel) colors.Warning else colors.Outline.copy(alpha = 0.2f)), modifier = modifier.height(64.dp)) { Column(Modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Row(verticalAlignment = Alignment.CenterVertically) { Text(icon, fontSize = 14.sp); Spacer(Modifier.width(4.dp)); Text(label, style = typography.Label, fontWeight = FontWeight.Bold, color = if(isSel) colors.Warning else colors.Secondary, maxLines = 1) }; Text(code, style = typography.Label, color = (if(isSel) colors.Warning else colors.Secondary).copy(alpha = 0.5f)) } } }
@Composable private fun UpperNextTextField(value: String, onValueChange: (String) -> Unit, label: String, singleLine: Boolean = true) { val focusManager = LocalFocusManager.current; val colors = LocalAforaColors.current; OutlinedTextField(value = value, onValueChange = { onValueChange(it.uppercase()) }, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = singleLine, shape = MaterialTheme.shapes.extraSmall, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colors.Primary, unfocusedBorderColor = colors.Outline.copy(alpha = 0.5f), focusedLabelColor = colors.Primary, unfocusedLabelColor = colors.Secondary.copy(alpha = 0.4f)), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })) }
@Composable private fun TripHeaderCard(routeName: String, direction: String, start: String, end: String, vehicleEco: String?, plateNumber: String?, seatCapacity: Int?, aforador: String?, supervisor: String?, deviceNumber: String?, isEnded: Boolean, onEdit: () -> Unit) { val colors = LocalAforaColors.current; val typography = LocalAforaTypography.current; AforaOperationalCard(modifier = Modifier.padding(horizontal = 16.dp)) { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { Row(verticalAlignment = Alignment.Top) { Column(Modifier.weight(1f)) { AforaSectionHeader("DATOS DEL LEVANTAMIENTO"); Text(routeName, style = typography.Title, fontWeight = FontWeight.Black, color = colors.Secondary) }; if (!isEnded) TextButton(onClick = onEdit) { Text("EDITAR", style = typography.Label, fontWeight = FontWeight.Bold) } } ; Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { AforaMetadataRow("SENTIDO", direction); AforaMetadataRow("UNIDAD", "ECO ${vehicleEco ?: "-"} • ${plateNumber ?: "-"}") ; AforaMetadataRow("INICIO", start); AforaMetadataRow("FIN", end, valueColor = if(isEnded) colors.Secondary else colors.Primary) ; AforaMetadataRow("OPERADOR", aforador ?: "-") } } } }
@Composable private fun TrackingStatusCard(trackingAlive: Boolean, lastAgeMs: Long, lastPoint: TrackPoint?, pointCount: Int, runtimeGps: TrackingService.Companion.RuntimeGpsState, tickMs: Long) {
    val colors = LocalAforaColors.current; val typography = LocalAforaTypography.current
    val gpsAgeMs = if (runtimeGps.hasFix) tickMs - runtimeGps.receivedAtMs else Long.MAX_VALUE
    val (text, color) = when {
        !runtimeGps.hasFix -> if (isEnded) "RASTREO FINALIZADO" else "RASTREO PENDIENTE" to colors.Offline
        gpsAgeMs <= 6000 -> "RASTREO EXCELENTE" to colors.Success
        gpsAgeMs <= 15000 -> "RASTREO USABLE" to colors.Warning
        else -> "RASTREO DÉBIL" to colors.Danger
    }
    AforaOperationalCard(modifier = Modifier.padding(horizontal = 16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { AforaSectionHeader("ESTADO DEL RASTREO", Modifier.weight(1f)); if (trackingAlive) AforaStatusChip("EN VIVO", colors.Success) } ; Text(text, style = typography.Title, fontWeight = FontWeight.Black, color = color) ; Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { AforaMetadataRow("PRECISIÓN", if(runtimeGps.hasFix) "±${runtimeGps.accM.toInt()}m" else "-"); AforaMetadataRow("ÚLTIMA SEÑAL", if(runtimeGps.hasFix) "${gpsAgeMs / 1000}s" else "-"); AforaMetadataRow("PUNTOS", pointCount.toString()) }
        }
    }
}
@Composable private fun CloudSyncStatusCard(status: AsdTripSyncStatus, pendingCount: Int, lastSyncTime: Long?, onSyncNow: () -> Unit) { val colors = LocalAforaColors.current; val typography = LocalAforaTypography.current; val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) } ; AforaOperationalCard(modifier = Modifier.padding(horizontal = 16.dp)) { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { AforaSectionHeader("RESPALDO EN LA NUBE", Modifier.weight(1f)); SyncStatusChip(status) } ; Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Column { Text("PENDIENTES", style = typography.Label, color = colors.Secondary.copy(alpha = 0.4f)); Text(pendingCount.toString(), style = typography.Title, color = if (pendingCount > 0) colors.Warning else colors.Secondary, fontWeight = FontWeight.Black) } ; Column(horizontalAlignment = Alignment.End) { Text("ÚLTIMO ENVÍO", style = typography.Label, color = colors.Secondary.copy(alpha = 0.4f)); Text(lastSyncTime?.let { fmt.format(Date(it)) } ?: "NUNCA", style = typography.Title, color = colors.Secondary, fontWeight = FontWeight.Black) } } ; AforaPrimaryButton(text = "REINTENTAR ERRORES", onClick = onSyncNow) } } }
@Composable private fun SyncStatusChip(status: AsdTripSyncStatus) { val colors = LocalAforaColors.current; val (text, color) = when (status) { AsdTripSyncStatus.PENDING -> "PENDIENTE" to colors.Warning; AsdTripSyncStatus.IN_PROGRESS -> "ENVIANDO" to colors.Primary; AsdTripSyncStatus.SYNCED -> "GUARDADO" to colors.Success; AsdTripSyncStatus.FAILED -> "ERROR" to colors.Danger; AsdTripSyncStatus.PARTIAL -> "PARCIAL" to Color(0xFF9C27B0); else -> "DESCONOCIDO" to Color.Gray } ; AforaStatusChip(text, color) }
@Composable private fun DemoSummaryCard(summary: AsdDemoSummary) { val colors = LocalAforaColors.current; AforaOperationalCard(modifier = Modifier.padding(horizontal = 16.dp)) { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { AforaSectionHeader("RESUMEN OPERATIVO"); Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { SummaryMetricBox("EVENTOS", summary.events.toString(), Modifier.weight(1f)); SummaryMetricBox("SUBEN", summary.boardings.toString(), Modifier.weight(1f), colors.Success); SummaryMetricBox("BAJAN", summary.alightings.toString(), Modifier.weight(1f), colors.Danger) } ; Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { SummaryMetricBox("A BORDO", summary.onBoard.toString(), Modifier.weight(1f)); SummaryMetricBox("H / M", "${summary.menOnBoard}/${summary.womenOnBoard}", Modifier.weight(1f)); SummaryMetricBox("PUNTOS", summary.trackPoints.toString(), Modifier.weight(1f)) } } } }
@Composable private fun SummaryMetricBox(label: String, value: String, modifier: Modifier = Modifier, valColor: Color? = null) { val colors = LocalAforaColors.current; val typography = LocalAforaTypography.current; Surface(modifier = modifier, color = colors.Surface, shape = MaterialTheme.shapes.extraSmall, border = BorderStroke(1.dp, colors.Outline.copy(alpha = 0.2f))) { Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(value, style = typography.Title, fontWeight = FontWeight.Black, color = valColor ?: colors.Secondary); Text(label, style = typography.Label, color = (valColor ?: colors.Secondary).copy(alpha = 0.5f), fontWeight = FontWeight.Bold) } } }
@Composable private fun DistanceCard(distanceKm: Double?, distanceLoading: Boolean, onCalculateAll: () -> Unit, onCalculateRecent: () -> Unit) { val colors = LocalAforaColors.current; val typography = LocalAforaTypography.current; AforaOperationalCard(modifier = Modifier.padding(horizontal = 16.dp)) { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { AforaSectionHeader("DISTANCIA TOTAL"); Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { if (distanceLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = colors.Primary) else Text(distanceKm?.let { "%.2f KM".format(it) } ?: "—", style = typography.Headline, fontWeight = FontWeight.Black, color = colors.Primary) } ; Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedButton(enabled = !distanceLoading, onClick = onCalculateRecent, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.extraSmall) { Text("15 MIN") } ; OutlinedButton(enabled = !distanceLoading, onClick = onCalculateAll, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.extraSmall) { Text("TODO") } } } } }
@Composable private fun ExportActionsCard(onExportClientXlsx: () -> Unit, onExportCsv: () -> Unit, onExportTrack: () -> Unit, onExportGpsAudit: () -> Unit, onExportGpx: () -> Unit, onExportKml: () -> Unit, onExportGarminTrack: () -> Unit, onExportGarminWaypoints: () -> Unit) { val typography = LocalAforaTypography.current; AforaOperationalCard(modifier = Modifier.padding(horizontal = 16.dp)) { Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { AforaSectionHeader("EXPORTACIONES"); ExportGroup("REPORTES OFICIALES") { AforaPrimaryButton(text = "EXCEL CLIENTE", onClick = onExportClientXlsx) } ; ExportGroup("AUDITORÍA") { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { AforaSecondaryButton("GPS CSV", onExportGpsAudit, Modifier.weight(1f)); AforaSecondaryButton("TRACK CSV", onExportTrack, Modifier.weight(1f)) } } ; ExportGroup("GEO") { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { AforaSecondaryButton("KML", onExportKml, Modifier.weight(1f)); AforaSecondaryButton("GPX", onExportGpx, Modifier.weight(1f)) } } ; ExportGroup("GARMIN") { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { AforaSecondaryButton("TRACK", onExportGarminTrack, Modifier.weight(1f)); AforaSecondaryButton("WAYPOINTS", onExportGarminWaypoints, Modifier.weight(1f)) } } } } }
@Composable private fun ExportGroup(title: String, content: @Composable ColumnScope.() -> Unit) { val colors = LocalAforaColors.current; val typography = LocalAforaTypography.current; Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, style = typography.Label, fontWeight = FontWeight.Bold, color = colors.Secondary.copy(alpha = 0.4f)); content() } }
@Composable private fun EventCard(event: StopEvent, fmt: SimpleDateFormat) { val colors = LocalAforaColors.current; val typography = LocalAforaTypography.current; AforaOperationalCard(modifier = Modifier.padding(horizontal = 16.dp), borderAlpha = 0.1f) { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("WP ${event.waypointStartId} -> WP ${event.waypointStopId}", style = typography.Data, fontWeight = FontWeight.Bold, color = colors.Secondary); Text(fmt.format(Date(event.timestamp)), style = typography.Label, color = colors.Secondary.copy(alpha = 0.4f)) } ; if (!event.stopName.isNullOrBlank()) Text(event.stopName.uppercase(), style = typography.BodySmall, fontWeight = FontWeight.Black) ; Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) { Column { Text("SUBEN: ${event.paxMenUp + event.paxWomenUp}", style = typography.Label, fontWeight = FontWeight.Bold, color = colors.Success); Text("H:${event.paxMenUp} M:${event.paxWomenUp}", style = typography.Label, color = colors.Secondary.copy(alpha = 0.4f)) } ; Column { Text("BAJAN: ${event.paxMenDown + event.paxWomenDown}", style = typography.Label, fontWeight = FontWeight.Bold, color = colors.Danger); Text("H:${event.paxMenDown} M:${event.paxWomenDown}", style = typography.Label, color = colors.Secondary.copy(alpha = 0.4f)) } } ; if (!event.delayCodes.isNullOrBlank()) AforaStatusChip(event.delayCodes, colors.Warning) } } }
@Preview(showBackground = true) @Composable fun AsdTripDetailPreview() { UrbanAppTheme { Box(Modifier.fillMaxSize()) { Text("Preview placeholder") } } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditTripHeaderDialog(trip: Trip, onDismiss: () -> Unit, vm: AsdTripDetailVM, onSave: (String, String?, String?, String, Int?, String?, String?, String?, String?, String?, Int?, String?, String?, String?, String?, String?) -> Unit) {
    val focusManager = LocalFocusManager.current; val colors = LocalAforaColors.current
    var pId by rememberSaveable(trip.tripId) { mutableStateOf(trip.planningRouteId) }
    var rN by rememberSaveable(trip.tripId) { mutableStateOf(trip.routeName.uppercase()) }
    var comp by rememberSaveable(trip.tripId) { mutableStateOf(trip.company?.uppercase() ?: "") }
    var vE by rememberSaveable(trip.tripId) { mutableStateOf(trip.vehicleEco?.uppercase() ?: "") }
    var dir by rememberSaveable(trip.tripId) { mutableStateOf(trip.direction.uppercase()) }
    var rNum by rememberSaveable(trip.tripId) { mutableStateOf(trip.routeNumber?.toString() ?: "") }
    var ef by rememberSaveable(trip.tripId) { mutableStateOf(trip.esFs?.uppercase() ?: "") }
    var bS by rememberSaveable(trip.tripId) { mutableStateOf(trip.baseStart?.uppercase() ?: "") }
    var bE by rememberSaveable(trip.tripId) { mutableStateOf(trip.baseEnd?.uppercase() ?: "") }
    var pN by rememberSaveable(trip.tripId) { mutableStateOf(trip.plateNumber?.uppercase() ?: "") }
    var vT by rememberSaveable(trip.tripId) { mutableStateOf(trip.vehicleType?.uppercase() ?: "") }
    var sC by rememberSaveable(trip.tripId) { mutableStateOf(trip.seatCapacity?.toString() ?: "") }
    var af by rememberSaveable(trip.tripId) { mutableStateOf(trip.aforador?.uppercase() ?: "") }
    var sup by rememberSaveable(trip.tripId) { mutableStateOf(trip.supervisor?.uppercase() ?: "") }
    var dN by rememberSaveable(trip.tripId) { mutableStateOf(trip.deviceNumber?.uppercase() ?: "") }
    var oS by rememberSaveable(trip.tripId) { mutableStateOf(trip.observerSex ?: "") }
    var hN by rememberSaveable(trip.tripId) { mutableStateOf(trip.notes?.uppercase() ?: "") }
    @Composable fun NF(l: String, v: String, c: (String) -> Unit, n: Boolean = false) { OutlinedTextField(value = v, onValueChange = { c(if (n) it.filter { ch -> ch.isDigit() }.take(6) else it.uppercase()) }, label = { Text(l) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = MaterialTheme.shapes.extraSmall, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colors.Primary), keyboardOptions = KeyboardOptions(keyboardType = if (n) KeyboardType.Number else KeyboardType.Text, imeAction = ImeAction.Next), keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("EDITAR ENCABEZADO", style = LocalAforaTypography.current.Title, fontWeight = FontWeight.Black) }, text = { Box(modifier = Modifier.heightIn(max = 400.dp)) { LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) { item { NF("ID PLANEACIÓN", pId, { pId = it }) }; item { NF("RUTA", rN, { rN = it }) }; item { NF("EMPRESA", comp, { comp = it }) }; item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Box(Modifier.weight(1f)) { NF("SENTIDO", dir, { dir = it }) }; Box(Modifier.weight(1f)) { NF("ES/FS", ef, { ef = it }) } } }; item { NF("OPERADOR", af, { af = it }) }; item { NF("PLACA", pN, { pN = it }) } } } }, confirmButton = { Button(onClick = { onSave(rN, comp.ifBlank{null}, vE.ifBlank{null}, dir, rNum.toIntOrNull(), ef.ifBlank{null}, bS.ifBlank{null}, bE.ifBlank{null}, pN.ifBlank{null}, vT.ifBlank{null}, sC.toIntOrNull(), hN.ifBlank{null}, af.ifBlank{null}, sup.ifBlank{null}, dN.ifBlank{null}, oS.ifBlank{null}) }, shape = MaterialTheme.shapes.extraSmall) { Text("GUARDAR") } }, dismissButton = { OutlinedButton(onClick = onDismiss, shape = MaterialTheme.shapes.extraSmall) { Text("CANCELAR") } })
}

private fun buildGpsQualitySummary(points: List<TrackPoint>): String { if (points.isEmpty()) return "-"; val qualities = points.map { point -> val parsed = com.oropeza.urbanapp.asd.location.GpsProviderDiagnostics.parse(point.provider); parsed.quality.ifBlank { when { point.accM <= 10.0 -> "EXCELLENT"; point.accM <= 25.0 -> "GOOD"; point.accM <= 45.0 -> "USABLE"; else -> "POOR" } } }; fun pct(label: String): Int { val count = qualities.count { it == label }; return ((count.toDouble() / qualities.size.toDouble()) * 100.0).toInt() }; return "EXCELLENT ${pct("EXCELLENT")}% / GOOD ${pct("GOOD")}% / USABLE ${pct("USABLE")}% / POOR ${pct("POOR")}%" }
private data class AsdDemoSummary(val events: Int, val boardings: Int, val alightings: Int, val delays: Int, val onBoard: Int, val menOnBoard: Int, val womenOnBoard: Int, val trackPoints: Int) { companion object { fun from(stops: List<StopEvent>, pointCount: Int): AsdDemoSummary { var boardings = 0; var alightings = 0; var delays = 0; var onBoard = 0; var menOnBoard = 0; var womenOnBoard = 0; stops.sortedBy { it.timestamp }.forEach { event -> val up = event.paxMenUp + event.paxWomenUp; val down = event.paxMenDown + event.paxWomenDown; val type = event.stopType.uppercase(Locale.getDefault()); if (!event.delayCodes.isNullOrBlank() || type in setOf("DEMORA", "BANDERA", "DELAY")) delays += 1; boardings += up; alightings += down; onBoard = (onBoard + up - down).coerceAtLeast(0); menOnBoard = (menOnBoard + event.paxMenUp - event.paxMenDown).coerceAtLeast(0); womenOnBoard = (womenOnBoard + event.paxWomenUp - event.paxWomenDown).coerceAtLeast(0) }; return AsdDemoSummary(stops.size, boardings, alightings, delays, onBoard, menOnBoard, womenOnBoard, pointCount) } } }
private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double { val r = 6_371_000.0; val dLat = Math.toRadians(lat2 - lat1); val dLat1 = Math.toRadians(lat2 - lat1); val dLon = Math.toRadians(lon2 - lon1); val a = sin(dLat / 2).pow(2.0) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0); val c = 2 * atan2(sqrt(a), sqrt(1 - a)); return r * c }
private fun distanceMeters(points: List<TrackPoint>): Double { if (points.size < 2) return 0.0; val raw = points.map { LatLng(it.lat, it.lon) }; val smooth = PolylineSmoother.movingAverage(raw, window = 3); val simplified = PolylineSmoother.douglasPeucker(smooth, epsilonMeters = 4.0); var total = 0.0; for (i in 1 until simplified.size) total += haversineMeters(simplified[i - 1].lat, simplified[i - 1].lon, simplified[i].lat, simplified[i].lon); return total }

