package com.oropeza.urbanapp.asd.ui.viewmodel

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oropeza.urbanapp.asd.AsdGraph
import com.oropeza.urbanapp.asd.data.local.StopEvent
import com.oropeza.urbanapp.asd.data.local.TrackPoint
import com.oropeza.urbanapp.asd.data.local.Trip
import com.oropeza.urbanapp.asd.export.CsvExporter
import com.oropeza.urbanapp.asd.export.GpxExporter
import com.oropeza.urbanapp.asd.export.KmlExporter
import com.oropeza.urbanapp.asd.location.LatLng
import com.oropeza.urbanapp.asd.location.LocationFix
import com.oropeza.urbanapp.asd.location.LocationProvider
import com.oropeza.urbanapp.asd.location.PolylineSmoother
import com.oropeza.urbanapp.asd.location.TrackingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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

class AsdTripDetailVM : ViewModel() {
    fun tripFlow(tripId: Long) = AsdGraph.repo.tripFlow(tripId)
    fun stopsFlow(tripId: Long) = AsdGraph.repo.stopsFlow(tripId)
    fun trackLastPointFlow(tripId: Long): Flow<TrackPoint?> = AsdGraph.repo.trackLastPointFlow(tripId)
    fun trackCountFlow(tripId: Long): Flow<Int> = AsdGraph.repo.trackCountFlow(tripId)
    suspend fun getTrackPointsOnce(tripId: Long) = AsdGraph.repo.getTrackPointsOnce(tripId)
    suspend fun getTrackPointsBetweenOnce(tripId: Long, fromMs: Long, toMs: Long) = AsdGraph.repo.getTrackPointsBetweenOnce(tripId, fromMs, toMs)

    suspend fun updateTripHeader(
        tripId: Long,
        routeName: String,
        company: String?,
        vehicleEco: String?,
        direction: String,
        routeNumber: Int?,
        esFs: String?,
        baseStart: String?,
        baseEnd: String?,
        plateNumber: String?,
        vehicleType: String?,
        seatCapacity: Int?,
        notes: String?
    ): Boolean = AsdGraph.repo.updateTripHeader(
        tripId, routeName, company, vehicleEco, direction, routeNumber, esFs, baseStart, baseEnd, plateNumber, vehicleType, seatCapacity, notes
    )

    suspend fun exportLayoutFinal(context: Context, tripId: Long, uri: Uri): Boolean {
        val trip = AsdGraph.repo.getTripOnce(tripId) ?: return false
        CsvExporter.exportLayoutFinal(context, uri, trip, AsdGraph.repo.getStopsOnce(tripId), AsdGraph.repo.getDelaysOnce(tripId))
        return true
    }

    suspend fun exportTrackCsv(context: Context, tripId: Long, uri: Uri): Boolean {
        val points = AsdGraph.repo.getTrackPointsOnce(tripId)
        val raw = points.map { LatLng(it.lat, it.lon) }
        val smooth = PolylineSmoother.movingAverage(raw, window = 3)
        val simplified = PolylineSmoother.douglasPeucker(smooth, epsilonMeters = 4.0)
        val rebuilt = points.take(simplified.size).mapIndexed { i, p -> p.copy(lat = simplified[i].lat, lon = simplified[i].lon) }
        CsvExporter.exportTrackPointsCsv(context, uri, rebuilt)
        return true
    }

    suspend fun exportTripGpx(context: Context, tripId: Long, uri: Uri): Boolean {
        val trip = AsdGraph.repo.getTripOnce(tripId) ?: return false
        GpxExporter.exportTripGpx(context, uri, trip, AsdGraph.repo.getTrackPointsOnce(tripId), AsdGraph.repo.getStopsOnce(tripId))
        return true
    }

    suspend fun exportTripKml(context: Context, tripId: Long, uri: Uri): Boolean {
        val trip = AsdGraph.repo.getTripOnce(tripId) ?: return false
        KmlExporter.exportTripKml(context, uri, trip, AsdGraph.repo.getTrackPointsOnce(tripId), AsdGraph.repo.getStopsOnce(tripId))
        return true
    }

    suspend fun addStopDetailed(
        tripId: Long,
        stopType: String,
        stopTimeMs: Long,
        startTimeMs: Long,
        stopName: String?,
        notes: String?,
        menUp: Int,
        womenUp: Int,
        menDown: Int,
        womenDown: Int,
        hasLuggage: Boolean,
        delayCodes: String?,
        otherDelayDesc: String?,
        stopFix: LocationFix,
        startFix: LocationFix = stopFix
    ) = AsdGraph.repo.addStopDetailed(
        tripId = tripId,
        stopType = stopType,
        stopTimeMs = stopTimeMs,
        startTimeMs = startTimeMs,
        stopName = stopName,
        notes = notes,
        menUp = menUp,
        womenUp = womenUp,
        menDown = menDown,
        womenDown = womenDown,
        hasLuggage = hasLuggage,
        delayCodes = delayCodes,
        otherDelayDesc = otherDelayDesc,
        eventTimestampMs = stopTimeMs,
        stopLat = stopFix.lat,
        stopLon = stopFix.lon,
        stopAccM = stopFix.accM,
        stopProvider = stopFix.provider,
        stopFixTime = stopFix.fixTime,
        locationStatus = stopFix.status,
        startLat = startFix.lat,
        startLon = startFix.lon,
        startAccM = startFix.accM,
        startProvider = startFix.provider,
        startFixTime = startFix.fixTime
    )

    suspend fun endTripWithFix(tripId: Long, fix: LocationFix): Boolean = AsdGraph.repo.endTripWithFix(
        tripId = tripId,
        stopLat = fix.lat,
        stopLon = fix.lon,
        stopAccM = fix.accM,
        stopProvider = fix.provider,
        stopFixTime = fix.fixTime,
        locationStatus = fix.status
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AsdTripDetailScreen(tripId: Long, onBack: () -> Unit, onOpenMap: (Long) -> Unit) {
    val vm: AsdTripDetailVM = viewModel()
    val context = LocalContext.current
    val gps = remember { LocationProvider(context) }
    val scope = rememberCoroutineScope()
    val trip by vm.tripFlow(tripId).collectAsState(initial = null)
    val stops by vm.stopsFlow(tripId).collectAsState(initial = emptyList())
    val lastPoint by vm.trackLastPointFlow(tripId).collectAsState(initial = null)
    val pointCount by vm.trackCountFlow(tripId).collectAsState(initial = 0)
    val fmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("es", "MX")) }
    val fileFmt = remember { SimpleDateFormat("yyyyMMdd_HHmm", Locale("es", "MX")) }

    var menUp by rememberSaveable { mutableStateOf(0) }
    var womenUp by rememberSaveable { mutableStateOf(0) }
    var menDown by rememberSaveable { mutableStateOf(0) }
    var womenDown by rememberSaveable { mutableStateOf(0) }
    var selectedDelayCodes by rememberSaveable { mutableStateOf(setOf<String>()) }
    var otherDelayDesc by rememberSaveable { mutableStateOf("") }
    var hasLuggage by rememberSaveable { mutableStateOf(false) }
    var stopName by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var activeDelayStartMs by rememberSaveable { mutableStateOf(0L) }
    var activeDelayLat by rememberSaveable { mutableStateOf(0.0) }
    var activeDelayLon by rememberSaveable { mutableStateOf(0.0) }
    var activeDelayAccM by rememberSaveable { mutableStateOf(0.0) }
    var activeDelayProvider by rememberSaveable { mutableStateOf("") }
    var activeDelayFixTime by rememberSaveable { mutableStateOf(0L) }
    var activeDelayStatus by rememberSaveable { mutableStateOf("GPS_PENDING") }
    var tickMs by remember { mutableStateOf(System.currentTimeMillis()) }
    var loadingGps by remember { mutableStateOf(false) }
    var gpsMsg by remember { mutableStateOf<String?>(null) }
    var snackbarText by remember { mutableStateOf<String?>(null) }
    var distanceKm by remember { mutableStateOf<Double?>(null) }
    var distanceLoading by remember { mutableStateOf(false) }
    var showCloseTripConfirm by remember { mutableStateOf(false) }
    var showEditHeader by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val isDelayActive = activeDelayStartMs > 0L

    LaunchedEffect(activeDelayStartMs) {
        while (activeDelayStartMs > 0L) {
            tickMs = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000L)
        }
    }
    LaunchedEffect(snackbarText) {
        snackbarText?.let { snackbarHostState.showSnackbar(it); snackbarText = null }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    fun requestPermsIfNeeded(): Boolean {
        if (!gps.hasPermission()) {
            permLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            return false
        }
        return true
    }
    fun startTrackingService() = context.startService(Intent(context, TrackingService::class.java).apply { action = TrackingService.ACTION_START; putExtra(TrackingService.EXTRA_TRIP_ID, tripId) })
    fun stopTrackingService() = context.startService(Intent(context, TrackingService::class.java).apply { action = TrackingService.ACTION_STOP })
    fun resetCaptureForm() { menUp = 0; womenUp = 0; menDown = 0; womenDown = 0; selectedDelayCodes = emptySet(); otherDelayDesc = ""; hasLuggage = false; stopName = ""; notes = "" }
    fun clearActiveDelay() { activeDelayStartMs = 0L; activeDelayLat = 0.0; activeDelayLon = 0.0; activeDelayAccM = 0.0; activeDelayProvider = ""; activeDelayFixTime = 0L; activeDelayStatus = "GPS_PENDING" }
    fun currentFix(now: Long): LocationFix {
        val p = lastPoint
        return if (p != null && p.lat != 0.0 && p.lon != 0.0) LocationFix(p.lat, p.lon, p.accM, p.provider, p.timeMs, if (p.accM <= 20.0) "FIX_USABLE" else "GPS_LAST") else LocationFix(0.0, 0.0, 0.0, "pending", now, "GPS_PENDING")
    }
    fun activeStartFix() = LocationFix(activeDelayLat, activeDelayLon, activeDelayAccM, activeDelayProvider.ifBlank { "pending" }, activeDelayFixTime, activeDelayStatus)
    fun ensureActiveDelayFromInput() {
        if (isDelayActive || trip?.endTime != null) return
        if (!requestPermsIfNeeded()) return
        val now = System.currentTimeMillis(); val fix = currentFix(now)
        activeDelayStartMs = now; activeDelayLat = fix.lat; activeDelayLon = fix.lon; activeDelayAccM = fix.accM; activeDelayProvider = fix.provider; activeDelayFixTime = fix.fixTime; activeDelayStatus = fix.status
    }
    fun inferredStopType(): String = if (menUp + womenUp + menDown + womenDown > 0) "ASD" else "DEMORA"
    fun selectedDelayText(): String? = selectedDelayCodes.joinToString("/").ifBlank { null }
    fun upper(value: String): String = value.uppercase(Locale("es", "MX"))
    fun hasValidDelayCause(): Boolean = (menUp + womenUp + menDown + womenDown > 0) || selectedDelayCodes.isNotEmpty() || otherDelayDesc.isNotBlank()
    fun validateGenderOnBoard(summary: AsdDemoSummary): String? {
        if (menDown > summary.menOnBoard + menUp) return "No puedes bajar $menDown hombres si solo van ${summary.menOnBoard + menUp} hombres disponibles."
        if (womenDown > summary.womenOnBoard + womenUp) return "No puedes bajar $womenDown mujeres si solo van ${summary.womenOnBoard + womenUp} mujeres disponibles."
        return null
    }

    fun closeActiveDelay(summary: AsdDemoSummary) {
        if (!isDelayActive) return
        if (!requestPermsIfNeeded()) { snackbarText = "Permiso de ubicación requerido."; return }
        if (!hasValidDelayCause()) { snackbarText = "El registro debe tener ascenso/descenso o una demora seleccionada."; return }
        validateGenderOnBoard(summary)?.let { snackbarText = it; return }
        val now = System.currentTimeMillis(); val endFix = currentFix(now)
        scope.launch {
            try {
                vm.addStopDetailed(tripId, inferredStopType(), activeDelayStartMs, now, stopName.trim().ifBlank { null }, notes.trim().ifBlank { null }, menUp, womenUp, menDown, womenDown, hasLuggage, selectedDelayText(), otherDelayDesc.trim().ifBlank { null }, activeStartFix(), endFix)
                clearActiveDelay(); resetCaptureForm(); snackbarText = "Registro cerrado y guardado ✅"
            } catch (e: Exception) { snackbarText = e.message ?: "Error al cerrar registro." }
        }
    }
    fun saveInlineEvent(summary: AsdDemoSummary) { if (isDelayActive) closeActiveDelay(summary) else { snackbarText = "Toca un contador o una demora para iniciar el registro." } }
    fun requestCloseTrip() {
        if (!requestPermsIfNeeded()) { snackbarText = "Permiso de ubicación requerido."; return }
        if (isDelayActive) { snackbarText = "Cierra o cancela el registro activo antes de cerrar el viaje."; return }
        showCloseTripConfirm = true
    }
    fun closeTripNow() {
        scope.launch {
            try {
                loadingGps = true; gpsMsg = "Cerrando viaje… fijando ubicación final"
                val fix = gps.getBestFixForEvent(10.0, 25.0, 7_000L, true)
                val ok = vm.endTripWithFix(tripId, fix); stopTrackingService()
                snackbarText = if (ok) "Viaje cerrado ✅ (${fix.status}) acc=±${fix.accM.toInt()}m" else "No se pudo cerrar el viaje."
            } catch (e: Exception) { snackbarText = e.message ?: "Error al cerrar viaje." }
            finally { loadingGps = false; gpsMsg = null; showCloseTripConfirm = false }
        }
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? -> uri?.let { scope.launch { snackbarText = runCatching { if (vm.exportLayoutFinal(context, tripId, it)) "CSV final exportado ✅" else "No se pudo exportar CSV." }.getOrElse { e -> e.message ?: "Error exportando CSV." } } } }
    val exportTrackLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? -> uri?.let { scope.launch { snackbarText = runCatching { if (vm.exportTrackCsv(context, tripId, it)) "TRACK CSV exportado ✅" else "No se pudo exportar TRACK." }.getOrElse { e -> e.message ?: "Error exportando TRACK." } } } }
    val exportGpxLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri: Uri? -> uri?.let { scope.launch { snackbarText = runCatching { if (vm.exportTripGpx(context, tripId, it)) "GPX exportado ✅" else "No se pudo exportar GPX." }.getOrElse { e -> e.message ?: "Error exportando GPX." } } } }
    val exportKmlLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.google-earth.kml+xml")) { uri: Uri? -> uri?.let { scope.launch { snackbarText = runCatching { if (vm.exportTripKml(context, tripId, it)) "KML exportado ✅" else "No se pudo exportar KML." }.getOrElse { e -> e.message ?: "Error exportando KML." } } } }

    if (showCloseTripConfirm) {
        AlertDialog(
            onDismissRequest = { showCloseTripConfirm = false },
            title = { Text("CONFIRMAR CIERRE") },
            text = { Text("¿REALMENTE QUIERES CERRAR ESTE VIAJE? ESTA ACCIÓN MARCARÁ AD/FINAL.") },
            confirmButton = { Button(onClick = { closeTripNow() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))) { Text("SÍ, CERRAR VIAJE") } },
            dismissButton = { OutlinedButton(onClick = { showCloseTripConfirm = false }) { Text("NO, REGRESAR") } }
        )
    }

    val currentTrip = trip
    if (showEditHeader && currentTrip != null) {
        EditTripHeaderDialog(
            trip = currentTrip,
            onDismiss = { showEditHeader = false },
            onSave = { routeName, company, vehicleEco, direction, routeNumber, esFs, baseStart, baseEnd, plateNumber, vehicleType, seatCapacity, headerNotes ->
                scope.launch {
                    val ok = vm.updateTripHeader(tripId, routeName, company, vehicleEco, direction, routeNumber, esFs, baseStart, baseEnd, plateNumber, vehicleType, seatCapacity, headerNotes)
                    snackbarText = if (ok) "Encabezado actualizado ✅" else "No se pudo actualizar encabezado."
                    showEditHeader = false
                }
            }
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text((trip?.routeName ?: "ASD").uppercase(Locale("es", "MX"))) }, navigationIcon = { TextButton(onClick = onBack) { Text("ATRÁS") } }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { pad ->
        val t = trip
        if (t == null) { Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return@Scaffold }
        val isEnded = t.endTime != null
        val summary = remember(stops, pointCount) { AsdDemoSummary.from(stops, pointCount) }
        val lastAgeMs = lastPoint?.let { System.currentTimeMillis() - it.timeMs } ?: Long.MAX_VALUE
        val trackingAlive = lastPoint != null && lastAgeMs in 0..12_000L
        val activeElapsedSec = if (isDelayActive) ((tickMs - activeDelayStartMs).coerceAtLeast(0L) / 1000L) else 0L
        val captureOnBoard = (summary.onBoard + menUp + womenUp - menDown - womenDown).coerceAtLeast(0)
        val exceedsCapacity = t.seatCapacity?.let { it > 0 && captureOnBoard > it } ?: false
        val maxMenDown = summary.menOnBoard + menUp
        val maxWomenDown = summary.womenOnBoard + womenUp
        LaunchedEffect(tripId, isEnded) { if (!isEnded) { if (gps.hasPermission()) startTrackingService() else snackbarText = "Tip: activa permisos de ubicación para registrar GPS." } else stopTrackingService() }
        LazyColumn(modifier = Modifier.padding(pad).fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                InlineAsdCaptureCard(
                    isEnded, isDelayActive, activeElapsedSec, menUp, womenUp, menDown, womenDown, summary.onBoard, summary.menOnBoard, summary.womenOnBoard, maxMenDown, maxWomenDown, t.seatCapacity, exceedsCapacity,
                    selectedDelayCodes, otherDelayDesc, hasLuggage, stopName, notes, lastPoint,
                    { ensureActiveDelayFromInput(); menUp = it.coerceAtLeast(0) },
                    { ensureActiveDelayFromInput(); womenUp = it.coerceAtLeast(0) },
                    { ensureActiveDelayFromInput(); menDown = it.coerceIn(0, maxMenDown) },
                    { ensureActiveDelayFromInput(); womenDown = it.coerceIn(0, maxWomenDown) },
                    { code -> ensureActiveDelayFromInput(); selectedDelayCodes = if (selectedDelayCodes.contains(code)) selectedDelayCodes - code else selectedDelayCodes + code },
                    { ensureActiveDelayFromInput(); otherDelayDesc = upper(it) },
                    { ensureActiveDelayFromInput(); hasLuggage = it },
                    { ensureActiveDelayFromInput(); stopName = upper(it) },
                    { ensureActiveDelayFromInput(); notes = upper(it) },
                    { closeActiveDelay(summary) },
                    { clearActiveDelay(); resetCaptureForm(); snackbarText = "Registro cancelado" },
                    { saveInlineEvent(summary) },
                    { resetCaptureForm() }
                )
            }
            gpsMsg?.let { item { Text(it.uppercase(Locale("es", "MX"))) } }
            if (loadingGps) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            item { TripHeaderCard(t.routeName.uppercase(Locale("es", "MX")), t.direction.uppercase(Locale("es", "MX")), fmt.format(Date(t.startTime)), t.endTime?.let { fmt.format(Date(it)) } ?: "EN CURSO", t.vehicleEco?.uppercase(Locale("es", "MX")), t.plateNumber?.uppercase(Locale("es", "MX")), t.seatCapacity, isEnded, onEdit = { showEditHeader = true }) }
            item { DemoSummaryCard(summary) }
            item { TrackingStatusCard(trackingAlive, lastAgeMs, lastPoint, pointCount) }
            item { DistanceCard(distanceKm, distanceLoading, {
                distanceLoading = true; distanceKm = null; scope.launch { try { distanceKm = withContext(Dispatchers.IO) { distanceMeters(vm.getTrackPointsOnce(tripId)) / 1000.0 } } catch (e: Exception) { snackbarText = e.message ?: "Error calculando distancia." } finally { distanceLoading = false } }
            }, {
                distanceLoading = true; distanceKm = null; scope.launch { try { val toMs = System.currentTimeMillis(); val fromMs = toMs - 15 * 60 * 1000L; distanceKm = withContext(Dispatchers.IO) { distanceMeters(vm.getTrackPointsBetweenOnce(tripId, fromMs, toMs)) / 1000.0 } } catch (e: Exception) { snackbarText = e.message ?: "Error calculando distancia." } finally { distanceLoading = false } }
            }) }
            item { TripActionsCard(isEnded, loadingGps, { onOpenMap(tripId) }, { requestCloseTrip() }) }
            item { ExportActionsCard({ exportCsvLauncher.launch("ASD_${t.tripId}_${fileFmt.format(Date())}.csv") }, { exportTrackLauncher.launch("ASD_track_trip_${t.tripId}.csv") }, { exportGpxLauncher.launch("ASD_${t.tripId}_${fileFmt.format(Date())}.gpx") }, { exportKmlLauncher.launch("ASD_${t.tripId}_${fileFmt.format(Date())}.kml") }) }
            item { Text("EVENTOS REGISTRADOS", style = MaterialTheme.typography.titleMedium) }
            if (stops.isEmpty()) item { Card { Text("AÚN NO HAY EVENTOS. USA EL BLOQUE SUPERIOR PARA REGISTRAR.", modifier = Modifier.padding(12.dp)) } } else items(stops) { EventCard(it, fmt) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InlineAsdCaptureCard(
    isEnded: Boolean,
    isDelayActive: Boolean,
    activeElapsedSec: Long,
    menUp: Int,
    womenUp: Int,
    menDown: Int,
    womenDown: Int,
    currentOnBoard: Int,
    menOnBoard: Int,
    womenOnBoard: Int,
    maxMenDown: Int,
    maxWomenDown: Int,
    seatCapacity: Int?,
    exceedsCapacity: Boolean,
    selectedDelayCodes: Set<String>,
    otherDelayDesc: String,
    hasLuggage: Boolean,
    stopName: String,
    notes: String,
    lastPoint: TrackPoint?,
    onMenUpChange: (Int) -> Unit,
    onWomenUpChange: (Int) -> Unit,
    onMenDownChange: (Int) -> Unit,
    onWomenDownChange: (Int) -> Unit,
    onToggleDelayCode: (String) -> Unit,
    onOtherDelayDescChange: (String) -> Unit,
    onHasLuggageChange: (Boolean) -> Unit,
    onStopNameChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onCloseDelay: () -> Unit,
    onCancelDelay: () -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit
) {
    val totalUp = menUp + womenUp
    val totalDown = menDown + womenDown
    val estimatedOnBoard = (currentOnBoard + totalUp - totalDown).coerceAtLeast(0)
    val delayCodes = listOf("C", "S", "TM", "CND", "VI", "VD", "PP", "CONG", "O")
    val green = Color(0xFF2E7D32)
    val red = Color(0xFFC62828)
    Card(border = if (isDelayActive) BorderStroke(2.dp, green) else null) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Text("REGISTRO ASD", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(if (isDelayActive) "ACTIVO ${formatElapsed(activeElapsedSec)}" else "LISTO", color = if (isDelayActive) green else Color.Unspecified, fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onCloseDelay, modifier = Modifier.weight(1f), enabled = !isEnded && isDelayActive, colors = ButtonDefaults.buttonColors(containerColor = green)) { Text("GUARDAR Y CERRAR") }
                Button(onClick = onCancelDelay, modifier = Modifier.weight(1f), enabled = !isEnded && isDelayActive, colors = ButtonDefaults.buttonColors(containerColor = red)) { Text("ABORTAR") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SummaryMetric("SUBEN", totalUp.toString(), Modifier.weight(1f))
                SummaryMetric("BAJAN", totalDown.toString(), Modifier.weight(1f))
                SummaryMetric("A BORDO", estimatedOnBoard.toString(), Modifier.weight(1f), isError = exceedsCapacity)
            }
            Text("A BORDO: H ${menOnBoard + menUp - menDown} • M ${womenOnBoard + womenUp - womenDown}", style = MaterialTheme.typography.bodySmall, color = if (menDown > maxMenDown || womenDown > maxWomenDown) MaterialTheme.colorScheme.error else Color.Unspecified)
            if (exceedsCapacity) Text("⚠ SUPERA CAPACIDAD (${seatCapacity ?: 0})", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            Text("SUBEN", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                CounterBox("👨 HOMBRES", menUp, onMenUpChange, Modifier.weight(1f))
                CounterBox("👩 MUJERES", womenUp, onWomenUpChange, Modifier.weight(1f))
            }
            Text("BAJAN", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                CounterBox("👨 HOMBRES", menDown, onMenDownChange, Modifier.weight(1f), maxValue = maxMenDown)
                CounterBox("👩 MUJERES", womenDown, onWomenDownChange, Modifier.weight(1f), maxValue = maxWomenDown)
            }
            Text("DEMORAS", style = MaterialTheme.typography.titleSmall)
            DelayCodeGrid(delayCodes, selectedDelayCodes, onToggleDelayCode)
            Text("C=CONGESTIÓN, S=SEMAFORIZACIÓN, TM=TRÁFICO MIXTO, VI=VUELTA IZQUIERDA, VD=VUELTA DERECHA, PP=PASE PEATONAL.", style = MaterialTheme.typography.bodySmall)
            if (selectedDelayCodes.contains("O")) UpperNextTextField(otherDelayDesc, onOtherDelayDescChange, "DESCRIPCIÓN DE OTRO", singleLine = true)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Switch(checked = hasLuggage, onCheckedChange = onHasLuggageChange); Text("MALETA / BULTO") }
            UpperNextTextField(stopName, onStopNameChange, "PARADA / REFERENCIA", singleLine = true)
            UpperNextTextField(notes, onNotesChange, "OBSERVACIONES", singleLine = false)
            Text(lastPoint?.let { "GPS: ±${it.accM.toInt()}M" } ?: "GPS: PENDIENTE", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f), enabled = !isEnded && !isDelayActive) { Text("LIMPIAR") }
                Button(onClick = onSave, modifier = Modifier.weight(1f), enabled = !isEnded, colors = ButtonDefaults.buttonColors(containerColor = green)) { Text(if (isDelayActive) "GUARDAR" else "INICIAR") }
            }
        }
    }
}

@Composable
private fun UpperNextTextField(value: String, onValueChange: (String) -> Unit, label: String, singleLine: Boolean) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
    )
}

@Composable
private fun EditTripHeaderDialog(
    trip: Trip,
    onDismiss: () -> Unit,
    onSave: (String, String?, String?, String, Int?, String?, String?, String?, String?, String?, Int?, String?) -> Unit
) {
    val focusManager = LocalFocusManager.current
    fun upper(v: String) = v.uppercase(Locale("es", "MX"))
    var routeName by rememberSaveable(trip.tripId) { mutableStateOf(trip.routeName.uppercase(Locale("es", "MX"))) }
    var company by rememberSaveable(trip.tripId) { mutableStateOf(trip.company?.uppercase(Locale("es", "MX")) ?: "") }
    var vehicleEco by rememberSaveable(trip.tripId) { mutableStateOf(trip.vehicleEco?.uppercase(Locale("es", "MX")) ?: "") }
    var direction by rememberSaveable(trip.tripId) { mutableStateOf(trip.direction.uppercase(Locale("es", "MX"))) }
    var routeNumber by rememberSaveable(trip.tripId) { mutableStateOf(trip.routeNumber?.toString() ?: "") }
    var esFs by rememberSaveable(trip.tripId) { mutableStateOf(trip.esFs?.uppercase(Locale("es", "MX")) ?: "") }
    var baseStart by rememberSaveable(trip.tripId) { mutableStateOf(trip.baseStart?.uppercase(Locale("es", "MX")) ?: "") }
    var baseEnd by rememberSaveable(trip.tripId) { mutableStateOf(trip.baseEnd?.uppercase(Locale("es", "MX")) ?: "") }
    var plateNumber by rememberSaveable(trip.tripId) { mutableStateOf(trip.plateNumber?.uppercase(Locale("es", "MX")) ?: "") }
    var vehicleType by rememberSaveable(trip.tripId) { mutableStateOf(trip.vehicleType?.uppercase(Locale("es", "MX")) ?: "") }
    var seatCapacity by rememberSaveable(trip.tripId) { mutableStateOf(trip.seatCapacity?.toString() ?: "") }
    var headerNotes by rememberSaveable(trip.tripId) { mutableStateOf(trip.notes?.uppercase(Locale("es", "MX")) ?: "") }

    @Composable
    fun NextField(label: String, value: String, change: (String) -> Unit, number: Boolean = false) {
        OutlinedTextField(
            value = value,
            onValueChange = { change(if (number) it.filter { ch -> ch.isDigit() }.take(4) else upper(it)) },
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Text, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("EDITAR ENCABEZADO") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NextField(label = "RUTA / DERROTERO", value = routeName, change = { routeName = it })
                NextField(label = "EMPRESA", value = company, change = { company = it })
                NextField(label = "ECO", value = vehicleEco, change = { vehicleEco = it })
                NextField(label = "SENTIDO", value = direction, change = { direction = it })
                NextField(label = "NO. RECORRIDO", value = routeNumber, change = { routeNumber = it }, number = true)
                NextField(label = "ES / FS", value = esFs, change = { esFs = it })
                NextField(label = "BASE INICIO", value = baseStart, change = { baseStart = it })
                NextField(label = "BASE FINAL", value = baseEnd, change = { baseEnd = it })
                NextField(label = "PLACA", value = plateNumber, change = { plateNumber = it })
                NextField(label = "TIPO VEHÍCULO", value = vehicleType, change = { vehicleType = it })
                NextField(label = "CAPACIDAD / ASIENTOS", value = seatCapacity, change = { seatCapacity = it }, number = true)
                NextField(label = "OBSERVACIONES", value = headerNotes, change = { headerNotes = it })
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    routeName,
                    company.ifBlank { null },
                    vehicleEco.ifBlank { null },
                    direction,
                    routeNumber.toIntOrNull(),
                    esFs.ifBlank { null },
                    baseStart.ifBlank { null },
                    baseEnd.ifBlank { null },
                    plateNumber.ifBlank { null },
                    vehicleType.ifBlank { null },
                    seatCapacity.toIntOrNull(),
                    headerNotes.ifBlank { null }
                )
            }) { Text("GUARDAR") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("CANCELAR") } }
    )
}

private fun formatElapsed(totalSec: Long): String { val h = totalSec / 3600; val m = (totalSec % 3600) / 60; val s = totalSec % 60; return if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s) }

@Composable
private fun CounterBox(label: String, value: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier, maxValue: Int? = null) {
    val atMax = maxValue != null && value >= maxValue
    val isLimitedCounter = maxValue != null
    val valueColor = if (atMax && isLimitedCounter) MaterialTheme.colorScheme.error else Color.Unspecified
    Card(modifier = modifier, border = if (atMax && isLimitedCounter) BorderStroke(1.dp, MaterialTheme.colorScheme.error) else null) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = valueColor)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { onChange((value - 1).coerceAtLeast(0)) }, modifier = Modifier.size(42.dp), contentPadding = PaddingValues(0.dp)) { Text("−", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                Text(value.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = valueColor, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { onChange(value + 1) }, enabled = maxValue == null || value < maxValue, modifier = Modifier.size(42.dp), contentPadding = PaddingValues(0.dp)) { Text("+", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(3, 5, 10).forEach { quickValue ->
                    AssistChip(onClick = { onChange(if (maxValue != null) quickValue.coerceAtMost(maxValue) else quickValue) }, label = { Text(quickValue.toString()) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DelayCodeGrid(codes: List<String>, selected: Set<String>, onToggle: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { codes.chunked(5).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) { row.forEach { code -> FilterChip(selected = selected.contains(code), onClick = { onToggle(code) }, label = { Text(code) }) } } } }
}

@Composable private fun TripHeaderCard(routeName: String, direction: String, start: String, end: String, vehicleEco: String?, plateNumber: String?, seatCapacity: Int?, isEnded: Boolean, onEdit: () -> Unit) { Card { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) { Text("RECORRIDO ASD", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); OutlinedButton(onClick = onEdit, enabled = !isEnded) { Text("EDITAR") } }; Text("RUTA: $routeName"); Text("SENTIDO: $direction"); Text("INICIO: $start"); Text("FIN: $end"); Text("UNIDAD: ECO ${vehicleEco ?: "-"} • PLACA ${plateNumber ?: "-"}"); Text("CAPACIDAD: ${seatCapacity ?: "-"}"); Text(if (isEnded) "ESTADO: CERRADO" else "ESTADO: EN CURSO") } } }
@Composable private fun DemoSummaryCard(summary: AsdDemoSummary) { Card { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text("RESUMEN OPERATIVO", style = MaterialTheme.typography.titleMedium); Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) { SummaryMetric("EVENTOS", summary.events.toString(), Modifier.weight(1f)); SummaryMetric("ASCENSOS", summary.boardings.toString(), Modifier.weight(1f)); SummaryMetric("DESCENSOS", summary.alightings.toString(), Modifier.weight(1f)) }; Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) { SummaryMetric("A BORDO", summary.onBoard.toString(), Modifier.weight(1f)); SummaryMetric("H/M", "${summary.menOnBoard}/${summary.womenOnBoard}", Modifier.weight(1f)); SummaryMetric("GPS", summary.trackPoints.toString(), Modifier.weight(1f)) } } } }
@Composable private fun SummaryMetric(label: String, value: String, modifier: Modifier = Modifier, isError: Boolean = false) { Card(modifier = modifier) { Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(value, style = MaterialTheme.typography.titleLarge, color = if (isError) MaterialTheme.colorScheme.error else Color.Unspecified, fontWeight = FontWeight.Bold); Text(label, style = MaterialTheme.typography.bodySmall, color = if (isError) MaterialTheme.colorScheme.error else Color.Unspecified) } } }
@Composable private fun TrackingStatusCard(trackingAlive: Boolean, lastAgeMs: Long, lastPoint: TrackPoint?, pointCount: Int) { Card { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text("ESTADO DE TRACKING", style = MaterialTheme.typography.titleMedium); Text(if (trackingAlive) "🟢 ACTIVO (${lastAgeMs / 1000}S)" else "🔴 SIN SEÑAL RECIENTE"); lastPoint?.let { Text("PRECISIÓN: ±${it.accM.toInt()} M"); Text("PROVEEDOR: ${it.provider.uppercase(Locale("es", "MX"))}") } ?: Text("AÚN NO HAY PUNTOS."); Text("PUNTOS: $pointCount") } } }
@Composable private fun DistanceCard(distanceKm: Double?, distanceLoading: Boolean, onCalculateAll: () -> Unit, onCalculateRecent: () -> Unit) { Card { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("DISTANCIA", style = MaterialTheme.typography.titleMedium); if (distanceLoading) LinearProgressIndicator(Modifier.fillMaxWidth()) else Text("APROX: ${distanceKm?.let { "%.2f KM".format(it) } ?: "—"}"); Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedButton(enabled = !distanceLoading, onClick = onCalculateRecent, modifier = Modifier.weight(1f)) { Text("15 MIN") }; OutlinedButton(enabled = !distanceLoading, onClick = onCalculateAll, modifier = Modifier.weight(1f)) { Text("TODO") } } } } }
@Composable private fun TripActionsCard(isEnded: Boolean, loadingGps: Boolean, onOpenMap: () -> Unit, onCloseTrip: () -> Unit) { Card { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("ACCIONES", style = MaterialTheme.typography.titleMedium); Button(onClick = onOpenMap, modifier = Modifier.fillMaxWidth()) { Text("VER MAPA") }; OutlinedButton(enabled = !isEnded && !loadingGps, onClick = onCloseTrip, modifier = Modifier.fillMaxWidth()) { Text("CERRAR VIAJE") } } } }
@Composable private fun ExportActionsCard(onExportCsv: () -> Unit, onExportTrack: () -> Unit, onExportGpx: () -> Unit, onExportKml: () -> Unit) { Card { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("EXPORTACIONES", style = MaterialTheme.typography.titleMedium); OutlinedButton(onClick = onExportCsv, modifier = Modifier.fillMaxWidth()) { Text("CSV FINAL") }; OutlinedButton(onClick = onExportKml, modifier = Modifier.fillMaxWidth()) { Text("KML") }; OutlinedButton(onClick = onExportGpx, modifier = Modifier.fillMaxWidth()) { Text("GPX") }; OutlinedButton(onClick = onExportTrack, modifier = Modifier.fillMaxWidth()) { Text("TRACK CSV") } } } }
@Composable private fun EventCard(event: StopEvent, fmt: SimpleDateFormat) { val up = event.paxMenUp + event.paxWomenUp; val down = event.paxMenDown + event.paxWomenDown; Card { Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text("${event.stopType} • ${fmt.format(Date(event.timestamp))}", style = MaterialTheme.typography.titleSmall); if (!event.stopName.isNullOrBlank()) Text("PARADA: ${event.stopName}"); Text("SUBEN: $up (H:${event.paxMenUp} M:${event.paxWomenUp})"); Text("BAJAN: $down (H:${event.paxMenDown} M:${event.paxWomenDown})"); Text("DEMORAS: ${event.delayCodes ?: "-"}"); Text("MALETA/BULTO: ${if (event.hasLuggage) "SÍ" else "NO"}"); if (!event.notes.isNullOrBlank()) Text("NOTAS: ${event.notes}"); Text("WP INICIO: ${event.waypointStopId} • WP CIERRE: ${event.waypointStartId}"); if (event.stopLat != 0.0 || event.stopLon != 0.0) Text("GPS: ${"%.5f".format(event.stopLat)}, ${"%.5f".format(event.stopLon)} (±${event.stopAccM.toInt()}M)") else Text("GPS: PENDIENTE") } } }

private data class AsdDemoSummary(val events: Int, val boardings: Int, val alightings: Int, val delays: Int, val onBoard: Int, val menOnBoard: Int, val womenOnBoard: Int, val trackPoints: Int) { companion object { fun from(stops: List<StopEvent>, pointCount: Int): AsdDemoSummary { var boardings = 0; var alightings = 0; var delays = 0; var onBoard = 0; var menOnBoard = 0; var womenOnBoard = 0; stops.sortedBy { it.timestamp }.forEach { event -> val up = event.paxMenUp + event.paxWomenUp; val down = event.paxMenDown + event.paxWomenDown; val type = event.stopType.uppercase(Locale("es", "MX")); if (!event.delayCodes.isNullOrBlank() || type in setOf("DEMORA", "BANDERA", "DELAY")) delays += 1; boardings += up; alightings += down; onBoard = (onBoard + up - down).coerceAtLeast(0); menOnBoard = (menOnBoard + event.paxMenUp - event.paxMenDown).coerceAtLeast(0); womenOnBoard = (womenOnBoard + event.paxWomenUp - event.paxWomenDown).coerceAtLeast(0) }; return AsdDemoSummary(stops.size, boardings, alightings, delays, onBoard, menOnBoard, womenOnBoard, pointCount) } } }
private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double { val r = 6_371_000.0; val dLat = Math.toRadians(lat2 - lat1); val dLon = Math.toRadians(lon2 - lon1); val a = sin(dLat / 2).pow(2.0) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0); val c = 2 * atan2(sqrt(a), sqrt(1 - a)); return r * c }
private fun distanceMeters(points: List<TrackPoint>): Double { if (points.size < 2) return 0.0; val raw = points.map { LatLng(it.lat, it.lon) }; val smooth = PolylineSmoother.movingAverage(raw, window = 3); val simplified = PolylineSmoother.douglasPeucker(smooth, epsilonMeters = 4.0); var total = 0.0; for (i in 1 until simplified.size) total += haversineMeters(simplified[i - 1].lat, simplified[i - 1].lon, simplified[i].lat, simplified[i].lon); return total }
