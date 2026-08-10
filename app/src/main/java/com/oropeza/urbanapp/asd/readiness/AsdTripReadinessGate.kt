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
    val pointCount by AsdGraph.repo.trackCountFlow(tripId).collectAsState(initial = 0)
    val pendingSync by AsdGraph.repo.syncQueuePendingCountFlow().collectAsState(initial = 0)
    val gps = remember { LocationProvider(context) }
    var accepted by rememberSaveable(tripId) { mutableStateOf(false) }
    var gpsCheck by remember(tripId) { mutableStateOf<ReadinessCheck?>(null) }
    var report by remember(tripId) { mutableStateOf(FieldReadiness.evaluate(context)) }

    fun composeReport(): FieldReadinessReport {
        val base = FieldReadiness.evaluate(context)
        val syncCheck = if (pendingSync >= 25) {
            ReadinessCheck("SYNC_BACKLOG", "Pendientes de sincronización", ReadinessSeverity.WARNING, "Existe un backlog importante de datos locales. Puedes continuar offline, pero conviene sincronizar antes de acumular otra jornada.", pendingSync.toString())
        } else {
            ReadinessCheck("SYNC_BACKLOG", "Pendientes de sincronización", ReadinessSeverity.PASS, if (pendingSync == 0) "No hay deuda de sincronización pendiente." else "La cola local tiene pocos elementos y puede continuar operando normalmente.", pendingSync.toString())
        }
        val missing = buildList {
            if (trip?.routeName.isNullOrBlank()) add("ruta")
            if (trip?.aforador.isNullOrBlank()) add("operador")
            if (trip?.deviceNumber.isNullOrBlank()) add("equipo")
            if (trip?.direction.isNullOrBlank()) add("sentido")
        }
        val identityCheck = if (missing.isEmpty()) {
            ReadinessCheck("TRIP_IDENTITY", "Identidad del levantamiento", ReadinessSeverity.PASS, "Ruta, operador, equipo y sentido están identificados.", "Completa")
        } else {
            ReadinessCheck("TRIP_IDENTITY", "Identidad del levantamiento", ReadinessSeverity.WARNING, "Faltan datos de identificación: ${missing.joinToString(", ")}. Conviene corregirlos para auditoría y análisis.", "${missing.size} faltante(s)")
        }
        val gpsResult = gpsCheck ?: ReadinessCheck("GPS_FIX", "Señal GPS inicial", ReadinessSeverity.WARNING, "Aún no se ha verificado un fix GPS real.", "Pendiente")
        return base.copy(checks = base.checks + listOf(gpsResult, syncCheck, identityCheck))
    }

    fun logReport() {
        Log.i(TAG, "PREFLIGHT_EVALUATED trip=$tripId version=${FieldReadiness.VERSION} state=${report.state} blockers=${report.blockers.size} warnings=${report.warnings.size} passed=${report.passed.size}")
        report.checks.forEach { check -> Log.i(TAG, "PREFLIGHT_CHECK trip=$tripId id=${check.id} severity=${check.severity} value=${check.value ?: "NONE"}") }
    }

    fun refresh(probeGps: Boolean = true) {
        report = composeReport()
        logReport()
        if (probeGps && report.blockers.none { it.id == "LOCATION_PERMISSION" || it.id == "LOCATION_SERVICES" }) {
            scope.launch {
                val fix = runCatching { gps.getBestFixForStartTrip(timeoutMs = 4_000L) }.getOrNull()
                gpsCheck = when {
                    fix == null || fix.lat == 0.0 || fix.lon == 0.0 || fix.status == "NO_FIX" -> ReadinessCheck("GPS_FIX", "Señal GPS inicial", ReadinessSeverity.WARNING, "No se obtuvo un fix GPS utilizable todavía. Puedes esperar y revisar de nuevo.", "Sin fix")
                    fix.accM <= 25.0 -> ReadinessCheck("GPS_FIX", "Señal GPS inicial", ReadinessSeverity.PASS, "Se obtuvo un fix GPS utilizable antes de iniciar.", "±${fix.accM.toInt()} m")
                    else -> ReadinessCheck("GPS_FIX", "Señal GPS inicial", ReadinessSeverity.WARNING, "El GPS responde, pero la precisión inicial es baja. Esperar unos segundos puede mejorarla.", "±${fix.accM.toInt()} m")
                }
                report = composeReport()
                logReport()
            }
        }
    }

    LaunchedEffect(tripId, trip, pendingSync) { refresh(probeGps = gpsCheck == null) }

    val alreadyStarted = pointCount > 0 || TrackingService.isRunning

    when {
        trip == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        trip?.endTime != null -> AsdTripDetailScreen(tripId = tripId, onBack = onBack, onOpenMap = onOpenMap)
        alreadyStarted -> AsdTripDetailScreen(tripId = tripId, onBack = onBack, onOpenMap = onOpenMap)
        accepted -> AsdTripDetailScreen(tripId = tripId, onBack = onBack, onOpenMap = onOpenMap)
        else -> FieldReadinessDialog(
            report = report,
            onRefresh = { refresh(probeGps = true) },
            onStart = {
                report = composeReport()
                logReport()
                if (report.canStart) {
                    accepted = true
                    Log.i(TAG, "PREFLIGHT_ACCEPTED trip=$tripId state=${report.state} warnings=${report.warnings.size}")
                } else {
                    Log.w(TAG, "PREFLIGHT_BLOCKED trip=$tripId blockers=${report.blockers.joinToString(",") { it.id }}")
                }
            },
            onDismiss = {
                Log.i(TAG, "PREFLIGHT_CANCELLED trip=$tripId")
                onBack()
            },
        )
    }
}
