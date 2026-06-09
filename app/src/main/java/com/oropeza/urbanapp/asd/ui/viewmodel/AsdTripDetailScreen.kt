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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.oropeza.urbanapp.asd.ui.EventKind
import com.oropeza.urbanapp.asd.ui.UnifiedEventDialog
import com.oropeza.urbanapp.asd.ui.UnifiedEventInput
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

    fun trackLastPointFlow(tripId: Long): Flow<TrackPoint?> =
        AsdGraph.repo.trackLastPointFlow(tripId)

    fun trackCountFlow(tripId: Long): Flow<Int> =
        AsdGraph.repo.trackCountFlow(tripId)

    suspend fun getTrackPointsOnce(tripId: Long) =
        AsdGraph.repo.getTrackPointsOnce(tripId)

    suspend fun getTrackPointsBetweenOnce(tripId: Long, fromMs: Long, toMs: Long) =
        AsdGraph.repo.getTrackPointsBetweenOnce(tripId, fromMs, toMs)

    suspend fun exportLayoutFinal(context: Context, tripId: Long, uri: Uri): Boolean {
        val trip = AsdGraph.repo.getTripOnce(tripId) ?: return false
        val stops = AsdGraph.repo.getStopsOnce(tripId)
        val delays = AsdGraph.repo.getDelaysOnce(tripId)
        CsvExporter.exportLayoutFinal(context, uri, trip, stops, delays)
        return true
    }

    suspend fun exportTrackCsv(context: Context, tripId: Long, uri: Uri): Boolean {
        val points = AsdGraph.repo.getTrackPointsOnce(tripId)
        val raw = points.map { LatLng(it.lat, it.lon) }
        val smooth = PolylineSmoother.movingAverage(raw, window = 3)
        val simplified = PolylineSmoother.douglasPeucker(smooth, epsilonMeters = 4.0)
        val rebuilt = points.take(simplified.size).mapIndexed { i, p ->
            p.copy(lat = simplified[i].lat, lon = simplified[i].lon)
        }
        CsvExporter.exportTrackPointsCsv(context, uri, rebuilt)
        return true
    }

    suspend fun exportTripGpx(context: Context, tripId: Long, uri: Uri): Boolean {
        val trip = AsdGraph.repo.getTripOnce(tripId) ?: return false
        val points = AsdGraph.repo.getTrackPointsOnce(tripId)
        val stops = AsdGraph.repo.getStopsOnce(tripId)
        GpxExporter.exportTripGpx(context, uri, trip, points, stops)
        return true
    }

    suspend fun exportTripKml(context: Context, tripId: Long, uri: Uri): Boolean {
        val trip = AsdGraph.repo.getTripOnce(tripId) ?: return false
        val points = AsdGraph.repo.getTrackPointsOnce(tripId)
        val stops = AsdGraph.repo.getStopsOnce(tripId)
        KmlExporter.exportTripKml(context, uri, trip, points, stops)
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
        stopLat: Double,
        stopLon: Double,
        stopAccM: Double,
        stopProvider: String,
        stopFixTime: Long,
        locationStatus: String,
        startLat: Double,
        startLon: Double,
        startAccM: Double,
        startProvider: String,
        startFixTime: Long
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
        stopLat = stopLat,
        stopLon = stopLon,
        stopAccM = stopAccM,
        stopProvider = stopProvider,
        stopFixTime = stopFixTime,
        locationStatus = locationStatus,
        startLat = startLat,
        startLon = startLon,
        startAccM = startAccM,
        startProvider = startProvider,
        startFixTime = startFixTime
    )

    suspend fun endTripWithFix(tripId: Long, fix: LocationFix): Boolean =
        AsdGraph.repo.endTripWithFix(
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
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val gps = remember { LocationProvider(context) }

    val trip by vm.tripFlow(tripId).collectAsState(initial = null)
    val stops by vm.stopsFlow(tripId).collectAsState(initial = emptyList())
    val lastPoint by vm.trackLastPointFlow(tripId).collectAsState(initial = null)
    val pointCount by vm.trackCountFlow(tripId).collectAsState(initial = 0)

    val fmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("es", "MX")) }
    val fileFmt = remember { SimpleDateFormat("yyyyMMdd_HHmm", Locale("es", "MX")) }

    var showDialog by remember { mutableStateOf(false) }
    var stopClickTime by remember { mutableStateOf(0L) }
    var pendingStopFix by remember { mutableStateOf<LocationFix?>(null) }
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

    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { /* permission result handled by next click */ }

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

    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            snackbarText = try {
                if (vm.exportLayoutFinal(context, tripId, uri)) "CSV final exportado ✅" else "No se pudo exportar CSV."
            } catch (e: Exception) {
                e.message ?: "Error exportando CSV."
            }
        }
    }

    val exportTrackLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            snackbarText = try {
                if (vm.exportTrackCsv(context, tripId, uri)) "TRACK CSV exportado ✅" else "No se pudo exportar TRACK."
            } catch (e: Exception) {
                e.message ?: "Error exportando TRACK."
            }
        }
    }

    val exportGpxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/gpx+xml")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            snackbarText = try {
                if (vm.exportTripGpx(context, tripId, uri)) "GPX exportado ✅" else "No se pudo exportar GPX."
            } catch (e: Exception) {
                e.message ?: "Error exportando GPX."
            }
        }
    }

    val exportKmlLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.google-earth.kml+xml")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            snackbarText = try {
                if (vm.exportTripKml(context, tripId, uri)) "KML exportado ✅" else "No se pudo exportar KML."
            } catch (e: Exception) {
                e.message ?: "Error exportando KML."
            }
        }
    }

    fun startTrackingService(tripId: Long) {
        context.startService(Intent(context, TrackingService::class.java).apply {
            action = TrackingService.ACTION_START
            putExtra(TrackingService.EXTRA_TRIP_ID, tripId)
        })
    }

    fun stopTrackingService() {
        context.startService(Intent(context, TrackingService::class.java).apply {
            action = TrackingService.ACTION_STOP
        })
    }

    fun mapStopType(input: UnifiedEventInput): String = when (input.kind) {
        EventKind.ASCENSO -> "ASCENSO"
        EventKind.DESCENSO -> "DESCENSO"
        EventKind.DEMORA -> "DEMORA"
    }

    fun delayCodesString(input: UnifiedEventInput): String? =
        input.delaySet.joinToString(separator = "/") { it.trim() }.trim().ifBlank { null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(trip?.routeName ?: "ASD") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Atrás") } }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { pad ->
        val t = trip
        if (t == null) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val isEnded = t.endTime != null
        val summary = remember(stops, pointCount) { AsdDemoSummary.from(stops, pointCount) }
        val nowMs = System.currentTimeMillis()
        val lastAgeMs = lastPoint?.let { nowMs - it.timeMs } ?: Long.MAX_VALUE
        val trackingAlive = lastPoint != null && lastAgeMs in 0..12_000L

        LaunchedEffect(tripId, isEnded) {
            if (!isEnded) {
                if (gps.hasPermission()) startTrackingService(tripId)
                else snackbarText = "Tip: activa permisos de ubicación para registrar GPS."
            } else {
                stopTrackingService()
            }
        }

        if (showDialog) {
            UnifiedEventDialog(
                title = "Registrar evento ASD",
                onDismiss = { showDialog = false },
                onConfirm = { input ->
                    scope.launch {
                        try {
                            if (!requestPermsIfNeeded()) {
                                snackbarText = "Permiso de ubicación requerido."
                                return@launch
                            }

                            val fixIn = pendingStopFix
                            if (fixIn == null) {
                                snackbarText = "No se pudo obtener el punto IN. Intenta de nuevo."
                                showDialog = false
                                return@launch
                            }

                            loadingGps = true
                            gpsMsg = "Marcando OUT (fin del evento)…"
                            val fixOut = gps.getBestFixForEvent(
                                targetAccM = 10.0,
                                fallbackAccM = 25.0,
                                timeoutMs = 6_000L,
                                highAccuracy = true
                            )
                            loadingGps = false
                            gpsMsg = null

                            val menUp = if (input.kind == EventKind.ASCENSO) input.men else 0
                            val womenUp = if (input.kind == EventKind.ASCENSO) input.women else 0
                            val menDown = if (input.kind == EventKind.DESCENSO) input.men else 0
                            val womenDown = if (input.kind == EventKind.DESCENSO) input.women else 0

                            vm.addStopDetailed(
                                tripId = tripId,
                                stopType = mapStopType(input),
                                stopTimeMs = stopClickTime,
                                startTimeMs = fixOut.fixTime,
                                stopName = input.stopName,
                                notes = input.notes,
                                menUp = menUp,
                                womenUp = womenUp,
                                menDown = menDown,
                                womenDown = womenDown,
                                hasLuggage = input.hasLuggage,
                                delayCodes = delayCodesString(input),
                                otherDelayDesc = input.otherDesc,
                                stopLat = fixIn.lat,
                                stopLon = fixIn.lon,
                                stopAccM = fixIn.accM,
                                stopProvider = fixIn.provider,
                                stopFixTime = fixIn.fixTime,
                                locationStatus = fixOut.status,
                                startLat = fixOut.lat,
                                startLon = fixOut.lon,
                                startAccM = fixOut.accM,
                                startProvider = fixOut.provider,
                                startFixTime = fixOut.fixTime
                            )

                            pendingStopFix = null
                            snackbarText = "Evento guardado ✅ IN±${fixIn.accM.toInt()}m | OUT±${fixOut.accM.toInt()}m"
                        } catch (e: Exception) {
                            loadingGps = false
                            gpsMsg = null
                            snackbarText = e.message ?: "Error al registrar evento."
                        } finally {
                            showDialog = false
                        }
                    }
                }
            )
        }

        LazyColumn(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                TripHeaderCard(
                    routeName = t.routeName,
                    direction = t.direction,
                    start = fmt.format(Date(t.startTime)),
                    end = t.endTime?.let { fmt.format(Date(it)) } ?: "EN CURSO",
                    vehicleEco = t.vehicleEco,
                    plateNumber = t.plateNumber,
                    isEnded = isEnded
                )
            }

            item {
                DemoSummaryCard(summary = summary)
            }

            item {
                TrackingStatusCard(
                    trackingAlive = trackingAlive,
                    lastAgeMs = lastAgeMs,
                    lastPoint = lastPoint,
                    pointCount = pointCount
                )
            }

            item {
                DistanceCard(
                    distanceKm = distanceKm,
                    distanceLoading = distanceLoading,
                    onCalculateAll = {
                        distanceLoading = true
                        distanceKm = null
                        scope.launch {
                            try {
                                val meters = withContext(Dispatchers.IO) {
                                    distanceMeters(vm.getTrackPointsOnce(tripId))
                                }
                                distanceKm = meters / 1000.0
                            } catch (e: Exception) {
                                snackbarText = e.message ?: "Error calculando distancia."
                            } finally {
                                distanceLoading = false
                            }
                        }
                    },
                    onCalculateRecent = {
                        distanceLoading = true
                        distanceKm = null
                        scope.launch {
                            try {
                                val toMs = System.currentTimeMillis()
                                val fromMs = toMs - 15 * 60 * 1000L
                                val meters = withContext(Dispatchers.IO) {
                                    distanceMeters(vm.getTrackPointsBetweenOnce(tripId, fromMs, toMs))
                                }
                                distanceKm = meters / 1000.0
                            } catch (e: Exception) {
                                snackbarText = e.message ?: "Error calculando distancia."
                            } finally {
                                distanceLoading = false
                            }
                        }
                    }
                )
            }

            item {
                DemoActionsCard(
                    isEnded = isEnded,
                    loadingGps = loadingGps,
                    onRegisterEvent = {
                        if (!requestPermsIfNeeded()) {
                            snackbarText = "Permiso de ubicación requerido."
                            return@DemoActionsCard
                        }
                        scope.launch {
                            try {
                                loadingGps = true
                                gpsMsg = "Marcando IN (inicio del evento)…"
                                val fixIn = gps.getBestFixForEvent(
                                    targetAccM = 10.0,
                                    fallbackAccM = 25.0,
                                    timeoutMs = 6_000L,
                                    highAccuracy = true
                                )
                                pendingStopFix = fixIn
                                stopClickTime = fixIn.fixTime
                                showDialog = true
                            } catch (e: Exception) {
                                snackbarText = e.message ?: "Error obteniendo GPS (IN)."
                            } finally {
                                loadingGps = false
                                gpsMsg = null
                            }
                        }
                    },
                    onOpenMap = { onOpenMap(tripId) },
                    onCloseTrip = {
                        if (!requestPermsIfNeeded()) {
                            snackbarText = "Permiso de ubicación requerido."
                            return@DemoActionsCard
                        }
                        scope.launch {
                            try {
                                loadingGps = true
                                gpsMsg = "Cerrando viaje… fijando ubicación final"
                                val fix = gps.getBestFixForEvent(
                                    targetAccM = 10.0,
                                    fallbackAccM = 25.0,
                                    timeoutMs = 7_000L,
                                    highAccuracy = true
                                )
                                val ok = vm.endTripWithFix(tripId, fix)
                                stopTrackingService()
                                snackbarText = if (ok) {
                                    "Viaje cerrado ✅ (${fix.status}) acc=±${fix.accM.toInt()}m"
                                } else {
                                    "No se pudo cerrar el viaje. Revisa si hay una demora activa."
                                }
                            } catch (e: Exception) {
                                snackbarText = e.message ?: "Error al cerrar viaje."
                            } finally {
                                loadingGps = false
                                gpsMsg = null
                            }
                        }
                    }
                )
            }

            item {
                ExportActionsCard(
                    onExportCsv = {
                        exportCsvLauncher.launch("ASD_${t.tripId}_${fileFmt.format(Date())}.csv")
                    },
                    onExportTrack = {
                        exportTrackLauncher.launch("ASD_track_trip_${t.tripId}.csv")
                    },
                    onExportGpx = {
                        exportGpxLauncher.launch("ASD_${t.tripId}_${fileFmt.format(Date())}.gpx")
                    },
                    onExportKml = {
                        exportKmlLauncher.launch("ASD_${t.tripId}_${fileFmt.format(Date())}.kml")
                    }
                )
            }

            gpsMsg?.let { msg -> item { Text(msg) } }
            if (loadingGps) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }

            item {
                Text("Eventos registrados", style = MaterialTheme.typography.titleMedium)
            }

            if (stops.isEmpty()) {
                item {
                    Card {
                        Text(
                            "Aún no hay eventos. Registra ascensos, descensos o demoras para la demo.",
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            } else {
                items(stops) { event ->
                    EventCard(event = event, fmt = fmt)
                }
            }
        }
    }
}

@Composable
private fun TripHeaderCard(
    routeName: String,
    direction: String,
    start: String,
    end: String,
    vehicleEco: String?,
    plateNumber: String?,
    isEnded: Boolean
) {
    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Recorrido ASD", style = MaterialTheme.typography.titleMedium)
            Text("Ruta: $routeName")
            Text("Sentido: $direction")
            Text("Inicio: $start")
            Text("Fin: $end")
            Text("Unidad: Eco ${vehicleEco ?: "-"} • Placa ${plateNumber ?: "-"}")
            Text(if (isEnded) "Estado: CERRADO" else "Estado: EN CURSO")
        }
    }
}

@Composable
private fun DemoSummaryCard(summary: AsdDemoSummary) {
    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Resumen operativo", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SummaryMetric("Eventos", summary.events.toString(), Modifier.weight(1f))
                SummaryMetric("Ascensos", summary.boardings.toString(), Modifier.weight(1f))
                SummaryMetric("Descensos", summary.alightings.toString(), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SummaryMetric("A bordo", summary.onBoard.toString(), Modifier.weight(1f))
                SummaryMetric("Demoras", summary.delays.toString(), Modifier.weight(1f))
                SummaryMetric("GPS", summary.trackPoints.toString(), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SummaryMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TrackingStatusCard(
    trackingAlive: Boolean,
    lastAgeMs: Long,
    lastPoint: TrackPoint?,
    pointCount: Int
) {
    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Estado de tracking", style = MaterialTheme.typography.titleMedium)
            Text(if (trackingAlive) "🟢 Activo (última señal hace ${lastAgeMs / 1000}s)" else "🔴 Sin señal reciente")
            lastPoint?.let { p ->
                Text("Precisión: ±${p.accM.toInt()} m")
                Text("Proveedor: ${p.provider}")
            } ?: Text("Aún no hay puntos guardados en este viaje.")
            Text("Puntos guardados: $pointCount")
        }
    }
}

@Composable
private fun DistanceCard(
    distanceKm: Double?,
    distanceLoading: Boolean,
    onCalculateAll: () -> Unit,
    onCalculateRecent: () -> Unit
) {
    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Distancia del recorrido", style = MaterialTheme.typography.titleMedium)
            if (distanceLoading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                Text("Distancia aprox: ${distanceKm?.let { "%.2f km".format(it) } ?: "—"}")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(enabled = !distanceLoading, onClick = onCalculateRecent, modifier = Modifier.weight(1f)) {
                    Text("15 min")
                }
                OutlinedButton(enabled = !distanceLoading, onClick = onCalculateAll, modifier = Modifier.weight(1f)) {
                    Text("Todo")
                }
            }
        }
    }
}

@Composable
private fun DemoActionsCard(
    isEnded: Boolean,
    loadingGps: Boolean,
    onRegisterEvent: () -> Unit,
    onOpenMap: () -> Unit,
    onCloseTrip: () -> Unit
) {
    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Acciones de demo", style = MaterialTheme.typography.titleMedium)
            Button(
                enabled = !isEnded && !loadingGps,
                onClick = onRegisterEvent,
                modifier = Modifier.fillMaxWidth()
            ) { Text("+ Registrar evento") }
            Button(onClick = onOpenMap, modifier = Modifier.fillMaxWidth()) {
                Text("Ver mapa del recorrido")
            }
            OutlinedButton(
                enabled = !isEnded && !loadingGps,
                onClick = onCloseTrip,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Cerrar viaje") }
        }
    }
}

@Composable
private fun ExportActionsCard(
    onExportCsv: () -> Unit,
    onExportTrack: () -> Unit,
    onExportGpx: () -> Unit,
    onExportKml: () -> Unit
) {
    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Exportaciones", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = onExportCsv, modifier = Modifier.fillMaxWidth()) { Text("Exportar CSV final") }
            OutlinedButton(onClick = onExportKml, modifier = Modifier.fillMaxWidth()) { Text("Exportar KML") }
            OutlinedButton(onClick = onExportGpx, modifier = Modifier.fillMaxWidth()) { Text("Exportar GPX") }
            OutlinedButton(onClick = onExportTrack, modifier = Modifier.fillMaxWidth()) { Text("Exportar TRACK CSV") }
        }
    }
}

@Composable
private fun EventCard(event: StopEvent, fmt: SimpleDateFormat) {
    val up = event.paxMenUp + event.paxWomenUp
    val down = event.paxMenDown + event.paxWomenDown
    Card {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${event.stopType} • ${fmt.format(Date(event.timestamp))}", style = MaterialTheme.typography.titleSmall)
            if (!event.stopName.isNullOrBlank()) Text("Parada: ${event.stopName}")
            Text("Suben: $up (H:${event.paxMenUp} M:${event.paxWomenUp})")
            Text("Bajan: $down (H:${event.paxMenDown} M:${event.paxWomenDown})")
            Text("Demoras: ${event.delayCodes ?: "-"}")
            Text("Maleta/Bulto: ${if (event.hasLuggage) "Sí" else "No"}")
            if (!event.notes.isNullOrBlank()) Text("Notas: ${event.notes}")
            if (event.stopLat != 0.0 || event.stopLon != 0.0) {
                Text("GPS IN: ${"%.5f".format(event.stopLat)}, ${"%.5f".format(event.stopLon)} (±${event.stopAccM.toInt()}m)")
            } else {
                Text("GPS IN: sin coordenadas")
            }
            if (event.startLat != 0.0 || event.startLon != 0.0) {
                Text("GPS OUT: ${"%.5f".format(event.startLat)}, ${"%.5f".format(event.startLon)} (±${event.startAccM.toInt()}m)")
            }
        }
    }
}

private data class AsdDemoSummary(
    val events: Int,
    val boardings: Int,
    val alightings: Int,
    val delays: Int,
    val onBoard: Int,
    val trackPoints: Int
) {
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
                val isDelay = !event.delayCodes.isNullOrBlank() || type in setOf("DEMORA", "BANDERA", "DELAY")

                if (up > 0 || type == "ASCENSO") boardings += up
                if (down > 0 || type == "DESCENSO") alightings += down
                if (isDelay) delays += 1

                onBoard += up - down
                if (onBoard < 0) onBoard = 0
            }

            return AsdDemoSummary(
                events = stops.size,
                boardings = boardings,
                alightings = alightings,
                delays = delays,
                onBoard = onBoard,
                trackPoints = pointCount
            )
        }
    }
}

private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2.0) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2).pow(2.0)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return r * c
}

private fun distanceMeters(points: List<TrackPoint>): Double {
    if (points.size < 2) return 0.0
    val raw = points.map { LatLng(it.lat, it.lon) }
    val smooth = PolylineSmoother.movingAverage(raw, window = 3)
    val simplified = PolylineSmoother.douglasPeucker(smooth, epsilonMeters = 4.0)
    var total = 0.0
    for (i in 1 until simplified.size) {
        total += haversineMeters(
            simplified[i - 1].lat,
            simplified[i - 1].lon,
            simplified[i].lat,
            simplified[i].lon
        )
    }
    return total
}
