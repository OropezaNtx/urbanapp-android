package com.oropeza.urbanapp.asd.ui.viewmodel

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oropeza.urbanapp.asd.AsdGraph
import com.oropeza.urbanapp.asd.data.local.StopEvent
import com.oropeza.urbanapp.asd.data.local.TrackPoint
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
        fix: LocationFix
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
        stopLat = fix.lat,
        stopLon = fix.lon,
        stopAccM = fix.accM,
        stopProvider = fix.provider,
        stopFixTime = fix.fixTime,
        locationStatus = fix.status,
        startLat = fix.lat,
        startLon = fix.lon,
        startAccM = fix.accM,
        startProvider = fix.provider,
        startFixTime = fix.fixTime
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
fun AsdTripDetailScreen(
    tripId: Long,
    onBack: () -> Unit,
    onOpenMap: (Long) -> Unit
) {
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

    var loadingGps by remember { mutableStateOf(false) }
    var gpsMsg by remember { mutableStateOf<String?>(null) }
    var snackbarText by remember { mutableStateOf<String?>(null) }
    var distanceKm by remember { mutableStateOf<Double?>(null) }
    var distanceLoading by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarText) {
        snackbarText?.let {
            snackbarHostState.showSnackbar(it)
            snackbarText = null
        }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    fun requestPermsIfNeeded(): Boolean {
        if (!gps.hasPermission()) {
            permLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            return false
        }
        return true
    }

    fun startTrackingService() {
        context.startService(Intent(context, TrackingService::class.java).apply {
            action = TrackingService.ACTION_START
            putExtra(TrackingService.EXTRA_TRIP_ID, tripId)
        })
    }

    fun stopTrackingService() {
        context.startService(Intent(context, TrackingService::class.java).apply { action = TrackingService.ACTION_STOP })
    }

    fun resetCaptureForm() {
        menUp = 0
        womenUp = 0
        menDown = 0
        womenDown = 0
        selectedDelayCodes = emptySet()
        otherDelayDesc = ""
        hasLuggage = false
        stopName = ""
        notes = ""
    }

    fun currentFix(now: Long): LocationFix {
        val p = lastPoint
        return if (p != null && p.lat != 0.0 && p.lon != 0.0) {
            LocationFix(p.lat, p.lon, p.accM, p.provider, p.timeMs, if (p.accM <= 20.0) "FIX_USABLE" else "GPS_LAST")
        } else {
            LocationFix(0.0, 0.0, 0.0, "pending", now, "GPS_PENDING")
        }
    }

    fun inferredStopType(): String {
        val hasPax = menUp + womenUp + menDown + womenDown > 0
        val hasDelay = selectedDelayCodes.isNotEmpty() || otherDelayDesc.isNotBlank()
        return if (hasPax) "ASD" else if (hasDelay) "DEMORA" else "ASD"
    }

    fun saveInlineEvent(summary: AsdDemoSummary) {
        if (!requestPermsIfNeeded()) {
            snackbarText = "Permiso de ubicación requerido."
            return
        }
        val up = menUp + womenUp
        val down = menDown + womenDown
        val hasDelay = selectedDelayCodes.isNotEmpty() || otherDelayDesc.isNotBlank()
        val hasPax = up + down > 0
        if (!hasPax && !hasDelay && stopName.isBlank() && notes.isBlank()) {
            snackbarText = "Captura al menos un ascenso, descenso, demora u observación."
            return
        }
        if (down > summary.onBoard + up) {
            snackbarText = "No puedes bajar $down personas si solo van ${summary.onBoard + up} a bordo."
            return
        }
        val now = System.currentTimeMillis()
        val fix = currentFix(now)
        val delayText = selectedDelayCodes.joinToString("/").ifBlank { null }
        scope.launch {
            try {
                vm.addStopDetailed(
                    tripId = tripId,
                    stopType = inferredStopType(),
                    stopTimeMs = now,
                    startTimeMs = now,
                    stopName = stopName.trim().ifBlank { null },
                    notes = notes.trim().ifBlank { null },
                    menUp = menUp,
                    womenUp = womenUp,
                    menDown = menDown,
                    womenDown = womenDown,
                    hasLuggage = hasLuggage,
                    delayCodes = delayText,
                    otherDelayDesc = otherDelayDesc.trim().ifBlank { null },
                    fix = fix
                )
                snackbarText = if (fix.status == "GPS_PENDING") "Evento guardado ✅ GPS pendiente" else "Evento guardado ✅ ${fix.status} ±${fix.accM.toInt()}m"
                resetCaptureForm()
            } catch (e: Exception) {
                snackbarText = e.message ?: "Error al guardar evento."
            }
        }
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch { snackbarText = runCatching { if (vm.exportLayoutFinal(context, tripId, uri)) "CSV final exportado ✅" else "No se pudo exportar CSV." }.getOrElse { it.message ?: "Error exportando CSV." } }
    }
    val exportTrackLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch { snackbarText = runCatching { if (vm.exportTrackCsv(context, tripId, uri)) "TRACK CSV exportado ✅" else "No se pudo exportar TRACK." }.getOrElse { it.message ?: "Error exportando TRACK." } }
    }
    val exportGpxLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch { snackbarText = runCatching { if (vm.exportTripGpx(context, tripId, uri)) "GPX exportado ✅" else "No se pudo exportar GPX." }.getOrElse { it.message ?: "Error exportando GPX." } }
    }
    val exportKmlLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.google-earth.kml+xml")) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch { snackbarText = runCatching { if (vm.exportTripKml(context, tripId, uri)) "KML exportado ✅" else "No se pudo exportar KML." }.getOrElse { it.message ?: "Error exportando KML." } }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(trip?.routeName ?: "ASD") }, navigationIcon = { TextButton(onClick = onBack) { Text("Atrás") } }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { pad ->
        val t = trip
        if (t == null) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val isEnded = t.endTime != null
        val summary = remember(stops, pointCount) { AsdDemoSummary.from(stops, pointCount) }
        val lastAgeMs = lastPoint?.let { System.currentTimeMillis() - it.timeMs } ?: Long.MAX_VALUE
        val trackingAlive = lastPoint != null && lastAgeMs in 0..12_000L

        LaunchedEffect(tripId, isEnded) {
            if (!isEnded) {
                if (gps.hasPermission()) startTrackingService() else snackbarText = "Tip: activa permisos de ubicación para registrar GPS."
            } else stopTrackingService()
        }

        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                InlineAsdCaptureCard(
                    isEnded = isEnded,
                    menUp = menUp,
                    womenUp = womenUp,
                    menDown = menDown,
                    womenDown = womenDown,
                    currentOnBoard = summary.onBoard,
                    selectedDelayCodes = selectedDelayCodes,
                    otherDelayDesc = otherDelayDesc,
                    hasLuggage = hasLuggage,
                    stopName = stopName,
                    notes = notes,
                    lastPoint = lastPoint,
                    onMenUpChange = { menUp = it.coerceAtLeast(0) },
                    onWomenUpChange = { womenUp = it.coerceAtLeast(0) },
                    onMenDownChange = { menDown = it.coerceAtLeast(0) },
                    onWomenDownChange = { womenDown = it.coerceAtLeast(0) },
                    onToggleDelayCode = { code -> selectedDelayCodes = if (selectedDelayCodes.contains(code)) selectedDelayCodes - code else selectedDelayCodes + code },
                    onOtherDelayDescChange = { otherDelayDesc = it },
                    onHasLuggageChange = { hasLuggage = it },
                    onStopNameChange = { stopName = it },
                    onNotesChange = { notes = it },
                    onSave = { saveInlineEvent(summary) },
                    onClear = { resetCaptureForm() }
                )
            }
            gpsMsg?.let { item { Text(it) } }
            if (loadingGps) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            item { TripHeaderCard(t.routeName, t.direction, fmt.format(Date(t.startTime)), t.endTime?.let { fmt.format(Date(it)) } ?: "EN CURSO", t.vehicleEco, t.plateNumber, isEnded) }
            item { DemoSummaryCard(summary) }
            item { TrackingStatusCard(trackingAlive, lastAgeMs, lastPoint, pointCount) }
            item {
                DistanceCard(
                    distanceKm = distanceKm,
                    distanceLoading = distanceLoading,
                    onCalculateAll = {
                        distanceLoading = true
                        distanceKm = null
                        scope.launch {
                            try { distanceKm = withContext(Dispatchers.IO) { distanceMeters(vm.getTrackPointsOnce(tripId)) / 1000.0 } }
                            catch (e: Exception) { snackbarText = e.message ?: "Error calculando distancia." }
                            finally { distanceLoading = false }
                        }
                    },
                    onCalculateRecent = {
                        distanceLoading = true
                        distanceKm = null
                        scope.launch {
                            try {
                                val toMs = System.currentTimeMillis()
                                val fromMs = toMs - 15 * 60 * 1000L
                                distanceKm = withContext(Dispatchers.IO) { distanceMeters(vm.getTrackPointsBetweenOnce(tripId, fromMs, toMs)) / 1000.0 }
                            } catch (e: Exception) { snackbarText = e.message ?: "Error calculando distancia." }
                            finally { distanceLoading = false }
                        }
                    }
                )
            }
            item {
                TripActionsCard(
                    isEnded = isEnded,
                    loadingGps = loadingGps,
                    onOpenMap = { onOpenMap(tripId) },
                    onCloseTrip = {
                        if (!requestPermsIfNeeded()) {
                            snackbarText = "Permiso de ubicación requerido."
                            return@TripActionsCard
                        }
                        scope.launch {
                            try {
                                loadingGps = true
                                gpsMsg = "Cerrando viaje… fijando ubicación final"
                                val fix = gps.getBestFixForEvent(10.0, 25.0, 7_000L, true)
                                val ok = vm.endTripWithFix(tripId, fix)
                                stopTrackingService()
                                snackbarText = if (ok) "Viaje cerrado ✅ (${fix.status}) acc=±${fix.accM.toInt()}m" else "No se pudo cerrar el viaje."
                            } catch (e: Exception) { snackbarText = e.message ?: "Error al cerrar viaje." }
                            finally { loadingGps = false; gpsMsg = null }
                        }
                    }
                )
            }
            item { ExportActionsCard({ exportCsvLauncher.launch("ASD_${t.tripId}_${fileFmt.format(Date())}.csv") }, { exportTrackLauncher.launch("ASD_track_trip_${t.tripId}.csv") }, { exportGpxLauncher.launch("ASD_${t.tripId}_${fileFmt.format(Date())}.gpx") }, { exportKmlLauncher.launch("ASD_${t.tripId}_${fileFmt.format(Date())}.kml") }) }
            item { Text("Eventos registrados", style = MaterialTheme.typography.titleMedium) }
            if (stops.isEmpty()) item { Card { Text("Aún no hay eventos. Usa el bloque superior para registrar.", modifier = Modifier.padding(12.dp)) } }
            else items(stops) { EventCard(it, fmt) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InlineAsdCaptureCard(
    isEnded: Boolean,
    menUp: Int,
    womenUp: Int,
    menDown: Int,
    womenDown: Int,
    currentOnBoard: Int,
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
    onSave: () -> Unit,
    onClear: () -> Unit
) {
    val totalUp = menUp + womenUp
    val totalDown = menDown + womenDown
    val estimatedOnBoard = (currentOnBoard + totalUp - totalDown).coerceAtLeast(0)
    val willAutoAddAd = totalUp + totalDown > 0 && !selectedDelayCodes.contains("AD")
    val delayCodes = listOf("AD", "C", "S", "TM", "CND", "O")

    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Registro operativo ASD", style = MaterialTheme.typography.titleMedium)
            Text("Captura subidas, bajadas y demoras en un solo punto.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SummaryMetric("Suben", totalUp.toString(), Modifier.weight(1f))
                SummaryMetric("Bajan", totalDown.toString(), Modifier.weight(1f))
                SummaryMetric("A bordo", estimatedOnBoard.toString(), Modifier.weight(1f))
            }
            if (willAutoAddAd) AssistChip(onClick = {}, label = { Text("AD se agregará automáticamente") })

            Text("Suben", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                CounterBox("Hombres", menUp, onMenUpChange, Modifier.weight(1f))
                CounterBox("Mujeres", womenUp, onWomenUpChange, Modifier.weight(1f))
            }
            Text("Bajan", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                CounterBox("Hombres", menDown, onMenDownChange, Modifier.weight(1f))
                CounterBox("Mujeres", womenDown, onWomenDownChange, Modifier.weight(1f))
            }
            Text("Demoras", style = MaterialTheme.typography.titleSmall)
            DelayCodeGrid(delayCodes, selectedDelayCodes, onToggleDelayCode)
            Text("AD = Ascenso/Descenso. También puedes marcar C, S, TM, CND u O.", style = MaterialTheme.typography.bodySmall)
            if (selectedDelayCodes.contains("O")) OutlinedTextField(otherDelayDesc, onOtherDelayDescChange, label = { Text("Descripción de otro") }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(checked = hasLuggage, onCheckedChange = onHasLuggageChange)
                Text("Porta maleta / bulto voluminoso")
            }
            OutlinedTextField(stopName, onStopNameChange, label = { Text("Parada / referencia") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(notes, onNotesChange, label = { Text("Observaciones") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            Text(lastPoint?.let { "GPS usado: último punto guardado ±${it.accM.toInt()}m" } ?: "GPS usado: pendiente si aún no hay punto guardado", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f), enabled = !isEnded) { Text("Limpiar") }
                Button(onClick = onSave, modifier = Modifier.weight(1f), enabled = !isEnded) { Text("Guardar punto") }
            }
        }
    }
}

@Composable
private fun CounterBox(label: String, value: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { onChange((value - 1).coerceAtLeast(0)) }, modifier = Modifier.weight(1f)) { Text("−") }
                OutlinedTextField(
                    value = value.toString(),
                    onValueChange = { onChange(it.filter { ch -> ch.isDigit() }.take(3).toIntOrNull() ?: 0) },
                    modifier = Modifier.weight(1.2f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedButton(onClick = { onChange(value + 1) }, modifier = Modifier.weight(1f)) { Text("+") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(3, 5, 10).forEach { quickValue ->
                    AssistChip(onClick = { onChange(quickValue) }, label = { Text(quickValue.toString()) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DelayCodeGrid(codes: List<String>, selected: Set<String>, onToggle: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { codes.take(3).forEach { FilterChip(selected.contains(it), { onToggle(it) }, label = { Text(it) }) } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { codes.drop(3).forEach { FilterChip(selected.contains(it), { onToggle(it) }, label = { Text(it) }) } }
    }
}

@Composable
private fun TripHeaderCard(routeName: String, direction: String, start: String, end: String, vehicleEco: String?, plateNumber: String?, isEnded: Boolean) {
    Card { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text("Recorrido ASD", style = MaterialTheme.typography.titleMedium); Text("Ruta: $routeName"); Text("Sentido: $direction"); Text("Inicio: $start"); Text("Fin: $end"); Text("Unidad: Eco ${vehicleEco ?: "-"} • Placa ${plateNumber ?: "-"}"); Text(if (isEnded) "Estado: CERRADO" else "Estado: EN CURSO") } }
}

@Composable
private fun DemoSummaryCard(summary: AsdDemoSummary) {
    Card { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text("Resumen operativo", style = MaterialTheme.typography.titleMedium); Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) { SummaryMetric("Eventos", summary.events.toString(), Modifier.weight(1f)); SummaryMetric("Ascensos", summary.boardings.toString(), Modifier.weight(1f)); SummaryMetric("Descensos", summary.alightings.toString(), Modifier.weight(1f)) }; Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) { SummaryMetric("A bordo", summary.onBoard.toString(), Modifier.weight(1f)); SummaryMetric("Demoras", summary.delays.toString(), Modifier.weight(1f)); SummaryMetric("GPS", summary.trackPoints.toString(), Modifier.weight(1f)) } } }
}

@Composable
private fun SummaryMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) { Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(value, style = MaterialTheme.typography.titleLarge); Text(label, style = MaterialTheme.typography.bodySmall) } }
}

@Composable
private fun TrackingStatusCard(trackingAlive: Boolean, lastAgeMs: Long, lastPoint: TrackPoint?, pointCount: Int) {
    Card { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text("Estado de tracking", style = MaterialTheme.typography.titleMedium); Text(if (trackingAlive) "🟢 Activo (última señal hace ${lastAgeMs / 1000}s)" else "🔴 Sin señal reciente"); lastPoint?.let { Text("Precisión: ±${it.accM.toInt()} m"); Text("Proveedor: ${it.provider}") } ?: Text("Aún no hay puntos guardados en este viaje."); Text("Puntos guardados: $pointCount") } }
}

@Composable
private fun DistanceCard(distanceKm: Double?, distanceLoading: Boolean, onCalculateAll: () -> Unit, onCalculateRecent: () -> Unit) {
    Card { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Distancia del recorrido", style = MaterialTheme.typography.titleMedium); if (distanceLoading) LinearProgressIndicator(Modifier.fillMaxWidth()) else Text("Distancia aprox: ${distanceKm?.let { "%.2f km".format(it) } ?: "—"}"); Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedButton(enabled = !distanceLoading, onClick = onCalculateRecent, modifier = Modifier.weight(1f)) { Text("15 min") }; OutlinedButton(enabled = !distanceLoading, onClick = onCalculateAll, modifier = Modifier.weight(1f)) { Text("Todo") } } } }
}

@Composable
private fun TripActionsCard(isEnded: Boolean, loadingGps: Boolean, onOpenMap: () -> Unit, onCloseTrip: () -> Unit) {
    Card { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Acciones del recorrido", style = MaterialTheme.typography.titleMedium); Button(onClick = onOpenMap, modifier = Modifier.fillMaxWidth()) { Text("Ver mapa del recorrido") }; OutlinedButton(enabled = !isEnded && !loadingGps, onClick = onCloseTrip, modifier = Modifier.fillMaxWidth()) { Text("Cerrar viaje") } } }
}

@Composable
private fun ExportActionsCard(onExportCsv: () -> Unit, onExportTrack: () -> Unit, onExportGpx: () -> Unit, onExportKml: () -> Unit) {
    Card { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Exportaciones", style = MaterialTheme.typography.titleMedium); OutlinedButton(onClick = onExportCsv, modifier = Modifier.fillMaxWidth()) { Text("Exportar CSV final") }; OutlinedButton(onClick = onExportKml, modifier = Modifier.fillMaxWidth()) { Text("Exportar KML") }; OutlinedButton(onClick = onExportGpx, modifier = Modifier.fillMaxWidth()) { Text("Exportar GPX") }; OutlinedButton(onClick = onExportTrack, modifier = Modifier.fillMaxWidth()) { Text("Exportar TRACK CSV") } } }
}

@Composable
private fun EventCard(event: StopEvent, fmt: SimpleDateFormat) {
    val up = event.paxMenUp + event.paxWomenUp
    val down = event.paxMenDown + event.paxWomenDown
    Card { Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text("${event.stopType} • ${fmt.format(Date(event.timestamp))}", style = MaterialTheme.typography.titleSmall); if (!event.stopName.isNullOrBlank()) Text("Parada: ${event.stopName}"); Text("Suben: $up (H:${event.paxMenUp} M:${event.paxWomenUp})"); Text("Bajan: $down (H:${event.paxMenDown} M:${event.paxWomenDown})"); Text("Demoras: ${event.delayCodes ?: "-"}"); Text("Maleta/Bulto: ${if (event.hasLuggage) "Sí" else "No"}"); if (!event.notes.isNullOrBlank()) Text("Notas: ${event.notes}"); if (event.stopLat != 0.0 || event.stopLon != 0.0) Text("GPS: ${"%.5f".format(event.stopLat)}, ${"%.5f".format(event.stopLon)} (±${event.stopAccM.toInt()}m)") else Text("GPS: pendiente") } }
}

private data class AsdDemoSummary(val events: Int, val boardings: Int, val alightings: Int, val delays: Int, val onBoard: Int, val trackPoints: Int) {
    companion object {
        fun from(stops: List<StopEvent>, pointCount: Int): AsdDemoSummary {
            var boardings = 0
            var alightings = 0
            var delays = 0
            var onBoard = 0
            stops.sortedBy { it.timestamp }.forEach { event ->
                val up = event.paxMenUp + event.paxWomenUp
                val down = event.paxMenDown + event.paxWomenDown
                val type = event.stopType.uppercase(Locale("es", "MX"))
                if (!event.delayCodes.isNullOrBlank() || type in setOf("DEMORA", "BANDERA", "DELAY")) delays += 1
                boardings += up
                alightings += down
                onBoard = (onBoard + up - down).coerceAtLeast(0)
            }
            return AsdDemoSummary(stops.size, boardings, alightings, delays, onBoard, pointCount)
        }
    }
}

private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2.0) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return r * c
}

private fun distanceMeters(points: List<TrackPoint>): Double {
    if (points.size < 2) return 0.0
    val raw = points.map { LatLng(it.lat, it.lon) }
    val smooth = PolylineSmoother.movingAverage(raw, window = 3)
    val simplified = PolylineSmoother.douglasPeucker(smooth, epsilonMeters = 4.0)
    var total = 0.0
    for (i in 1 until simplified.size) total += haversineMeters(simplified[i - 1].lat, simplified[i - 1].lon, simplified[i].lat, simplified[i].lon)
    return total
}
