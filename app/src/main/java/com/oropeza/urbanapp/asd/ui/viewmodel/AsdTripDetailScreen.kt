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
import com.oropeza.urbanapp.asd.export.CsvExporter
import com.oropeza.urbanapp.asd.location.LocationProvider
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
import com.oropeza.urbanapp.asd.location.PolylineSmoother
import com.oropeza.urbanapp.asd.location.LatLng
import java.net.URLEncoder



class AsdTripDetailVM : ViewModel() {
    fun tripFlow(tripId: Long) = AsdGraph.repo.tripFlow(tripId)
    fun stopsFlow(tripId: Long) = AsdGraph.repo.stopsFlow(tripId)
    fun delaysFlow(tripId: Long) = AsdGraph.repo.delaysFlow(tripId)

    // ✅ TRACKING flows (para UI)
    fun trackLastPointFlow(tripId: Long): Flow<com.oropeza.urbanapp.asd.data.local.TrackPoint?> =
        AsdGraph.repo.trackLastPointFlow(tripId)

    fun trackCountFlow(tripId: Long): Flow<Int> =
        AsdGraph.repo.trackCountFlow(tripId)

    // ✅ TRACKING data (para resumen / export)
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

        // reconstruir TrackPoint “ligero” manteniendo timeMs/acc como aproximación (opcional)
        // o exportar solo lat/lon/time con otra función.
        val rebuilt = points.take(simplified.size).mapIndexed { i, p ->
            p.copy(lat = simplified[i].lat, lon = simplified[i].lon)
        }

        CsvExporter.exportTrackPointsCsv(context, uri, rebuilt)
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

        // ✅ GPS IN
        stopLat: Double,
        stopLon: Double,
        stopAccM: Double,
        stopProvider: String,
        stopFixTime: Long,
        locationStatus: String,

        // ✅ GPS OUT
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


    suspend fun endTripWithFix(
        tripId: Long,
        fix: com.oropeza.urbanapp.asd.location.LocationFix
    ): Boolean = AsdGraph.repo.endTripWithFix(
        tripId = tripId,
        stopLat = fix.lat,
        stopLon = fix.lon,
        stopAccM = fix.accM,
        stopProvider = fix.provider,
        stopFixTime = fix.fixTime,
        locationStatus = fix.status
    )

    suspend fun exportTripGpx(context: Context, tripId: Long, uri: Uri): Boolean {
        val trip = AsdGraph.repo.getTripOnce(tripId) ?: return false
        val points = AsdGraph.repo.getTrackPointsOnce(tripId)
        val stops = AsdGraph.repo.getStopsOnce(tripId)

        // OJO: ya estamos usando StopEvent como tabla maestra (incluye demoras como BANDERA)
        com.oropeza.urbanapp.asd.export.GpxExporter.exportTripGpx(context, uri, trip, points, stops)
        return true
    }



}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AsdTripDetailScreen(
    tripId: Long,
    onBack: () -> Unit
) {
    val vm: AsdTripDetailVM = viewModel()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val gps = remember { LocationProvider(context) }

    val trip by vm.tripFlow(tripId).collectAsState(initial = null)
    val stops by vm.stopsFlow(tripId).collectAsState(initial = emptyList())

    // ✅ Tracking visible
    val lastPoint by vm.trackLastPointFlow(tripId).collectAsState(initial = null)
    val pointCount by vm.trackCountFlow(tripId).collectAsState(initial = 0)

    val fmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("es", "MX")) }

    var showDialog by remember { mutableStateOf(false) }
    var stopClickTime by remember { mutableStateOf(0L) }
    var pendingStopFix by remember { mutableStateOf<com.oropeza.urbanapp.asd.location.LocationFix?>(null) }


    var loadingGps by remember { mutableStateOf(false) }
    var gpsMsg by remember { mutableStateOf<String?>(null) }

    var snackbarText by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Resumen / distancia
    var distanceKm by remember { mutableStateOf<Double?>(null) }
    var distanceLoading by remember { mutableStateOf(false) }

    var routeUrl by remember { mutableStateOf<String?>(null) }
    var routeBuildLoading by remember { mutableStateOf(false) }
    var routeInfo by remember { mutableStateOf<String?>(null) }


    LaunchedEffect(snackbarText) {
        snackbarText?.let {
            snackbarHostState.showSnackbar(it)
            snackbarText = null
        }
    }

    // ✅ Permisos
    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { /* no-op */ }

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

    // ✅ Export launcher (LAYOUT FINAL)
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val ok = vm.exportLayoutFinal(context, tripId, uri)
                snackbarText = if (ok) "CSV exportado ✅" else "No se pudo exportar."
            } catch (e: Exception) {
                snackbarText = e.message ?: "Error exportando CSV."
            }
        }
    }

    // ✅ Export launcher (TRACK CSV)
    val exportTrackLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val ok = vm.exportTrackCsv(context, tripId, uri)
                snackbarText = if (ok) "TRACK exportado ✅ ($pointCount puntos)" else "No se pudo exportar TRACK."
            } catch (e: Exception) {
                snackbarText = e.message ?: "Error exportando TRACK."
            }
        }
    }

    // ✅ Export launcher (GPX)
    val exportGpxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/gpx+xml")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val ok = vm.exportTripGpx(context, tripId, uri)
                snackbarText = if (ok) "GPX exportado ✅" else "No se pudo exportar GPX."
            } catch (e: Exception) {
                snackbarText = e.message ?: "Error exportando GPX."
            }
        }
    }


    fun mapStopType(input: UnifiedEventInput): String =
        when (input.kind) {
            EventKind.ASCENSO -> "ASCENSO"
            EventKind.DESCENSO -> "DESCENSO"
            EventKind.DEMORA -> "DEMORA"
        }

    fun delayCodesString(input: UnifiedEventInput): String? {
        val s = input.delaySet.joinToString(separator = "/") { it.trim() }.trim()
        return s.ifBlank { null }
    }

    // ✅ Helpers para iniciar/parar el service con tripId
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(trip?.routeName ?: "ASD") },
                navigationIcon = {
                    TextButton(onClick = { onBack() }) { Text("Atrás") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { pad ->

        if (trip == null) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val t = trip!!
        val isEnded = t.endTime != null

        // ✅ AUTOSTART: cuando el viaje está en curso, mantenemos tracking encendido
        LaunchedEffect(tripId, isEnded) {
            if (!isEnded) {
                if (gps.hasPermission()) {
                    startTrackingService(tripId)
                } else {
                    snackbarText = "Tip: activa permisos de ubicación para registrar GPS."
                }
            } else {
                stopTrackingService()
            }
        }

        // ✅ Estado visible de tracking (inferido por último punto reciente)
        val aliveWindowMs = 12_000L
        val nowMs = System.currentTimeMillis()
        val lastAgeMs = lastPoint?.let { nowMs - it.timeMs } ?: Long.MAX_VALUE
        val trackingAlive = lastPoint != null && lastAgeMs in 0..aliveWindowMs

        // ✅ Dialog
        if (showDialog) {
            UnifiedEventDialog(
                title = "Registrar evento",
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

                            // ✅ Fix OUT (arranque)
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

                            val stopType = mapStopType(input)

                            val menUp = if (input.kind == EventKind.ASCENSO) input.men else 0
                            val womenUp = if (input.kind == EventKind.ASCENSO) input.women else 0
                            val menDown = if (input.kind == EventKind.DESCENSO) input.men else 0
                            val womenDown = if (input.kind == EventKind.DESCENSO) input.women else 0

                            val delayCodes = delayCodesString(input)

                            val stopEpoch = stopClickTime
                            val startEpoch = fixOut.fixTime

                            vm.addStopDetailed(
                                tripId = tripId,
                                stopType = stopType,
                                stopTimeMs = stopEpoch,
                                startTimeMs = startEpoch,
                                stopName = input.stopName,
                                notes = input.notes,
                                menUp = menUp,
                                womenUp = womenUp,
                                menDown = menDown,
                                womenDown = womenDown,
                                hasLuggage = input.hasLuggage,
                                delayCodes = delayCodes,
                                otherDelayDesc = input.otherDesc,

                                // IN
                                stopLat = fixIn.lat,
                                stopLon = fixIn.lon,
                                stopAccM = fixIn.accM,
                                stopProvider = fixIn.provider,
                                stopFixTime = fixIn.fixTime,
                                locationStatus = fixOut.status, // status final (puedes dejar el de IN si prefieres)

                                // OUT
                                startLat = fixOut.lat,
                                startLon = fixOut.lon,
                                startAccM = fixOut.accM,
                                startProvider = fixOut.provider,
                                startFixTime = fixOut.fixTime
                            )

                            pendingStopFix = null
                            snackbarText = "Evento ✅ IN±${fixIn.accM.toInt()}m | OUT±${fixOut.accM.toInt()}m"
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

        Column(
            Modifier.padding(pad).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card {
                Column(Modifier.padding(12.dp)) {
                    Text("Ruta: ${t.routeName}")
                    Text("Inicio: ${fmt.format(Date(t.startTime))}")
                    Text("Fin: ${t.endTime?.let { fmt.format(Date(it)) } ?: "EN CURSO"}")
                }
            }

            // ✅ Card: Estado de tracking (VISIBLE)
            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Estado de tracking", style = MaterialTheme.typography.titleMedium)

                    Text(
                        when {
                            trackingAlive -> "🟢 Activo (última señal hace ${lastAgeMs / 1000}s)"
                            else -> "🔴 Sin señal reciente"
                        }
                    )

                    lastPoint?.let { p ->
                        Text("Precisión: ±${p.accM.toInt()} m")
                        Text("Proveedor: ${p.provider}")
                    } ?: Text("Aún no hay puntos guardados en este viaje.")

                    Text("Puntos guardados: $pointCount")
                }
            }

            // ✅ Card: Resumen (duración + distancia)
            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Resumen del recorrido", style = MaterialTheme.typography.titleMedium)

                    val endMs = t.endTime ?: System.currentTimeMillis()
                    val durSec = ((endMs - t.startTime).coerceAtLeast(0L)) / 1000L
                    val hh = durSec / 3600
                    val mm = (durSec % 3600) / 60
                    val ss = durSec % 60
                    Text("Duración: %02d:%02d:%02d".format(hh, mm, ss))

                    if (distanceLoading) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    } else {
                        Text("Distancia aprox: ${distanceKm?.let { "%.2f km".format(it) } ?: "—"}")
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            enabled = !distanceLoading,
                            onClick = {
                                distanceLoading = true
                                distanceKm = null
                                scope.launch {
                                    try {
                                        val toMs2 = System.currentTimeMillis()
                                        val fromMs = toMs2 - 15 * 60 * 1000L
                                        val meters = withContext(Dispatchers.IO) {
                                            val pts = vm.getTrackPointsBetweenOnce(tripId, fromMs, toMs2)
                                            distanceMeters(pts)
                                        }
                                        distanceKm = meters / 1000.0
                                    } catch (e: Exception) {
                                        snackbarText = e.message ?: "Error calculando distancia."
                                    } finally {
                                        distanceLoading = false
                                    }
                                }
                            }
                        ) { Text("Calcular (15 min)") }

                        OutlinedButton(
                            enabled = !distanceLoading,
                            onClick = {
                                distanceLoading = true
                                distanceKm = null
                                scope.launch {
                                    try {
                                        val meters = withContext(Dispatchers.IO) {
                                            val pts = vm.getTrackPointsOnce(tripId)
                                            distanceMeters(pts)
                                        }
                                        distanceKm = meters / 1000.0
                                    } catch (e: Exception) {
                                        snackbarText = e.message ?: "Error calculando distancia."
                                    } finally {
                                        distanceLoading = false
                                    }
                                }
                            }
                        ) { Text("Calcular (todo)") }
                    }
                }
            }

            gpsMsg?.let { Text(it) }
            if (loadingGps) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            // ✅ Export layout final
            OutlinedButton(
                onClick = {
                    val safeName =
                        "ASD_${t.tripId}_${SimpleDateFormat("yyyyMMdd_HHmm", Locale("es", "MX")).format(Date())}.csv"
                    exportLauncher.launch(safeName)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Exportar CSV (Layout final)") }

            // ✅ Export trackpoints
            OutlinedButton(
                onClick = {
                    val name = "ASD_track_trip_${t.tripId}.csv"
                    exportTrackLauncher.launch(name)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Exportar TRACK CSV") }

            // ✅ Export GPX
            OutlinedButton(
                onClick = {
                    val name = "ASD_${t.tripId}_${SimpleDateFormat("yyyyMMdd_HHmm", Locale("es", "MX")).format(Date())}.gpx"
                    exportGpxLauncher.launch(name)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Exportar GPX (viaje)") }


            Button(
                enabled = !isEnded && !loadingGps,
                onClick = {
                    if (!requestPermsIfNeeded()) {
                        snackbarText = "Permiso de ubicación requerido."
                        return@Button
                    }

                    scope.launch {
                        try {
                            loadingGps = true
                            gpsMsg = "Marcando IN (inicio del evento)…"

                            // ✅ Fix IN (parada)
                            val fixIn = gps.getBestFixForEvent(
                                targetAccM = 10.0,
                                fallbackAccM = 25.0,
                                timeoutMs = 6_000L,
                                highAccuracy = true
                            )

                            pendingStopFix = fixIn
                            stopClickTime = fixIn.fixTime
                            showDialog = true

                            loadingGps = false
                            gpsMsg = null
                        } catch (e: Exception) {
                            loadingGps = false
                            gpsMsg = null
                            snackbarText = e.message ?: "Error obteniendo GPS (IN)."
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("+ Registrar evento") }


            OutlinedButton(
                enabled = !isEnded && !loadingGps,
                onClick = {
                    if (!requestPermsIfNeeded()) {
                        snackbarText = "Permiso de ubicación requerido."
                        return@OutlinedButton
                    }

                    scope.launch {
                        try {
                            loadingGps = true
                            gpsMsg = "Cerrando… fijando ubicación del final (lecturas múltiples)"

                            // ✅ GPS PRO también para cierre (ASD/FINAL)
                            val fix = gps.getBestFixForEvent(
                                targetAccM = 10.0,
                                fallbackAccM = 25.0,
                                timeoutMs = 7_000L,
                                highAccuracy = true
                            )

                            val ok = vm.endTripWithFix(tripId, fix)

                            loadingGps = false
                            gpsMsg = null

                            // ✅ Al cerrar, paramos tracking siempre
                            stopTrackingService()

                            snackbarText = if (ok) {
                                "Viaje cerrado ✅ (${fix.status}) acc=±${fix.accM.toInt()}m"
                            } else {
                                "No se pudo cerrar el viaje (¿demora activa?)."
                            }
                        } catch (e: Exception) {
                            loadingGps = false
                            gpsMsg = null
                            snackbarText = e.message ?: "Error al cerrar viaje."
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Cerrar viaje") }

            Divider()
            Text("Eventos", style = MaterialTheme.typography.titleMedium)

            LazyColumn {
                items(stops) { s ->
                    Card {
                        Column(Modifier.padding(10.dp)) {
                            Text("${s.stopType} • ${fmt.format(Date(s.timestamp))}")
                            Text("Demoras: ${s.delayCodes ?: "-"}")
                            if (s.stopLat != 0.0 || s.stopLon != 0.0) {
                                Text(
                                    "GPS: ${"%.5f".format(s.stopLat)}, ${"%.5f".format(s.stopLon)} " +
                                            "(±${s.stopAccM.toInt()}m)"
                                )
                            } else {
                                Text("GPS: sin coordenadas")
                            }
                        }
                    }
                }
            }
        }
    }

    fun buildGoogleMapsUrlFromTrack(points: List<com.oropeza.urbanapp.asd.data.local.TrackPoint>): String? {
        if (points.size < 2) return null

        // 1) Convertir
        val raw = points.map { LatLng(it.lat, it.lon) }

        // 2) Suavizar + simplificar
        val smooth = PolylineSmoother.movingAverage(raw, window = 3)
        val simplified = PolylineSmoother.douglasPeucker(smooth, epsilonMeters = 4.0)

        if (simplified.size < 2) return null

        // 3) Google Maps /dir permite waypoints pero tiene límites prácticos.
        //    Tomamos máximo 20 puntos intermedios (muestreo).
        val origin = simplified.first()
        val dest = simplified.last()

        val maxWaypoints = 20
        val middle = simplified.drop(1).dropLast(1)

        val sampled = if (middle.size <= maxWaypoints) {
            middle
        } else {
            val step = (middle.size.toDouble() / maxWaypoints.toDouble()).coerceAtLeast(1.0)
            (0 until maxWaypoints).map { idx -> middle[(idx * step).toInt().coerceAtMost(middle.lastIndex)] }
        }

        val originStr = "${origin.lat},${origin.lon}"
        val destStr = "${dest.lat},${dest.lon}"

        val waypointsStr = if (sampled.isNotEmpty()) {
            sampled.joinToString("|") { "${it.lat},${it.lon}" }
        } else ""

        val urlBase = "https://www.google.com/maps/dir/?api=1"
        val url = if (waypointsStr.isNotBlank()) {
            val wpEnc = URLEncoder.encode(waypointsStr, "UTF-8")
            "$urlBase&origin=$originStr&destination=$destStr&travelmode=driving&waypoints=$wpEnc"
        } else {
            "$urlBase&origin=$originStr&destination=$destStr&travelmode=driving"
        }

        return url
    }

    suspend fun computeRoutePreview(tripId: Long) {
        routeBuildLoading = true
        routeUrl = null
        routeInfo = null

        try {
            val pts = withContext(Dispatchers.IO) { vm.getTrackPointsOnce(tripId) }
            if (pts.size < 2) {
                routeInfo = "No hay suficientes puntos para generar ruta."
                return
            }

            // Info rápida (raw vs suavizado)
            val rawCount = pts.size
            val rawLatLng = pts.map { LatLng(it.lat, it.lon) }
            val smooth = PolylineSmoother.movingAverage(rawLatLng, window = 3)
            val simplified = PolylineSmoother.douglasPeucker(smooth, epsilonMeters = 4.0)

            routeInfo = "Ruta: raw=$rawCount → suavizado=${simplified.size} (w=3, eps=4m)"

            routeUrl = buildGoogleMapsUrlFromTrack(pts)
            if (routeUrl == null) routeInfo = "No se pudo construir URL (puntos inválidos)."
        } catch (e: Exception) {
            routeInfo = e.message ?: "Error generando vista previa de ruta."
        } finally {
            routeBuildLoading = false
        }
    }

}

/** Haversine en metros */
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

private fun distanceMeters(points: List<com.oropeza.urbanapp.asd.data.local.TrackPoint>): Double {
    if (points.size < 2) return 0.0

    // 1) convertir a LatLng
    val raw = points.map { LatLng(it.lat, it.lon) }

    // 2) suavizar (promedio móvil)
    val smooth = PolylineSmoother.movingAverage(raw, window = 3)

    // 3) simplificar (Douglas-Peucker)
    //    Ajusta epsilon: 3–6m. Yo recomiendo 4m para ciudad.
    val simplified = PolylineSmoother.douglasPeucker(smooth, epsilonMeters = 4.0)

    // 4) calcular distancia con la ruta resultante
    var total = 0.0
    for (i in 1 until simplified.size) {
        val a = simplified[i - 1]
        val b = simplified[i]
        total += haversineMeters(a.lat, a.lon, b.lat, b.lon)
    }
    return total
}

