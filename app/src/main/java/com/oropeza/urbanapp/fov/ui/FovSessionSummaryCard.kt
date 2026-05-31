package com.oropeza.urbanapp.fov.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.oropeza.urbanapp.asd.data.local.FovObservation
import com.oropeza.urbanapp.asd.data.local.FovSession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FovSessionSummaryCard(
    session: FovSession,
    observations: List<FovObservation>,
    modifier: Modifier = Modifier
) {
    val summary = remember(session, observations) {
        FovSessionSummary.from(session, observations)
    }

    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Resumen de sesión", style = MaterialTheme.typography.titleMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryItem("Registros", summary.totalObservations.toString(), Modifier.weight(1f))
                SummaryItem("Rutas", summary.uniqueRoutes.toString(), Modifier.weight(1f))
                SummaryItem("GPS válido", summary.validGps.toString(), Modifier.weight(1f))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryItem("Inicio", summary.firstTime.ifBlank { "—" }, Modifier.weight(1f))
                SummaryItem("Último", summary.lastTime.ifBlank { "—" }, Modifier.weight(1f))
                SummaryItem("Duración", summary.durationText, Modifier.weight(1f))
            }

            Text("POI: ${session.poiKey}", style = MaterialTheme.typography.bodySmall)
            Text("Estado: ${summary.sessionStatus}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SummaryItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

private data class FovSessionSummary(
    val totalObservations: Int,
    val uniqueRoutes: Int,
    val validGps: Int,
    val firstTime: String,
    val lastTime: String,
    val durationText: String,
    val sessionStatus: String
) {
    companion object {
        private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale("es", "MX"))

        fun from(session: FovSession, observations: List<FovObservation>): FovSessionSummary {
            val sorted = observations.sortedBy { it.timeMs }
            val first = sorted.firstOrNull()?.timeMs
            val last = sorted.lastOrNull()?.timeMs
            val validGps = observations.count { it.lat != 0.0 && it.lon != 0.0 && it.locationStatus != "NO_FIX" }
            val uniqueRoutes = observations.map { it.routeUid }.distinct().size

            return FovSessionSummary(
                totalObservations = observations.size,
                uniqueRoutes = uniqueRoutes,
                validGps = validGps,
                firstTime = first?.let { timeFormat.format(Date(it)) }.orEmpty(),
                lastTime = last?.let { timeFormat.format(Date(it)) }.orEmpty(),
                durationText = durationText(first, last),
                sessionStatus = if (session.endedAt == null) "ABIERTA" else "CERRADA"
            )
        }

        private fun durationText(first: Long?, last: Long?): String {
            if (first == null || last == null || last < first) return "—"
            val minutes = (last - first) / 60_000L
            val hours = minutes / 60L
            val remainingMinutes = minutes % 60L
            return if (hours > 0) "${hours}h ${remainingMinutes}m" else "${remainingMinutes}m"
        }
    }
}
