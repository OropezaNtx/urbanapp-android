package com.oropeza.urbanapp.asd.readiness

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oropeza.urbanapp.asd.AsdGraph
import com.oropeza.urbanapp.asd.data.local.Trip
import com.oropeza.urbanapp.asd.location.TrackingService
import com.oropeza.urbanapp.asd.ui.viewmodel.AsdTripDetailScreen

private const val TAG = "FieldReadiness"

@Composable
fun AsdTripReadinessGate(
    tripId: Long,
    onBack: () -> Unit,
    onOpenMap: (Long) -> Unit,
) {
    val context = LocalContext.current
    val trip by AsdGraph.repo.tripFlow(tripId).collectAsState(initial = null)
    var accepted by rememberSaveable(tripId) { mutableStateOf(false) }
    var report by remember(tripId) { mutableStateOf(FieldReadiness.evaluate(context)) }

    fun refresh() {
        report = FieldReadiness.evaluate(context)
        Log.i(TAG, "PREFLIGHT_EVALUATED trip=$tripId version=${FieldReadiness.VERSION} state=${report.state} blockers=${report.blockers.size} warnings=${report.warnings.size} passed=${report.passed.size}")
        report.checks.forEach { check ->
            Log.i(TAG, "PREFLIGHT_CHECK trip=$tripId id=${check.id} severity=${check.severity} value=${check.value ?: "NONE"}")
        }
    }

    LaunchedEffect(tripId) { refresh() }

    when {
        trip == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        trip?.endTime != null -> AsdTripDetailScreen(tripId = tripId, onBack = onBack, onOpenMap = onOpenMap)
        TrackingService.isRunning -> AsdTripDetailScreen(tripId = tripId, onBack = onBack, onOpenMap = onOpenMap)
        accepted -> AsdTripDetailScreen(tripId = tripId, onBack = onBack, onOpenMap = onOpenMap)
        else -> FieldReadinessDialog(
            report = report,
            onRefresh = { refresh() },
            onStart = {
                refresh()
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
