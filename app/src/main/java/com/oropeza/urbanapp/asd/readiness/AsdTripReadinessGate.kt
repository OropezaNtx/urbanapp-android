package com.oropeza.urbanapp.asd.readiness

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.oropeza.urbanapp.asd.AsdGraph
import com.oropeza.urbanapp.asd.location.LocationProvider
import com.oropeza.urbanapp.asd.location.TrackingService
import com.oropeza.urbanapp.asd.ui.viewmodel.AsdTripDetailScreen
import kotlinx.coroutines.launch

private const val TAG = "FieldReadiness"

@Composable
fun AsdTripReadinessGate(
    tripId: Long,
    onBack: () -> Unit,
    onOpenMap: (Long) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val trip by AsdGraph.repo.tripFlow(tripId).collectAsState(initial = null)
    val pendingSync by AsdGraph.repo.syncQueuePendingCountFlow().collectAsState(initial = 0)
    val trackingMetrics by TrackingService.trackingMetrics.collectAsState()
    val gps = remember { LocationProvider(context) }
    var accepted by rememberSaveable(tripId) { mutableStateOf(false) }
    var gpsCheck by remember(tripId) { mutableStateOf<ReadinessCheck?>(null) }
    var gpsProbeStarted by remember(tripId) { mutableStateOf(false) }
    var report by remember(tripId) { mutableStateOf(FieldReadiness.evaluate(context)) }

    val trackingThisTrip = trackingMetrics.active && trackingMetrics.tripId == tripId

    fun missingIdentityFields(): List<String> = buildList {
        if (trip?.routeName.isNullOrBlank()) add("ruta")
        if (trip?.aforador.isNullOrBlank()) add("operador")
        if (trip?.deviceNumber.isNullOrBlank()) add("equipo")
        if (trip?.direction.isNullOrBlank()) add("sentido")
    }

    fun composeReport(): FieldReadinessReport {
        val base = FieldReadiness.evaluate(context)
        val syncCheck = if (pendingSync >= 25) {
            ReadinessCheck("SYNC_BACKLOG", "Pendientes de sincronización", ReadinessSeverity.WARNING, "Existe un backlog importante de datos locales. Afora puede continuar offline y lo reportará a supervisión.", pendingSync.toString())
        } else {
            ReadinessCheck("SYNC_BACKLOG", "Pendientes de sincronización", ReadinessSeverity.PASS, if (pendingSync == 0) "No hay deuda de sincronización pendiente." else "La cola local tiene pocos elementos y puede continuar operando normalmente.", pendingSync.toString())
        }
        val missing = missingIdentityFields()
        val identityCheck = if (missing.isEmpty()) {
            ReadinessCheck("TRIP_IDENTITY", "Identidad del levantamiento", ReadinessSeverity.PASS, "Ruta, operador, equipo y sentido están identificados.", "Completa")
        } else {
            ReadinessCheck("TRIP_IDENTITY", "Identidad del levantamiento", ReadinessSeverity.WARNING, "Faltan datos de identificación: ${missing.joinToString(", ")}. Se conserva como evidencia para supervisión.", "${missing.size} faltante(s)")
        }
        val gpsResult = gpsCheck ?: ReadinessCheck("GPS_FIX", "Señal GPS inicial", ReadinessSeverity.WARNING, "La verificación del fix inicial continúa en segundo plano.", "Pendiente")
        return base.copy(checks = base.checks + listOf(gpsResult, syncCheck, identityCheck))
    }

    fun persistAndLog(current: FieldReadinessReport) {
        FieldReadiness.persistEvidence(context, tripId, current)
        Log.i(TAG, "PREFLIGHT_EVALUATED trip=$tripId version=${FieldReadiness.VERSION} state=${current.state} blockers=${current.blockers.size} warnings=${current.warnings.size} passed=${current.passed.size}")
        current.checks.forEach { check -> Log.i(TAG, "PREFLIGHT_CHECK trip=$tripId id=${check.id} severity=${check.severity} value=${check.value ?: "NONE"}") }
        val missing = missingIdentityFields()
        if (missing.isNotEmpty()) Log.w(TAG, "PREFLIGHT_IDENTITY_MISSING trip=$tripId fields=${missing.joinToString(",")}")
    }

    fun probeGpsSilently() {
        if (gpsProbeStarted) return
        gpsProbeStarted = true
        scope.launch {
            val fix = runCatching { gps.getBestFixForStartTrip(timeoutMs = 4_000L) }.getOrNull()
            gpsCheck = when {
                fix == null || fix.lat == 0.0 || fix.lon == 0.0 || fix.status == "NO_FIX" -> ReadinessCheck("GPS_FIX", "Señal GPS inicial", ReadinessSeverity.WARNING, "No se obtuvo un fix GPS utilizable durante el preflight silencioso.", "Sin fix")
                fix.accM <= 25.0 -> ReadinessCheck("GPS_FIX", "Señal GPS inicial", ReadinessSeverity.PASS, "Se obtuvo un fix GPS utilizable al inicio.", "±${fix.accM.toInt()} m")
                else -> ReadinessCheck("GPS_FIX", "Señal GPS inicial", ReadinessSeverity.WARNING, "El GPS respondió con precisión inicial reducida. Se conserva para análisis de calidad.", "±${fix.accM.toInt()} m")
            }
            report = composeReport()
            persistAndLog(report)
            Log.i(TAG, "PREFLIGHT_GPS_EVIDENCE trip=$tripId severity=${gpsCheck?.severity} value=${gpsCheck?.value}")
        }
    }

    fun evaluateAndContinue() {
        report = composeReport()
        persistAndLog(report)

        if (report.canStart) {
            if (!accepted) {
                accepted = true
                Log.i(TAG, "PREFLIGHT_SILENT_ACCEPTED trip=$tripId state=${report.state} warnings=${report.warnings.size}")
            }
            probeGpsSilently()
        } else {
            Log.w(TAG, "PREFLIGHT_BLOCKED trip=$tripId blockers=${report.blockers.joinToString(",") { it.id }}")
        }
    }

    LaunchedEffect(tripId, trip, trackingThisTrip) {
        if (trip != null && trip?.endTime == null && !trackingThisTrip && !accepted) {
            evaluateAndContinue()
        }
    }

    when {
        trip == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        trip?.endTime != null -> AsdTripDetailScreen(tripId = tripId, onBack = onBack, onOpenMap = onOpenMap)
        trackingThisTrip -> AsdTripDetailScreen(tripId = tripId, onBack = onBack, onOpenMap = onOpenMap)
        accepted -> AsdTripDetailScreen(tripId = tripId, onBack = onBack, onOpenMap = onOpenMap)
        else -> FieldReadinessDialog(
            report = report.copy(checks = report.blockers),
            onRefresh = { evaluateAndContinue() },
            onStart = { evaluateAndContinue() },
            onDismiss = {
                Log.i(TAG, "PREFLIGHT_CANCELLED trip=$tripId")
                onBack()
            },
        )
    }
}
