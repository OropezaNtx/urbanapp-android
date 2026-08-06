package com.oropeza.urbanapp.asd.ui.viewmodel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oropeza.urbanapp.asd.location.TrackingService
import com.oropeza.urbanapp.ui.components.AforaMetadataRow
import com.oropeza.urbanapp.ui.components.AforaOperationalCard
import com.oropeza.urbanapp.ui.components.AforaSectionHeader
import com.oropeza.urbanapp.ui.components.AforaStatusChip
import com.oropeza.urbanapp.ui.theme.LocalAforaColors
import com.oropeza.urbanapp.ui.theme.LocalAforaTypography
import java.util.Locale
import kotlin.math.abs

@Composable
fun TrackingDiagnosticsCard(
    metrics: TrackingService.Companion.TrackingMetrics,
    roomPointCount: Int,
    isEnded: Boolean
) {
    val colors = LocalAforaColors.current
    val typography = LocalAforaTypography.current
    val expectedSamples = if (metrics.elapsedMs > 0L) metrics.elapsedMs / 2_000L else 0L
    val cadenceGap = roomPointCount.toLong() - expectedSamples
    val cadenceHealthy = expectedSamples == 0L || abs(cadenceGap) <= 2L
    val noFixPercent = if (metrics.persistedSampleCount > 0L) {
        metrics.noFixSampleCount * 100.0 / metrics.persistedSampleCount
    } else {
        0.0
    }
    val statusColor = when {
        metrics.watchdogRestartCount > 2 -> colors.Danger
        !cadenceHealthy -> colors.Warning
        metrics.averageRoomInsertMs > 20.0 -> colors.Warning
        else -> colors.Success
    }
    val statusText = when {
        isEnded -> "SESIÓN CERRADA"
        metrics.isActive && cadenceHealthy -> "MOTOR ESTABLE"
        metrics.isActive -> "REVISAR CADENCIA"
        else -> "SIN SESIÓN ACTIVA"
    }

    AforaOperationalCard(modifier = Modifier.padding(horizontal = 16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                AforaSectionHeader("DIAGNÓSTICO DEL MOTOR", Modifier.weight(1f))
                AforaStatusChip(statusText, statusColor)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                DiagnosticMetric(
                    label = "PERSISTIDOS",
                    value = roomPointCount.toString(),
                    modifier = Modifier.weight(1f),
                    valueColor = if (cadenceHealthy) colors.Success else colors.Warning
                )
                DiagnosticMetric(
                    label = "ESPERADOS",
                    value = expectedSamples.toString(),
                    modifier = Modifier.weight(1f)
                )
                DiagnosticMetric(
                    label = "DIFERENCIA",
                    value = if (cadenceGap > 0) "+$cadenceGap" else cadenceGap.toString(),
                    modifier = Modifier.weight(1f),
                    valueColor = if (cadenceHealthy) colors.Success else colors.Warning
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AforaMetadataRow("TIEMPO ACTIVO", formatDuration(metrics.elapsedMs))
                AforaMetadataRow("MODO", metrics.engineMode)
                AforaMetadataRow("FIXES RECIBIDOS", metrics.rawFixCount.toString())
                AforaMetadataRow("MUESTRAS LIVE", metrics.liveSampleCount.toString())
                AforaMetadataRow(
                    "MUESTRAS SIN FIX",
                    "${metrics.noFixSampleCount} (${String.format(Locale.US, "%.1f", noFixPercent)}%)",
                    valueColor = if (noFixPercent > 10.0) colors.Warning else colors.Secondary
                )
                AforaMetadataRow(
                    "PRECISIÓN PROMEDIO",
                    if (metrics.averageAccuracyM > 0.0) {
                        "±${String.format(Locale.US, "%.1f", metrics.averageAccuracyM)} m"
                    } else {
                        "-"
                    }
                )
                AforaMetadataRow("MAYOR PÉRDIDA DE FIX", formatDuration(metrics.maxRawFixGapMs))
                AforaMetadataRow(
                    "ROOM PROMEDIO",
                    "${String.format(Locale.US, "%.2f", metrics.averageRoomInsertMs)} ms"
                )
                AforaMetadataRow(
                    "ROOM MÁXIMO",
                    "${String.format(Locale.US, "%.2f", metrics.maxRoomInsertMs)} ms",
                    valueColor = if (metrics.maxRoomInsertMs > 50.0) colors.Warning else colors.Secondary
                )
                AforaMetadataRow(
                    "REINICIOS WATCHDOG",
                    metrics.watchdogRestartCount.toString(),
                    valueColor = if (metrics.watchdogRestartCount > 0) colors.Warning else colors.Success
                )
            }
        }
    }
}

@Composable
private fun DiagnosticMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null
) {
    val colors = LocalAforaColors.current
    val typography = LocalAforaTypography.current
    Column(modifier = modifier) {
        Text(
            text = value,
            style = typography.Title,
            fontWeight = FontWeight.Black,
            color = valueColor ?: colors.Secondary
        )
        Text(
            text = label,
            style = typography.Label,
            color = colors.Secondary.copy(alpha = 0.45f),
            fontWeight = FontWeight.Bold
        )
    }
}

private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "00:00"
    val totalSeconds = durationMs / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
