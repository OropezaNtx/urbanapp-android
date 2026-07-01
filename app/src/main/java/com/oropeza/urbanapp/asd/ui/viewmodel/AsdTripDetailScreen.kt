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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oropeza.urbanapp.asd.AsdGraph
import com.oropeza.urbanapp.asd.data.local.AsdTripSyncStatus
import com.oropeza.urbanapp.asd.data.local.StopEvent
import com.oropeza.urbanapp.asd.data.local.TrackPoint
import com.oropeza.urbanapp.asd.data.local.Trip
import com.oropeza.urbanapp.asd.export.CsvExporter
import com.oropeza.urbanapp.asd.export.AsdClientXlsxExporter
import com.oropeza.urbanapp.asd.export.GpsAuditCsvExporter
import com.oropeza.urbanapp.asd.export.GpxExporter
import com.oropeza.urbanapp.asd.export.KmlExporter
import com.oropeza.urbanapp.asd.export.AsdGarminGpxExporter
import com.oropeza.urbanapp.asd.location.LatLng
import com.oropeza.urbanapp.asd.location.LocationFix
import com.oropeza.urbanapp.asd.location.LocationProvider
import com.oropeza.urbanapp.asd.location.PolylineSmoother
import com.oropeza.urbanapp.asd.location.TrackingService
import com.oropeza.urbanapp.asd.sync.AsdCloudSyncWorker
import com.oropeza.urbanapp.core.events.UrbanEventFactory
import com.oropeza.urbanapp.core.events.UrbanEventTypes
import com.oropeza.urbanapp.core.runtime.UrbanRuntime
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
import com.oropeza.urbanapp.asd.export.TrackCsvExporter

class AsdTripDetailVM : ViewModel() {
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

    suspend fun getTripOnce(tripId: Long) = AsdGraph.repo.getTripOnce(tripId)
    suspend fun getStopsOnce(tripId: Long) = AsdGraph.repo.getStopsOnce(tripId)
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
        notes: String?,
        aforador: String?,
        supervisor: String?,
        deviceNumber: String?,
        observerSex: String?
    ): Boolean = AsdGraph.repo.updateTripHeader(
        tripId = tripId,
        routeName = routeName,
        company = company,
        vehicleEco = vehicleEco,
        direction = direction,
        routeNumber = routeNumber,
        esFs = esFs,
        baseStart = baseStart,
        baseEnd = baseEnd,
        plateNumber = plateNumber,
        vehicleType = vehicleType,
        seatCapacity = seatCapacity,
        notes = notes,
        aforador = aforador,
        supervisor = supervisor,
        deviceNumber = deviceNumber,
        observerSex = observerSex
    )

    suspend fun exportLayoutFinal(context: Context, tripId: Long, uri: Uri): Boolean {
        val trip = AsdGraph.repo.getTripOnce(tripId) ?: return false
        CsvExporter.exportLayoutFinal(context, uri, trip, AsdGraph.repo.getStopsOnce(tripId), AsdGraph.repo.getDelaysOnce(tripId))
        return true
    }

    suspend fun exportClientXlsx(context: Context, tripId: Long, uri: Uri): Boolean {
        val trip = AsdGraph.repo.getTripOnce(tripId) ?: return false
        val events = AsdGraph.repo.getStopsOnce(tripId)
        val points = AsdGraph.repo.getTrackPointsOnce(tripId)
        AsdClientXlsxExporter.export(context, uri, trip, events, points)
        return true
    }

    suspend fun exportTrackCsv(context: Context, tripId: Long, uri: Uri): Boolean {
        val points = AsdGraph.repo.getTrackPointsOnce(tripId)
        val raw = points.map { LatLng(it.lat, it.lon) }
        val smooth = PolylineSmoother.movingAverage(raw, window = 3)
        val simplified = PolylineSmoother.douglasPeucker(smooth, epsilonMeters = 4.0)
        val rebuilt = points.take(simplified.size).mapIndexed { i, p -> p.copy(lat = simplified[i].lat, lon = simplified[i].lon) }
        TrackCsvExporter.exportTrackPointsCsv(context, uri, rebuilt)
        return true
    }

    suspend fun exportGpsAuditCsv(context: Context, tripId: Long, uri: Uri): Boolean {
        val points = AsdGraph.repo.getTrackPointsOnce(tripId)
        GpsAuditCsvExporter.export(context, uri, points)
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

    suspend fun exportGarminTrack(context: Context, tripId: Long, uri: Uri): Boolean {
        val trip = AsdGraph.repo.getTripOnce(tripId) ?: return false
        val points = AsdGraph.repo.getTrackPointsOnce(tripId)
        AsdGarminGpxExporter.exportGarminTrack(context, uri, trip, points)
        return true
    }

    suspend fun exportGarminWaypoints(context: Context, tripId: Long, uri: Uri): Boolean {
        val stops = AsdGraph.repo.getStopsOnce(tripId)
        AsdGarminGpxExporter.exportGarminWaypoints(context, uri, stops)
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
    ) {
        AsdGraph.repo.addStopDetailed(
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
            stopAltM = stopFix.altM,
            stopAccM = stopFix.accM,
            stopProvider = stopFix.provider,
            stopFixTime = stopFix.fixTime,
            locationStatus = stopFix.status,
            startLat = startFix.lat,
            startLon = startFix.lon,
            startAltM = startFix.altM,
            startAccM = startFix.accM,
            startProvider = startFix.provider,
            startFixTime = startFix.fixTime
        )
        UrbanRuntime.publishEvent(UrbanEventFactory.asd(UrbanEventTypes.ASD_EVENT_CREATED, mapOf("tripId" to tripId, "type" to stopType)))
    }

    suspend fun endTripWithFix(tripId: Long, fix: LocationFix): Boolean {
        val ok = AsdGraph.repo.endTripWithFix(
            tripId = tripId,
            stopLat = fix.lat,
            stopLon = fix.lon,
            stopAltM = fix.altM,
            stopAccM = fix.accM,
            stopProvider = fix.provider,
            stopFixTime = fix.fixTime,
            locationStatus = fix.status
        )
        if (ok) {
            UrbanRuntime.publishEvent(UrbanEventFactory.asd(UrbanEventTypes.ASD_TRIP_CLOSED, mapOf("tripId" to tripId)))
        }
        return ok
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AsdTripDetailScreen(
    tripId: Long,
    onBack: () -> Unit,
    onOpenMap: (Long) -> Unit,
    vm: AsdTripDetailVM = viewModel()
) {
    val haptic = LocalHapticFeedback.current
    fun triggerHaptic() = haptic.performHapticFeedback(HapticFeedbackType.LongPress)

    val context = LocalContext.current
    val gps = remember { LocationProvider(context) }
    val scope = rememberCoroutineScope()
    val trip by vm.tripFlow(tripId).collectAsState(initial = null)
    val stops by vm.stopsFlow(tripId).collectAsState(initial = emptyList())
    val lastPoint by vm.trackLastPointFlow(tripId).collectAsState(initial = null)
    val pointCount by vm.trackCountFlow(tripId).collectAsState(initial = 0)

    val pendingSyncCount by vm.syncPendingCount.collectAsState(initial = 0)
    val lastSyncTimeMs by vm.lastSyncTime.collectAsState(initial = null)
    val syncStatus by vm.tripSyncStatusFlow(tripId).collectAsState(initial = AsdTripSyncStatus.NOT_QUEUED)
    
    // Optimización: Calcular estadísticas de paradas solo cuando cambian las paradas
    val stopsSummary by remember(stops) { 
        derivedStateOf { AsdDemoSummary.from(stops, 0) } 
    }
    // Combinar con pointCount de forma eficiente
    val summary = remember(stopsSummary, pointCount) {
        stopsSummary.copy(trackPoints = pointCount)
    }

    val trackPoints by vm.trackPointsFlow(tripId).collectAsState(initial = emptyList())
    var qualitySummary by remember { mutableStateOf("-") }
    var openingMap by remember { mutableStateOf(false) }

    LaunchedEffect(trackPoints.size) {
        if (trackPoints.isNotEmpty()) {
            qualitySummary = withContext(Dispatchers.Default) {
                buildGpsQualitySummary(trackPoints)
            }
        }
    }

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
    var activeDelayAltM by rememberSaveable { mutableStateOf(0.0) }
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

    fun gpsLog(msg: String) {
        Log.d("ASD_GPS_CAPTURE", msg)
    }

    fun LocationFix.isUsableForEvent(): Boolean {
        return lat != 0.0 &&
                lon != 0.0 &&
                accM > 0.0 &&
                accM <= 45.0 &&
                status != "GPS_PENDING"
    }


    val bgApp = Color(0xFF07110F)
    val greenAcc = Color(0xFF35D36B)

    LaunchedEffect(trip?.endTime) {
        if (trip?.endTime == null) {
            while (true) {
                tickMs = System.currentTimeMillis()
                kotlinx.coroutines.delay(1000L)
            }
        }
    }

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

    fun startTrackingService() = context.startService(Intent(context, TrackingService::class.java).apply {
        action = TrackingService.ACTION_START
        putExtra(TrackingService.EXTRA_TRIP_ID, tripId)
    })

    fun stopTrackingService() = context.startService(Intent(context, TrackingService::class.java).apply {
        action = TrackingService.ACTION_STOP
    })

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

    fun clearActiveDelay() {
        activeDelayStartMs = 0L
        activeDelayLat = 0.0
        activeDelayLon = 0.0
        activeDelayAltM = 0.0
        activeDelayAccM = 0.0
        activeDelayProvider = ""
        activeDelayFixTime = 0L
        activeDelayStatus = "GPS_PENDING"
    }

    fun currentFix(now: Long): LocationFix {
        val p = lastPoint
        val ageMs = p?.let { now - it.timeMs } ?: Long.MAX_VALUE

        val isRecent = p != null && ageMs <= 15_000L
        val isAccurate = p != null && p.accM <= 45.0
        val isValid = p != null && p.lat != 0.0 && p.lon != 0.0

        gpsLog(
            "currentFix() lastPoint=" +
                    "exists=${p != null}, " +
                    "valid=$isValid, " +
                    "recent=$isRecent, " +
                    "ageMs=$ageMs, " +
                    "acc=${p?.accM}, " +
                    "lat=${p?.lat}, lon=${p?.lon}"
        )

        return if (p != null && isValid && isRecent && isAccurate) {
            LocationFix(
                lat = p.lat,
                lon = p.lon,
                altM = p.altM,
                accM = p.accM,
                provider = p.provider,
                fixTime = p.timeMs,
                status = "FIX_USABLE"
            )
        } else {
            LocationFix(
                lat = 0.0,
                lon = 0.0,
                altM = 0.0,
                accM = 0.0,
                provider = "pending",
                fixTime = now,
                status = "GPS_PENDING"
            )
        }
    }

    fun activeStartFix() = LocationFix(
        activeDelayLat,
        activeDelayLon,
        activeDelayAccM,
        activeDelayAltM,
        activeDelayProvider.ifBlank { "pending" },
        activeDelayFixTime,
        activeDelayStatus
    )

    fun ensureActiveDelayFromInput() {
        if (isDelayActive || trip?.endTime != null) return
        if (!requestPermsIfNeeded()) return
        val now = System.currentTimeMillis()
        val fix = currentFix(now)
        gpsLog(
            "ensureActiveDelayFromInput() " +
                    "fixStatus=${fix.status}, " +
                    "lat=${fix.lat}, lon=${fix.lon}, acc=${fix.accM}, " +
                    "provider=${fix.provider}"
        )
        activeDelayStartMs = now
        activeDelayLat = fix.lat
        activeDelayLon = fix.lon
        activeDelayAltM = fix.altM
        activeDelayAccM = fix.accM
        activeDelayProvider = fix.provider
        activeDelayFixTime = fix.fixTime
        activeDelayStatus = fix.status
    }

    fun inferredStopType(): String = if (menUp + womenUp + menDown + womenDown > 0) "ASD" else "DEMORA"
    fun selectedDelayText(): String? = selectedDelayCodes.joinToString("/").ifBlank { null }
    fun upper(value: String): String = value.uppercase(Locale("es", "MX"))
    fun hasValidDelayCause(): Boolean = (menUp + womenUp + menDown + womenDown > 0) || selectedDelayCodes.isNotEmpty() || otherDelayDesc.isNotBlank()

    fun validateGenderOnBoard(summary: AsdDemoSummary, trip: Trip?): String? {
        val protectedMen = if (trip?.observerSex == "H") 1 else 0
        val protectedWomen = if (trip?.observerSex == "M") 1 else 0

        val maxM = (summary.menOnBoard + menUp - protectedMen).coerceAtLeast(0)
        val maxW = (summary.womenOnBoard + womenUp - protectedWomen).coerceAtLeast(0)

        if (menDown > maxM) {
            return if (protectedMen > 0 && summary.menOnBoard + menUp <= 1) "No puedes bajar al operador durante el levantamiento."
            else "No puedes bajar $menDown hombres si solo hay $maxM disponibles (protegiendo operador)."
        }
        if (womenDown > maxW) {
            return if (protectedWomen > 0 && summary.womenOnBoard + womenUp <= 1) "No puedes bajar a la operadora durante el levantamiento."
            else "No puedes bajar $womenDown mujeres si solo hay $maxW disponibles (protegiendo operador)."
        }
        return null
    }

    fun closeActiveDelay(summary: AsdDemoSummary) {
        if (!isDelayActive) return
        if (!requestPermsIfNeeded()) {
            snackbarText = "Permiso de ubicación requerido."
            return
        }
        if (!hasValidDelayCause()) {
            snackbarText = "El registro debe tener ascenso/descenso o una demora seleccionada."
            return
        }
        validateGenderOnBoard(summary, trip)?.let {
            snackbarText = it
            return
        }
        val now = System.currentTimeMillis()
        var endFix = currentFix(now)
        
        scope.launch {
            try {
                if (endFix.status == "GPS_PENDING") {
                    loadingGps = true
                    gpsMsg = "Obteniendo ubicación final…"
                    endFix = gps.getBestFixForEvent(targetAccM = 15.0, fallbackAccM = 45.0, timeoutMs = 2_500L)
                }

                // BACKFILL: Si el inicio estaba pendiente pero tenemos fin usable, usamos el fin para el inicio
                val effectiveStartFix = if (activeDelayStatus == "GPS_PENDING" && endFix.status != "GPS_PENDING") {
                    endFix.copy(status = "FIX_BACKFILLED_FROM_END")
                } else {
                    activeStartFix()
                }

                gpsLog(
                    "closeActiveDelay() BEFORE_SAVE " +
                            "startStatus=${effectiveStartFix.status}, " +
                            "startLat=${effectiveStartFix.lat}, startLon=${effectiveStartFix.lon}, startAcc=${effectiveStartFix.accM}, " +
                            "endStatus=${endFix.status}, " +
                            "endLat=${endFix.lat}, endLon=${endFix.lon}, endAcc=${endFix.accM}, " +
                            "type=${inferredStopType()}, " +
                            "delayCodes=${selectedDelayText()}"
                )

                vm.addStopDetailed(
                    tripId,
                    inferredStopType(),
                    activeDelayStartMs,
                    now,
                    stopName.trim().ifBlank { null },
                    notes.trim().ifBlank { null },
                    menUp,
                    womenUp,
                    menDown,
                    womenDown,
                    hasLuggage,
                    selectedDelayText(),
                    otherDelayDesc.trim().ifBlank { null },
                    effectiveStartFix,
                    endFix
                )
                clearActiveDelay()
                resetCaptureForm()
                snackbarText = if (endFix.status == "GPS_PENDING") {
                    "Registro guardado ✅ · GPS pendiente, se completará automáticamente"
                } else {
                    "Registro guardado ✅"
                }
            } catch (e: Exception) {
                snackbarText = e.message ?: "Error al cerrar registro."
            } finally {
                loadingGps = false
            }
        }
    }

    fun saveInlineEvent(summary: AsdDemoSummary) {
        if (isDelayActive) closeActiveDelay(summary) else snackbarText = "Toca un contador o una demora para iniciar el registro."
    }

    fun requestCloseTrip() {
        if (!requestPermsIfNeeded()) {
            snackbarText = "Permiso de ubicación requerido."
            return
        }
        if (isDelayActive) {
            snackbarText = "Cierra o cancela el registro activo antes de cerrar el viaje."
            return
        }
        showCloseTripConfirm = true
    }

    fun closeTripNow() {
        scope.launch {
            try {
                loadingGps = true
                gpsMsg = "Cerrando viaje…"

                val now = System.currentTimeMillis()
                val recentPoint = lastPoint?.takeIf {
                    it.lat != 0.0 &&
                            it.lon != 0.0 &&
                            now - it.timeMs <= 10_000L &&
                            it.accM <= 25.0
                }

                val fix = if (recentPoint != null) {
                    LocationFix(
                        lat = recentPoint.lat,
                        lon = recentPoint.lon,
                        altM = recentPoint.altM,
                        accM = recentPoint.accM,
                        provider = recentPoint.provider,
                        fixTime = recentPoint.timeMs,
                        status = "GPS_LAST_FAST_CLOSE"
                    )
                } else {
                    gpsMsg = "Cerrando viaje… buscando GPS rápido"
                    gps.getBestFixForEvent(
                        targetAccM = 10.0,
                        fallbackAccM = 25.0,
                        timeoutMs = 1_500L,
                        highAccuracy = true
                    )
                }

                val ok = vm.endTripWithFix(tripId, fix)
                stopTrackingService()
                snackbarText = if (ok) {
                    "Viaje cerrado ✅ (${fix.status}) acc=±${fix.accM.toInt()}m"
                } else {
                    "No se pudo cerrar el viaje."
                }
            } catch (e: Exception) {
                snackbarText = e.message ?: "Error al cerrar viaje."
            } finally {
                loadingGps = false
                gpsMsg = null
                showCloseTripConfirm = false
            }
        }
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        uri?.let { scope.launch { snackbarText = runCatching { if (vm.exportLayoutFinal(context, tripId, it)) "CSV final exportado ✅" else "No se pudo exportar CSV." }.getOrElse { e -> e.message ?: "Error exportando CSV." } } }
    }
    val exportClientXlsxLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                snackbarText = runCatching {
                    if (vm.exportClientXlsx(context, tripId, it)) "Excel cliente exportado ✅" else "No se pudo exportar Excel cliente."
                }.getOrElse { e -> e.message ?: "Error exportando Excel cliente." }
            }
        }
    }
    val exportTrackLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        uri?.let { scope.launch { snackbarText = runCatching { if (vm.exportTrackCsv(context, tripId, it)) "TRACK CSV exportado ✅" else "No se pudo exportar TRACK." }.getOrElse { e -> e.message ?: "Error exportando TRACK." } } }
    }
    val exportGpsAuditLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        uri?.let { scope.launch { snackbarText = runCatching { if (vm.exportGpsAuditCsv(context, tripId, it)) "Auditoría GPS exportada ✅" else "No se pudo exportar auditoría GPS." }.getOrElse { e -> e.message ?: "Error exportando auditoría GPS." } } }
    }
    val exportGpxLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri: Uri? ->
        uri?.let { scope.launch { snackbarText = runCatching { if (vm.exportTripGpx(context, tripId, it)) "GPX exportado ✅" else "No se pudo exportar GPX." }.getOrElse { e -> e.message ?: "Error exportando GPX." } } }
    }
    val exportKmlLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.google-earth.kml+xml")) { uri: Uri? ->
        uri?.let { scope.launch { snackbarText = runCatching { if (vm.exportTripKml(context, tripId, it)) "KML exportado ✅" else "No se pudo exportar KML." }.getOrElse { e -> e.message ?: "Error exportando KML." } } }
    }
    val exportGarminTrackLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri: Uri? ->
        uri?.let { scope.launch { snackbarText = runCatching { if (vm.exportGarminTrack(context, tripId, it)) "Track Garmin exportado ✅" else "No se pudo exportar track Garmin." }.getOrElse { e -> e.message ?: "Error exportando track Garmin." } } }
    }
    val exportGarminWaypointsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri: Uri? ->
        uri?.let { scope.launch { snackbarText = runCatching { if (vm.exportGarminWaypoints(context, tripId, it)) "Waypoints Garmin exportados ✅" else "No se pudo exportar waypoints Garmin." }.getOrElse { e -> e.message ?: "Error exportando waypoints Garmin." } } }
    }

    if (showCloseTripConfirm) {
        val totalOnBoard = summary.onBoard
        var confirmTextTyped by remember { mutableStateOf("") }
        val isConfirmed = confirmTextTyped.trim().uppercase() == "FINALIZAR"
        
        AlertDialog(
            onDismissRequest = { showCloseTripConfirm = false },
            title = { Text("CONFIRMAR CIERRE CRÍTICO") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val warningText = when {
                        totalOnBoard > 0 -> "QUEDAN $totalOnBoard PASAJEROS A BORDO QUE SERÁN BAJADOS AUTOMÁTICAMENTE EN AD/FINAL."
                        else -> "ESTA ACCIÓN CERRARÁ DEFINITIVAMENTE EL LEVANTAMIENTO Y GENERARÁ EL EVENTO AD/FINAL."
                    }
                    Text(warningText)
                    Text("No podrá modificarse desde la captura operativa.", fontWeight = FontWeight.Bold, color = Color.Red)
                    Text("Para continuar, escribe la palabra FINALIZAR:", style = MaterialTheme.typography.labelSmall)
                    OutlinedTextField(
                        value = confirmTextTyped,
                        onValueChange = { confirmTextTyped = it.uppercase() },
                        placeholder = { Text("FINALIZAR") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (isConfirmed) greenAcc else Color.Red,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                        )
                    )
                }
            },
            confirmButton = { 
                Button(
                    enabled = isConfirmed,
                    onClick = { triggerHaptic(); closeTripNow() }, 
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) { Text("CERRAR VIAJE") } 
            },
            dismissButton = { OutlinedButton(onClick = { triggerHaptic(); showCloseTripConfirm = false }) { Text("CANCELAR") } }
        )
    }

    val currentTrip = trip
    if (showEditHeader && currentTrip != null) {
        EditTripHeaderDialog(
            trip = currentTrip,
            onDismiss = { showEditHeader = false },
            vm = vm,
            onSave = { routeName, company, vehicleEco, direction, routeNumber, esFs, baseStart, baseEnd, plateNumber, vehicleType, seatCapacity, headerNotes, aforador, supervisor, deviceNumber, observerSex ->
                scope.launch {
                    val ok = vm.updateTripHeader(
                        tripId,
                        routeName,
                        company,
                        vehicleEco,
                        direction,
                        routeNumber,
                        esFs,
                        baseStart,
                        baseEnd,
                        plateNumber,
                        vehicleType,
                        seatCapacity,
                        headerNotes,
                        aforador,
                        supervisor,
                        deviceNumber,
                        observerSex
                    )
                    snackbarText = if (ok) "Encabezado actualizado ✅" else "No se pudo actualizar encabezado."
                    showEditHeader = false
                }
            }
        )
    }

    Scaffold(
        containerColor = bgApp,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgApp, titleContentColor = Color.White, navigationIconContentColor = Color.White),
                title = { 
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = (trip?.routeName ?: "ASD").uppercase(Locale("es", "MX")), 
                            fontWeight = FontWeight.ExtraBold, 
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { 
                                if (!openingMap) {
                                    openingMap = true
                                    triggerHaptic()
                                    onOpenMap(tripId) 
                                }
                            },
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Text("🗺 VER MAPA", color = greenAcc, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                },
                navigationIcon = { 
                    TextButton(onClick = { triggerHaptic(); onBack() }) { 
                        Text("ATRÁS", color = Color.White, fontWeight = FontWeight.Bold) 
                    } 
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { pad ->
        val t = trip
        if (t == null) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val isEnded = t.endTime != null
        val lastAgeMs = lastPoint?.let { System.currentTimeMillis() - it.timeMs } ?: Long.MAX_VALUE
        val trackingAlive = lastPoint != null && lastAgeMs in 0..12_000L
        val activeElapsedSec = if (isDelayActive) ((tickMs - activeDelayStartMs).coerceAtLeast(0L) / 1000L) else 0L
        val captureOnBoard = (summary.onBoard + menUp + womenUp - menDown - womenDown).coerceAtLeast(0)

        val vehicleTypesCatalog by vm.vehicleTypesFlow.collectAsState(initial = emptyList())
        val capacityApplies = if (vehicleTypesCatalog.isNotEmpty()) {
            vehicleTypesCatalog.any { it.name.equals(t.vehicleType, ignoreCase = true) && it.capacityApplies }
        } else {
            t.vehicleType?.uppercase()?.trim() in listOf("COMBI", "VAN", "SPRINTER")
        }
        val exceedsCapacity = capacityApplies && t.seatCapacity != null && captureOnBoard > t.seatCapacity

        val protectedMen = if (t.observerSex == "H") 1 else 0
        val protectedWomen = if (t.observerSex == "M") 1 else 0

        val maxMenDown = (summary.menOnBoard + menUp - protectedMen).coerceAtLeast(0)
        val maxWomenDown = (summary.womenOnBoard + womenUp - protectedWomen).coerceAtLeast(0)

        LaunchedEffect(tripId, isEnded) {
            if (!isEnded) {
                if (gps.hasPermission()) startTrackingService() else snackbarText = "Tip: activa permisos de ubicación para registrar GPS."
            } else {
                stopTrackingService()
            }
        }

        LazyColumn(modifier = Modifier.padding(pad).fillMaxSize().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // 1. CAPTURA ASD
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
                    { clearActiveDelay(); resetCaptureForm(); snackbarText = "Registro cancelado" },
                    { saveInlineEvent(summary) },
                    { 
                        if (!openingMap) {
                            openingMap = true
                            onOpenMap(tripId)
                        }
                    },
                    { requestCloseTrip() },
                    t.observerSex
                )
            }

            // 2. TIEMPO DE LEVANTAMIENTO
            item {
                val start = t.startTime
                val end = t.endTime ?: tickMs
                val durationSec = (end - start).coerceAtLeast(0L) / 1000L
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color(0xFF223A36)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1716))
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("TIEMPO DEL LEVANTAMIENTO", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
                            Text(formatElapsed(durationSec), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        }
                        if (isEnded) {
                            Surface(color = Color(0xFFFF4B4B).copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) {
                                Text("FINALIZADO", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = Color(0xFFFF4B4B), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Surface(color = greenAcc.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) {
                                Text("EN TIEMPO REAL", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = greenAcc, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 3. DATOS DEL LEVANTAMIENTO
            item {
                TripHeaderCard(
                    routeName = t.routeName.uppercase(Locale("es", "MX")),
                    direction = t.direction.uppercase(Locale("es", "MX")),
                    start = fmt.format(Date(t.startTime)),
                    end = t.endTime?.let { fmt.format(Date(it)) } ?: "EN CURSO",
                    vehicleEco = t.vehicleEco?.uppercase(Locale("es", "MX")),
                    plateNumber = t.plateNumber?.uppercase(Locale("es", "MX")),
                    seatCapacity = t.seatCapacity,
                    aforador = t.aforador?.uppercase(Locale("es", "MX")),
                    supervisor = t.supervisor?.uppercase(Locale("es", "MX")),
                    deviceNumber = t.deviceNumber?.uppercase(Locale("es", "MX")),
                    isEnded = isEnded,
                    onEdit = { showEditHeader = true }
                )
            }

            // 4. RASTREO GPS
            item { TrackingStatusCard(trackingAlive, lastAgeMs, lastPoint, pointCount, qualitySummary) }

            // 4.5 RESPALDO EN LA NUBE
            item {
                CloudSyncStatusCard(
                    status = syncStatus,
                    pendingCount = pendingSyncCount,
                    lastSyncTime = lastSyncTimeMs,
                    onSyncNow = { 
                        scope.launch {
                            UrbanRuntime.publishEvent(UrbanEventFactory.asd(UrbanEventTypes.ASD_TRIP_SYNC_REQUESTED, mapOf("tripId" to tripId)))
                            val isOnline = UrbanRuntime.diagnostics(context).isNetworkAvailable
                            if (!isOnline) {
                                snackbarText = "Sin conexión, el respaldo queda pendiente"
                                return@launch
                            }
                            val res = UrbanRuntime.syncNow(context)
                            snackbarText = if (res.isSuccess) "Sincronización finalizada ✅" else "Error al sincronizar con la nube"
                        }
                    }
                )
            }

            // 5. RESUMEN OPERATIVO
            item { DemoSummaryCard(summary) }

            // STATUS TRANSITORIOS (Cierre de viaje)
            gpsMsg?.let { msg ->
                item {
                    Surface(
                        color = Color(0xFF35D36B).copy(alpha = 0.1f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)
                    ) {
                        Text(
                            msg.uppercase(Locale("es", "MX")),
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF35D36B),
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
            if (loadingGps) item { 
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(4.dp),
                    color = Color(0xFF35D36B),
                    trackColor = Color(0xFF35D36B).copy(alpha = 0.2f),
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                ) 
            }

            // 6. EVENTOS REGISTRADOS
            item { 
                Text(
                    "HISTORIAL DE EVENTOS", 
                    style = MaterialTheme.typography.titleMedium, 
                    fontWeight = FontWeight.ExtraBold, 
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                ) 
            }
            if (stops.isEmpty()) {
                item { 
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF101C1A)),
                        border = BorderStroke(1.dp, Color(0xFF223A36))
                    ) { 
                        Text(
                            "AÚN NO HAY EVENTOS. USA EL BLOQUE SUPERIOR PARA REGISTRAR.", 
                            modifier = Modifier.padding(20.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.5f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        ) 
                    } 
                } 
            } else {
                items(stops) { EventCard(it, fmt) }
            }

            // DISTANCIA (Opcional, antes de exportaciones)
            item {
                DistanceCard(distanceKm, distanceLoading, {
                    distanceLoading = true
                    distanceKm = null
                    scope.launch {
                        try {
                            distanceKm = withContext(Dispatchers.IO) { distanceMeters(vm.getTrackPointsOnce(tripId)) / 1000.0 }
                        } catch (e: Exception) {
                            snackbarText = e.message ?: "Error calculando distancia."
                        } finally {
                            distanceLoading = false
                        }
                    }
                }, {
                    distanceLoading = true
                    distanceKm = null
                    scope.launch {
                        try {
                            val toMs = System.currentTimeMillis()
                            val fromMs = toMs - 15 * 60 * 1000L
                            distanceKm = withContext(Dispatchers.IO) { distanceMeters(vm.getTrackPointsBetweenOnce(tripId, fromMs, toMs)) / 1000.0 }
                        } catch (e: Exception) {
                            snackbarText = e.message ?: "Error calculando distancia."
                        } finally {
                            distanceLoading = false
                        }
                    }
                })
            }

            // 7. EXPORTACIONES (Al final)
            item {
                ExportActionsCard(
                    onExportClientXlsx = { exportClientXlsxLauncher.launch("ASD_cliente_${t.tripId}_${fileFmt.format(Date())}.xlsx") },
                    onExportCsv = { exportCsvLauncher.launch("ASD_${t.tripId}_${fileFmt.format(Date())}.csv") },
                    onExportTrack = { exportTrackLauncher.launch("ASD_track_trip_${t.tripId}.csv") },
                    onExportGpsAudit = { exportGpsAuditLauncher.launch("ASD_auditoria_gps_${t.tripId}.csv") },
                    onExportGpx = { exportGpxLauncher.launch("ASD_${t.tripId}_${fileFmt.format(Date())}.gpx") },
                    onExportKml = { exportKmlLauncher.launch("ASD_${t.tripId}_${fileFmt.format(Date())}.kml") },
                    onExportGarminTrack = { exportGarminTrackLauncher.launch("TRACK_${t.direction.uppercase()}_${t.tripId}.gpx") },
                    onExportGarminWaypoints = { exportGarminWaypointsLauncher.launch("WPT_${t.tripId}_${fileFmt.format(Date())}.gpx") }
                )
            }
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
    onCancelDelay: () -> Unit,
    onSave: () -> Unit,
    onOpenMap: () -> Unit,
    onCloseTrip: () -> Unit,
    observerSex: String? = null
) {
    val haptic = LocalHapticFeedback.current
    fun triggerHaptic() = haptic.performHapticFeedback(HapticFeedbackType.LongPress)

    val totalUp = menUp + womenUp
    val totalDown = menDown + womenDown
    val estimatedOnBoard = (currentOnBoard + totalUp - totalDown).coerceAtLeast(0)
    
    val greenAcc = Color(0xFF35D36B)
    val redAcc = Color(0xFFFF4B4B)
    val cardBg = Color(0xFF0D1716)
    val cardBorder = Color(0xFF223A36)
    val internalBg = Color(0xFF101C1A)

    val maleColor = Color(0xFF3B82F6)
    val femaleColor = Color(0xFFEC4899)
    val boardColor = Color(0xFF22C55E)
    val alightColor = Color(0xFFEF4444)

    var showStopField by rememberSaveable { mutableStateOf(false) }
    var showNotesField by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        border = if (isDelayActive) BorderStroke(4.dp, Color(0xFF33E676)) else BorderStroke(1.dp, cardBorder),
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // HEADER ACTION CARD
            val activeGreen = Color(0xFF33E676)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp, max = 86.dp)
                    .then(
                        if (isDelayActive) {
                            Modifier.shadow(
                                elevation = 8.dp,
                                spotColor = activeGreen,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                            )
                        } else Modifier
                    ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = internalBg),
                border = BorderStroke(if (isDelayActive) 4.dp else 1.dp, if (isDelayActive) activeGreen else cardBorder)
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(12.dp).background(if (isDelayActive) greenAcc else Color.Gray, androidx.compose.foundation.shape.CircleShape))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (isDelayActive) "ACTIVO" else "LISTO",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isDelayActive) greenAcc else Color.White
                        )
                        Text(
                            if (isDelayActive) formatElapsed(activeElapsedSec) else "Captura automática",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                    if (isDelayActive) {
                        Button(
                            onClick = { triggerHaptic(); onSave() },
                            enabled = !isEnded,
                            colors = ButtonDefaults.buttonColors(containerColor = greenAcc),
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            modifier = Modifier.height(44.dp),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
                        ) {
                            Text("GUARDAR", fontWeight = FontWeight.ExtraBold, color = Color.Black)
                        }
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = { triggerHaptic(); onCancelDelay() }, modifier = Modifier.size(44.dp)) {
                            Text("✕", color = redAcc, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }

            // METRICS
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ProMetric("SUBEN", totalUp.toString(), Modifier.weight(1f), color = boardColor)
                ProMetric("BAJAN", totalDown.toString(), Modifier.weight(1f), color = alightColor)
                ProMetric("A BORDO", estimatedOnBoard.toString(), Modifier.weight(1f), isError = exceedsCapacity)
            }

            if (exceedsCapacity) {
                Text(
                    "Capacidad excedida: $estimatedOnBoard / ${seatCapacity ?: 0}",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.labelMedium,
                    color = redAcc,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            // ON BOARD COMPACT BANDS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // HOMBRES
                Card(
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = BorderStroke(1.5.dp, maleColor)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(modifier = Modifier.size(20.dp), shape = androidx.compose.foundation.shape.CircleShape, color = maleColor.copy(alpha = 0.2f)) {
                                Box(contentAlignment = Alignment.Center) { Text("H", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = maleColor) }
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("HOMBRES", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Text("${menOnBoard + menUp - menDown}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = maleColor)
                    }
                }

                // MUJERES
                Card(
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = BorderStroke(1.5.dp, femaleColor)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(modifier = Modifier.size(20.dp), shape = androidx.compose.foundation.shape.CircleShape, color = femaleColor.copy(alpha = 0.2f)) {
                                Box(contentAlignment = Alignment.Center) { Text("M", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = femaleColor) }
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("MUJERES", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Text("${womenOnBoard + womenUp - womenDown}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = femaleColor)
                    }
                }
            }

            // SUBEN
            PassengerSection(
                title = "SUBEN",
                icon = "↑",
                total = totalUp,
                menValue = menUp,
                womenValue = womenUp,
                onMenChange = onMenUpChange,
                onWomenChange = onWomenUpChange,
                accentColor = boardColor,
                maxMen = null,
                maxWomen = null,
                maleColor = maleColor,
                femaleColor = femaleColor,
                observerSex = observerSex
            )

            // BAJAN
            PassengerSection(
                title = "BAJAN",
                icon = "↓",
                total = totalDown,
                menValue = menDown,
                womenValue = womenDown,
                onMenChange = onMenDownChange,
                onWomenChange = onWomenDownChange,
                accentColor = alightColor,
                maxMen = maxMenDown,
                maxWomen = maxWomenDown,
                maleColor = maleColor,
                femaleColor = femaleColor,
                observerSex = observerSex
            )

            // SECCION DEMORAS
            Card(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = internalBg),
                border = BorderStroke(1.dp, cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("DEMORAS", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
                    
                    val items = listOf(
                        Triple("🚦", "SEMÁFORO", "S"),
                        Triple("🚗", "CONGESTIÓN", "C"),
                        Triple("🚌", "TRÁFICO MIXTO", "TM"),
                        Triple("🚧", "COND. VIAL", "CND"),
                        Triple("↩", "VUELTA IZQ", "VI"),
                        Triple("↪", "VUELTA DER", "VD"),
                        Triple("🚶", "PEATONAL", "PP"),
                        Triple("⛔", "OTRO", "O")
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items.chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                pair.forEach { (icon, name, code) ->
                                    DelayTile(icon, name, code, selectedDelayCodes.contains(code), onToggle = onToggleDelayCode, modifier = Modifier.weight(1f))
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                    
                    if (selectedDelayCodes.contains("O")) {
                        UpperNextTextField(otherDelayDesc, onOtherDelayDescChange, "DESCRIPCIÓN DE OTRO", singleLine = true)
                    }
                }
            }

            // OBSERVACIONES
            Card(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = internalBg),
                border = BorderStroke(1.dp, cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("OBSERVACIONES", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
                        Switch(
                            checked = hasLuggage,
                            onCheckedChange = { triggerHaptic(); onHasLuggageChange(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = greenAcc),
                            modifier = Modifier.scale(0.8f)
                        )
                        Text("MALETA", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                    }
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = showStopField || stopName.isNotBlank(),
                            onClick = { triggerHaptic(); showStopField = !showStopField },
                            label = { Text("+ PARADA") },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = greenAcc.copy(alpha = 0.2f), selectedLabelColor = greenAcc)
                        )
                        FilterChip(
                            selected = showNotesField || notes.isNotBlank(),
                            onClick = { triggerHaptic(); showNotesField = !showNotesField },
                            label = { Text("+ NOTA") },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = greenAcc.copy(alpha = 0.2f), selectedLabelColor = greenAcc)
                        )
                    }

                    if (showStopField || stopName.isNotBlank()) {
                        UpperNextTextField(stopName, onStopNameChange, "PARADA / REFERENCIA", singleLine = true)
                    }
                    if (showNotesField || notes.isNotBlank()) {
                        UpperNextTextField(notes, onNotesChange, "OBSERVACIONES", singleLine = false)
                    }
                    
                    val now = System.currentTimeMillis()
                    val gpsStatus = lastPoint?.let {
                        val ageSec = (now - it.timeMs) / 1000
                        val isRecent = ageSec <= 10
                        val isAccurate = it.accM <= 25.0

                        when {
                            isRecent && isAccurate -> "🟢 GPS BUENO ±${it.accM.toInt()}m"
                            isRecent -> "🟡 GPS DÉBIL ±${it.accM.toInt()}m"
                            else -> "🔴 SIN ACTUALIZACIÓN / ÚLTIMO FIX HACE ${ageSec}s"
                        }
                    } ?: "🔴 PENDIENTE"

                    Text("GPS $gpsStatus", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
                }
            }

            // BOTTOM BUTTONS
            Row(modifier = Modifier.fillMaxWidth().height(60.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { triggerHaptic(); onOpenMap() },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF101C1A)),
                    border = BorderStroke(1.dp, greenAcc.copy(alpha = 0.5f)),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
                ) {
                    Text("🗺 VER MAPA", color = greenAcc, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { triggerHaptic(); onSave() },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    enabled = !isEnded,
                    colors = ButtonDefaults.buttonColors(containerColor = greenAcc),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
                ) {
                    Text("GUARDAR", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, color = Color.Black)
                }
            }

            if (!isEnded) {
                Button(
                    onClick = { triggerHaptic(); onCloseTrip() },
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF101C1A)),
                    border = BorderStroke(1.dp, Color(0xFFFF4B4B).copy(alpha = 0.5f)),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
                ) {
                    Text("🏁 FINALIZAR LEVANTAMIENTO", color = Color(0xFFFF4B4B), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ProMetric(label: String, value: String, modifier: Modifier = Modifier, isError: Boolean = false, color: Color? = null) {
    val displayColor = if (isError) Color(0xFFFF4B4B) else color ?: Color.White
    Surface(
        modifier = modifier,
        color = Color(0xFF101C1A),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (isError) Color(0xFFFF4B4B) else Color(0xFF223A36))
    ) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = displayColor)
            Text(label, style = MaterialTheme.typography.labelSmall, color = displayColor.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PassengerSection(
    title: String,
    icon: String,
    total: Int,
    menValue: Int,
    womenValue: Int,
    onMenChange: (Int) -> Unit,
    onWomenChange: (Int) -> Unit,
    accentColor: Color,
    maxMen: Int? = null,
    maxWomen: Int? = null,
    maleColor: Color,
    femaleColor: Color,
    observerSex: String? = null
) {
    Card(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101C1A)),
        border = BorderStroke(3.dp, accentColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$icon $title", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = accentColor)
                Spacer(Modifier.weight(1f))
                Text("TOTAL: $total", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = accentColor)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PassengerCounterCard(
                    label = "HOMBRES",
                    value = menValue,
                    onValueChange = onMenChange,
                    modifier = Modifier.weight(1f),
                    accentColor = maleColor,
                    maxValue = maxMen,
                    isWoman = false,
                    isObserver = observerSex == "H"
                )
                PassengerCounterCard(
                    label = "MUJERES",
                    value = womenValue,
                    onValueChange = onWomenChange,
                    modifier = Modifier.weight(1f),
                    accentColor = femaleColor,
                    maxValue = maxWomen,
                    isWoman = true,
                    isObserver = observerSex == "M"
                )
            }
        }
    }
}

@Composable
private fun PassengerCounterCard(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color,
    maxValue: Int? = null,
    isWoman: Boolean,
    isObserver: Boolean = false
) {
    val isAlert = maxValue != null && maxValue <= 0
    val redAcc = Color(0xFFFF4B4B)
    val alertCol = Color(0xFFFF5252)
    val cardBg = if (isAlert) Color(0xFF1A0F0F) else Color(0xFF101C1A)
    val displayValColor = if (isAlert) redAcc else accentColor

    Surface(
        modifier = modifier.heightIn(min = 150.dp, max = 170.dp),
        color = cardBg,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        border = BorderStroke(if (isAlert) 1.5.dp else 1.5.dp, if (isAlert) alertCol else accentColor)
    ) {
        Column(
            Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Surface(modifier = Modifier.size(18.dp), shape = androidx.compose.foundation.shape.CircleShape, color = accentColor.copy(alpha = 0.2f)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(if (isWoman) "M" else "H", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = accentColor)
                    }
                }
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = accentColor)
            }
            
            Text(value.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = displayValColor)
            
            if (isAlert) {
                val alertMsg = if (isObserver) {
                    if (isWoman) "Solo observadora a bordo" else "Solo observador a bordo"
                } else {
                    if (isWoman) "Sin pasajeras disponibles" else "Sin pasajeros disponibles"
                }
                Text(alertMsg, style = MaterialTheme.typography.labelSmall, color = redAcc, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                ProCounterButton("-1", { onValueChange(value - 1) }, value > 0 && !isAlert, isRed = true, modifier = Modifier.weight(1f))
                ProCounterButton("+1", { onValueChange(value + 1) }, maxValue == null || value < maxValue, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ProCounterButton(text: String, onClick: () -> Unit, enabled: Boolean, isRed: Boolean = false, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    val borderCol = if (isRed) Color(0xFFFF4B4B) else if (enabled) Color(0xFF1E8E4D) else Color(0xFF1E8E4D).copy(alpha = 0.15f)
    val textCol = if (isRed) Color(0xFFFF6B6B) else if (enabled) Color(0xFF4DFF91) else Color(0xFF4DFF91).copy(alpha = 0.2f)
    
    OutlinedButton(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        enabled = enabled,
        modifier = modifier.height(48.dp),
        contentPadding = PaddingValues(0.dp),
        border = BorderStroke(1.dp, borderCol),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, style = androidx.compose.ui.text.TextStyle(fontSize = 14.sp), fontWeight = FontWeight.Bold, color = textCol)
        }
    }
}

@Composable
private fun DelayTile(
    icon: String,
    label: String,
    code: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onToggle: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val bg = if (isSelected) Color(0xFF2A2112) else Color(0xFF101C1A)
    val borderCol = if (isSelected) Color(0xFFFF9800) else Color(0xFF203B37)
    val contentColor = if (isSelected) Color(0xFFFFB74D) else Color.White
    
    Surface(
        onClick = { 
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onToggle(code) 
        },
        color = bg,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, borderCol),
        modifier = modifier.height(76.dp)
    ) {
        Column(Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Text(icon, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(6.dp))
                Text(label, style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp), fontWeight = FontWeight.Bold, color = contentColor, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            Text(code, style = MaterialTheme.typography.labelSmall, color = contentColor.copy(alpha = 0.5f))
        }
    }
}
@Composable
private fun UpperNextTextField(value: String, onValueChange: (String) -> Unit, label: String, singleLine: Boolean = true) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = singleLine,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color(0xFF07110F),
            unfocusedContainerColor = Color(0xFF07110F),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedBorderColor = Color(0xFF35D36B),
            unfocusedBorderColor = Color(0xFF1E3834)
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditTripHeaderDialog(
    trip: Trip,
    onDismiss: () -> Unit,
    vm: AsdTripDetailVM,
    onSave: (
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
        notes: String?,
        aforador: String?,
        supervisor: String?,
        deviceNumber: String?,
        observerSex: String?
    ) -> Unit
) {
    val focusManager = LocalFocusManager.current
    fun upper(v: String) = v.uppercase(Locale("es", "MX"))
    var planningRouteId by rememberSaveable(trip.tripId) { mutableStateOf(trip.planningRouteId) }
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
    var aforador by rememberSaveable(trip.tripId) { mutableStateOf(trip.aforador?.uppercase(Locale("es", "MX")) ?: "") }
    var supervisor by rememberSaveable(trip.tripId) { mutableStateOf(trip.supervisor?.uppercase(Locale("es", "MX")) ?: "") }
    var deviceNumber by rememberSaveable(trip.tripId) { mutableStateOf(trip.deviceNumber?.uppercase(Locale("es", "MX")) ?: "") }
    var observerSex by rememberSaveable(trip.tripId) { mutableStateOf(trip.observerSex ?: "") }
    var headerNotes by rememberSaveable(trip.tripId) { mutableStateOf(trip.notes?.uppercase(Locale("es", "MX")) ?: "") }

    val observers by vm.observersFlow("OBSERVADOR").collectAsState(initial = emptyList())
    val supervisors by vm.observersFlow("SUPERVISOR").collectAsState(initial = emptyList())

    @Composable
    fun NextField(label: String, value: String, change: (String) -> Unit, number: Boolean = false) {
        OutlinedTextField(
            value = value,
            onValueChange = { change(if (number) it.filter { ch -> ch.isDigit() }.take(6) else upper(it)) },
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
            Column {
                Text(
                    "Esta sección permite corregir únicamente la información visible del encabezado del reporte. Los eventos capturados, ubicación GPS, tiempos y registros operativos no se modifican. Uso recomendado solo para supervisión o corrección administrativa.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Yellow.copy(alpha = 0.8f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                Box(modifier = Modifier.heightIn(max = 400.dp)) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        item { NextField(label = "ID CATÁLOGO / PLANEACIÓN", value = planningRouteId, change = { planningRouteId = it }) }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.weight(1f)) { NextField(label = "SENTIDO", value = direction, change = { direction = it }) }
                                Box(Modifier.weight(1f)) { NextField(label = "ES / FS", value = esFs, change = { esFs = it }) }
                            }
                        }
                        item { NextField(label = "RUTA / DERROTERO", value = routeName, change = { routeName = it }) }
                        item { NextField(label = "EMPRESA", value = company, change = { company = it }) }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.weight(1f)) { NextField(label = "BASE INICIO", value = baseStart, change = { baseStart = it }) }
                                Box(Modifier.weight(1f)) { NextField(label = "BASE FINAL", value = baseEnd, change = { baseEnd = it }) }
                            }
                        }
                        
                        item {
                            Text("PERSONAL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF35D36B))
                        }
                        item {
                            var obsExpanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(expanded = obsExpanded, onExpandedChange = { obsExpanded = it }) {
                                OutlinedTextField(
                                    value = aforador,
                                    onValueChange = { aforador = it },
                                    readOnly = observers.isNotEmpty(),
                                    label = { Text("OBSERVADOR") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = obsExpanded) },
                                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                                )
                                if (observers.isNotEmpty()) {
                                    ExposedDropdownMenu(expanded = obsExpanded, onDismissRequest = { obsExpanded = false }) {
                                        observers.forEach { person ->
                                            DropdownMenuItem(
                                                text = { Text(person.name) },
                                                onClick = {
                                                    aforador = person.name
                                                    person.defaultSex?.let { observerSex = it }
                                                    obsExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            Text("SEXO DEL OBSERVADOR", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = observerSex == "H",
                                    onClick = { observerSex = "H" },
                                    label = { Text("HOMBRE") },
                                    leadingIcon = if (observerSex == "H") { { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) } } else null
                                )
                                FilterChip(
                                    selected = observerSex == "M",
                                    onClick = { observerSex = "M" },
                                    label = { Text("MUJER") },
                                    leadingIcon = if (observerSex == "M") { { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) } } else null
                                )
                            }
                        }
                        item {
                            var supExpanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(expanded = supExpanded, onExpandedChange = { supExpanded = it }) {
                                OutlinedTextField(
                                    value = supervisor,
                                    onValueChange = { supervisor = it },
                                    readOnly = supervisors.isNotEmpty(),
                                    label = { Text("SUPERVISOR") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = supExpanded) },
                                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                                )
                                if (supervisors.isNotEmpty()) {
                                    ExposedDropdownMenu(expanded = supExpanded, onDismissRequest = { supExpanded = false }) {
                                        supervisors.forEach { person ->
                                            DropdownMenuItem(
                                                text = { Text(person.name) },
                                                onClick = {
                                                    supervisor = person.name
                                                    supExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        item { NextField(label = "NO. DISPOSITIVO", value = deviceNumber, change = { deviceNumber = it }) }

                        item {
                            Text("UNIDAD Y OPERACIÓN", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF35D36B))
                        }
                        item { NextField(label = "TIPO VEHÍCULO", value = vehicleType, change = { vehicleType = it }) }
                        item { NextField(label = "CAPACIDAD", value = seatCapacity, change = { seatCapacity = it }, number = true) }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.weight(1f)) { NextField(label = "ECO", value = vehicleEco, change = { vehicleEco = it }) }
                                Box(Modifier.weight(1f)) { NextField(label = "PLACA", value = plateNumber, change = { plateNumber = it }) }
                            }
                        }
                        item { NextField(label = "FOLIO / NO. ECONÓMICO", value = routeNumber, change = { routeNumber = it }, number = true) }
                        item { NextField(label = "OBSERVACIONES", value = headerNotes, change = { headerNotes = it }) }
                    }
                }
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
                    headerNotes.ifBlank { null },
                    aforador.ifBlank { null },
                    supervisor.ifBlank { null },
                    deviceNumber.ifBlank { null },
                    observerSex.ifBlank { null }
                )
            }) { Text("GUARDAR") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("CANCELAR") } }
    )
}

private fun formatElapsed(totalSec: Long): String {
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

@Composable
private fun TripHeaderCard(
    routeName: String,
    direction: String,
    start: String,
    end: String,
    vehicleEco: String?,
    plateNumber: String?,
    seatCapacity: Int?,
    aforador: String?,
    supervisor: String?,
    deviceNumber: String?,
    isEnded: Boolean,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0xFF223A36)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1716))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(routeName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Surface(
                        color = if (isEnded) Color(0xFFFF4B4B).copy(alpha = 0.2f) else Color(0xFF35D36B).copy(alpha = 0.2f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            if (isEnded) "LEVANTAMIENTO CERRADO" else "LEVANTAMIENTO ACTIVO",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isEnded) Color(0xFFFF4B4B) else Color(0xFF35D36B)
                        )
                    }
                }
                OutlinedButton(
                    onClick = onEdit,
                    enabled = !isEnded,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    border = BorderStroke(1.dp, Color(0xFF1E8E4D)),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ) {
                    Text("EDITAR", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4DFF91))
                }
            }

            Text("SENTIDO: $direction", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color.White)
            
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    LabelValue("UNIDAD", "ECO ${vehicleEco ?: "-"} • ${plateNumber ?: "-"}")
                    LabelValue("CAPACIDAD", seatCapacity?.toString() ?: "-")
                }
                Column(Modifier.weight(1f)) {
                    LabelValue("INICIO", start)
                    LabelValue("FIN", end)
                }
            }

            HorizontalDivider(color = Color(0xFF223A36))

            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("OPERADOR: ${aforador ?: "-"}", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
                Text("SUPERVISOR: ${supervisor ?: "-"}", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
                Text("ID DEL EQUIPO: ${deviceNumber ?: "-"}", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
private fun LabelValue(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f), fontWeight = FontWeight.Bold)
        Text(value, style = MaterialTheme.typography.bodySmall, color = Color.White)
    }
}

@Composable
private fun CloudSyncStatusCard(
    status: AsdTripSyncStatus,
    pendingCount: Int,
    lastSyncTime: Long?,
    onSyncNow: () -> Unit
) {
    val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale("es", "MX")) }
    val lastSyncText = lastSyncTime?.let { fmt.format(Date(it)) } ?: "Nunca"

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0xFF223A36)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101C1A))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("RESPALDO EN LA NUBE", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
                SyncStatusChip(status)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Pendientes", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f))
                    Text(pendingCount.toString(), style = MaterialTheme.typography.bodyLarge, color = if (pendingCount > 0) Color(0xFFFFB300) else Color.White, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Último envío", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f))
                    Text(lastSyncText, style = MaterialTheme.typography.bodyLarge, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            Button(
                onClick = onSyncNow,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF35D36B)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("SINCRONIZAR AHORA", color = Color.Black, fontWeight = FontWeight.ExtraBold)
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

@Composable
private fun DemoSummaryCard(summary: AsdDemoSummary) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0xFF223A36)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1716))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("RESUMEN DEL LEVANTAMIENTO", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SummaryMetricBox("EVENTOS", summary.events.toString(), Modifier.weight(1f))
                SummaryMetricBox("ASCENSOS", summary.boardings.toString(), Modifier.weight(1f), valColor = Color(0xFF35D36B))
                SummaryMetricBox("DESCENSOS", summary.alightings.toString(), Modifier.weight(1f), valColor = Color(0xFFFF4B4B))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SummaryMetricBox("A BORDO", summary.onBoard.toString(), Modifier.weight(1f), valColor = Color.White)
                SummaryMetricBox("H / M", "${summary.menOnBoard}/${summary.womenOnBoard}", Modifier.weight(1f))
                SummaryMetricBox("GPS", summary.trackPoints.toString(), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SummaryMetricBox(label: String, value: String, modifier: Modifier = Modifier, valColor: Color? = null) {
    Surface(
        modifier = modifier,
        color = Color(0xFF101C1A),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFF1E3834))
    ) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = valColor ?: Color.White)
            Text(label, style = MaterialTheme.typography.labelSmall, color = (valColor ?: Color.White).copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TrackingStatusCard(
    trackingAlive: Boolean,
    lastAgeMs: Long,
    lastPoint: TrackPoint?,
    pointCount: Int,
    qualitySummary: String
) {
    val ageSec = if (lastAgeMs == Long.MAX_VALUE) null else (lastAgeMs / 1000)
    val ageText = ageSec?.let { "${it}s" } ?: "-"

    val (statusText, statusColor, score) = when {
        lastPoint == null -> Triple("⚫ GPS PENDIENTE", Color.Gray, 0)
        lastPoint.accM <= 10.0 -> {
            if (lastPoint.accM <= 5.0) Triple("🟢 GPS EXCELENTE", Color(0xFF35D36B), 100)
            else Triple("🟢 GPS EXCELENTE", Color(0xFF35D36B), 90)
        }
        lastPoint.accM <= 25.0 -> Triple("🟢 GPS BUENO", Color(0xFF35D36B).copy(alpha = 0.8f), 75)
        lastPoint.accM <= 45.0 -> Triple("🟡 GPS USABLE", Color(0xFFFF9800), 55)
        else -> Triple("🔴 GPS DÉBIL", Color(0xFFFF4B4B), 25)
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0xFF223A36)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1716))
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("RASTREO GPS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.weight(1f))
                if (trackingAlive) {
                    Surface(color = Color(0xFF35D36B).copy(alpha = 0.1f), shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)) {
                        Text("EN VIVO", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Color(0xFF35D36B), fontWeight = FontWeight.Bold)
                    }
                }
            }
            
            Text(
                statusText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = statusColor
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LinearProgressIndicator(
                    progress = { score / 100f },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = statusColor,
                    trackColor = statusColor.copy(alpha = 0.2f),
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                Text(
                    "CONFIANZA GPS: $score%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
            }

            HorizontalDivider(color = Color(0xFF223A36))

            Row(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    StatusRow("Precisión", lastPoint?.let { "±${it.accM.toInt()}m" } ?: "-")
                    StatusRow("Edad", ageText)
                    StatusRow("Puntos", pointCount.toString())
                }
            }

            HorizontalDivider(color = Color(0xFF223A36))
            
            Text("CALIDAD DEL LEVANTAMIENTO", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.4f))
            Text(qualitySummary, style = MaterialTheme.typography.bodySmall, color = Color.White)
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row {
        Text("$label: ", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.4f))
        Text(value, style = MaterialTheme.typography.bodySmall, color = Color.White)
    }
}

@Composable
private fun GpsChip(text: String) {
    Surface(
        color = Color(0xFF101C1A),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, Color(0xFF1E3834))
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
    }
}

@Composable
private fun DistanceCard(distanceKm: Double?, distanceLoading: Boolean, onCalculateAll: () -> Unit, onCalculateRecent: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0xFF223A36)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1716))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("DISTANCIA DEL LEVANTAMIENTO", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
            
            Surface(
                color = Color(0xFF101C1A),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFF1E3834)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                    if (distanceLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color(0xFF35D36B))
                    } else {
                        Text(
                            distanceKm?.let { "%.2f KM".format(it) } ?: "—",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF35D36B)
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    enabled = !distanceLoading,
                    onClick = onCalculateRecent,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, Color(0xFF223A36)),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
                ) {
                    Text("15 MIN", color = Color.White)
                }
                OutlinedButton(
                    enabled = !distanceLoading,
                    onClick = onCalculateAll,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, Color(0xFF223A36)),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
                ) {
                    Text("TODO", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ExportActionsCard(
    onExportClientXlsx: () -> Unit,
    onExportCsv: () -> Unit,
    onExportTrack: () -> Unit,
    onExportGpsAudit: () -> Unit,
    onExportGpx: () -> Unit,
    onExportKml: () -> Unit,
    onExportGarminTrack: () -> Unit,
    onExportGarminWaypoints: () -> Unit
){
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0xFF223A36)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1716))
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("EXPORTACIONES", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
            
            ExportGroup("ENTREGABLE CLIENTE") {
                Button(onClick = onExportClientXlsx, modifier = Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF35D36B))) {
                    Text("EXCEL CLIENTE", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
            
            ExportGroup("AUDITORÍA INTERNA") {
                OutlinedButton(onClick = onExportGpsAudit, modifier = Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Color(0xFF223A36))) { Text("AUDITORÍA GPS", color = Color.White) }
                OutlinedButton(onClick = onExportTrack, modifier = Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Color(0xFF223A36))) { Text("TRACK CSV", color = Color.White) }
            }
            
            ExportGroup("GEO") {
                OutlinedButton(onClick = onExportKml, modifier = Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Color(0xFF223A36))) { Text("KML", color = Color.White) }
                OutlinedButton(onClick = onExportGpx, modifier = Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Color(0xFF223A36))) { Text("GPX AUDITORÍA (URBAN)", color = Color.White) }
            }

            ExportGroup("GARMIN (eTrex 20x)") {
                OutlinedButton(
                    onClick = onExportGarminTrack,
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF223A36))
                ) {
                    Text("GPX TRACK (GARMIN)", color = Color.White)
                }
                OutlinedButton(
                    onClick = onExportGarminWaypoints,
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF223A36))
                ) {
                    Text("GPX WAYPOINTS (GARMIN)", color = Color.White)
                }
            }
            
            ExportGroup("LEGADO") {
                OutlinedButton(onClick = onExportCsv, modifier = Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Color(0xFF223A36))) { Text("CSV FINAL", color = Color.White) }
            }
        }
    }
}

@Composable
private fun ExportGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.4f))
        content()
        HorizontalDivider(modifier = Modifier.padding(top = 4.dp), color = Color(0xFF223A36))
    }
}

@Composable
private fun EventCard(event: StopEvent, fmt: SimpleDateFormat) {
    val up = event.paxMenUp + event.paxWomenUp
    val down = event.paxMenDown + event.paxWomenDown
    val hasPax = up > 0 || down > 0
    val codes = event.delayCodes.orEmpty().uppercase(Locale.ROOT)
    
    val isInicio = codes.contains("AD/INICIO")
    val isFinal = codes.contains("AD/FINAL")
    val hasDelay = !event.delayCodes.isNullOrBlank() && !isInicio && !isFinal
    
    val friendlyType = when {
        isInicio -> "INICIO"
        isFinal -> "FINAL"
        hasPax && hasDelay -> "ASD + DEMORA"
        hasDelay -> "DEMORA"
        hasPax -> "ASD"
        else -> "REGISTRO"
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, if (isInicio || isFinal) Color(0xFF35D36B).copy(alpha = 0.5f) else Color(0xFF223A36)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1716))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "WP ${event.waypointStopId} → ${event.waypointStartId}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Surface(
                    color = when(friendlyType) {
                        "INICIO" -> Color(0xFF35D36B).copy(alpha = 0.2f)
                        "FINAL" -> Color(0xFFFF4B4B).copy(alpha = 0.2f)
                        else -> Color(0xFF42A5F5).copy(alpha = 0.2f)
                    },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                ) {
                    Text(
                        friendlyType,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = when(friendlyType) {
                            "INICIO" -> Color(0xFF35D36B)
                            "FINAL" -> Color(0xFFFF4B4B)
                            else -> Color(0xFF42A5F5)
                        }
                    )
                }
            }
            
            Text(fmt.format(Date(event.timestamp)), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.5f))

            if (!event.stopName.isNullOrBlank()) {
                Surface(
                    color = Color(0xFF101C1A),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, Color(0xFF1E3834))
                ) {
                    Text(
                        "PARADA: ${event.stopName}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column {
                    Text("SUBEN: $up", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF35D36B))
                    Text("H:${event.paxMenUp} M:${event.paxWomenUp}", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f))
                }
                Column {
                    Text("BAJAN: $down", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFFFF4B4B))
                    Text("H:${event.paxMenDown} M:${event.paxWomenDown}", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f))
                }
            }

            if (hasDelay) {
                Text("DEMORAS: ${event.delayCodes}", style = MaterialTheme.typography.bodySmall, color = Color(0xFFFF9800), fontWeight = FontWeight.Bold)
            }

            if (event.hasLuggage) {
                Text("🧳 MALETA / BULTO", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color.White)
            }

            if (!event.notes.isNullOrBlank()) {
                HorizontalDivider(color = Color(0xFF223A36))
                Text("Observaciones:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.4f))
                Text(event.notes, style = MaterialTheme.typography.bodySmall, color = Color.White)
            }

            val gpsText = if (event.stopLat != 0.0 || event.stopLon != 0.0) {
                "±${event.stopAccM.toInt()}m"
            } else {
                "PENDIENTE"
            }
            Text("GPS: $gpsText", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f))
        }
    }
}

private fun buildGpsQualitySummary(points: List<TrackPoint>): String {
    if (points.isEmpty()) return "-"

    val qualities = points.map { point ->
        val parsed = com.oropeza.urbanapp.asd.location.GpsProviderDiagnostics.parse(point.provider)
        parsed.quality.ifBlank {
            when {
                point.accM <= 10.0 -> "EXCELLENT"
                point.accM <= 25.0 -> "GOOD"
                point.accM <= 45.0 -> "USABLE"
                else -> "POOR"
            }
        }
    }

    fun pct(label: String): Int {
        val count = qualities.count { it == label }
        return ((count.toDouble() / qualities.size.toDouble()) * 100.0).toInt()
    }

    return "EXCELLENT ${pct("EXCELLENT")}% / GOOD ${pct("GOOD")}% / USABLE ${pct("USABLE")}% / POOR ${pct("POOR")}%"
}

private data class AsdDemoSummary(val events: Int, val boardings: Int, val alightings: Int, val delays: Int, val onBoard: Int, val menOnBoard: Int, val womenOnBoard: Int, val trackPoints: Int) { companion object { fun from(stops: List<StopEvent>, pointCount: Int): AsdDemoSummary { var boardings = 0; var alightings = 0; var delays = 0; var onBoard = 0; var menOnBoard = 0; var womenOnBoard = 0; stops.sortedBy { it.timestamp }.forEach { event -> val up = event.paxMenUp + event.paxWomenUp; val down = event.paxMenDown + event.paxWomenDown; val type = event.stopType.uppercase(Locale("es", "MX")); if (!event.delayCodes.isNullOrBlank() || type in setOf("DEMORA", "BANDERA", "DELAY")) delays += 1; boardings += up; alightings += down; onBoard = (onBoard + up - down).coerceAtLeast(0); menOnBoard = (menOnBoard + event.paxMenUp - event.paxMenDown).coerceAtLeast(0); womenOnBoard = (womenOnBoard + event.paxWomenUp - event.paxWomenDown).coerceAtLeast(0) }; return AsdDemoSummary(stops.size, boardings, alightings, delays, onBoard, menOnBoard, womenOnBoard, pointCount) } } }
private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double { val r = 6_371_000.0; val dLat = Math.toRadians(lat2 - lat1); val dLon = Math.toRadians(lon2 - lon1); val a = sin(dLat / 2).pow(2.0) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0); val c = 2 * atan2(sqrt(a), sqrt(1 - a)); return r * c }
private fun distanceMeters(points: List<TrackPoint>): Double { if (points.size < 2) return 0.0; val raw = points.map { LatLng(it.lat, it.lon) }; val smooth = PolylineSmoother.movingAverage(raw, window = 3); val simplified = PolylineSmoother.douglasPeucker(smooth, epsilonMeters = 4.0); var total = 0.0; for (i in 1 until simplified.size) total += haversineMeters(simplified[i - 1].lat, simplified[i - 1].lon, simplified[i].lat, simplified[i].lon); return total }
