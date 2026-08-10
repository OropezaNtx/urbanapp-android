package com.oropeza.urbanapp.asd.readiness

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun FieldReadinessDialog(
    report: FieldReadinessReport,
    onRefresh: () -> Unit,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { onRefresh() }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onRefresh() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("PREPARACIÓN DE CAMPO", fontWeight = FontWeight.Black)
                Text(
                    when (report.state) {
                        "READY" -> "Equipo listo para iniciar"
                        "READY_WITH_WARNINGS" -> "Equipo utilizable con advertencias"
                        else -> "Corrige los bloqueos antes de iniciar"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (report.state) {
                        "READY" -> Color(0xFF16A34A)
                        "READY_WITH_WARNINGS" -> Color(0xFFF59E0B)
                        else -> MaterialTheme.colorScheme.error
                    }
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(report.checks, key = { it.id }) { check ->
                    ReadinessRow(
                        check = check,
                        onAction = when (check.id) {
                            "LOCATION_PERMISSION" -> ({
                                locationPermissionLauncher.launch(arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                ))
                            })
                            "LOCATION_SERVICES" -> ({
                                runCatching { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                            })
                            "NOTIFICATIONS" -> if (android.os.Build.VERSION.SDK_INT >= 33) ({
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }) else null
                            else -> null
                        },
                    )
                }
                item {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Internet no es obligatorio para capturar. Afora trabaja offline y sincroniza cuando vuelve la conexión.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onStart, enabled = report.canStart) {
                Text(if (report.warnings.isEmpty()) "INICIAR LEVANTAMIENTO" else "INICIAR CON ADVERTENCIAS")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onRefresh) { Text("REVISAR DE NUEVO") }
                TextButton(onClick = onDismiss) { Text("CANCELAR") }
            }
        },
    )
}

@Composable
private fun ReadinessRow(check: ReadinessCheck, onAction: (() -> Unit)?) {
    val tone = when (check.severity) {
        ReadinessSeverity.PASS -> Color(0xFF16A34A)
        ReadinessSeverity.WARNING -> Color(0xFFF59E0B)
        ReadinessSeverity.BLOCKER -> MaterialTheme.colorScheme.error
    }
    Surface(
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, tone.copy(alpha = 0.35f)),
        color = tone.copy(alpha = 0.05f),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (check.severity) {
                        ReadinessSeverity.PASS -> "✓"
                        ReadinessSeverity.WARNING -> "!"
                        ReadinessSeverity.BLOCKER -> "×"
                    },
                    color = tone,
                    fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.width(8.dp))
                Text(check.title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                check.value?.let { Text(it, color = tone, fontWeight = FontWeight.Bold) }
            }
            Text(check.summary, style = MaterialTheme.typography.bodySmall)
            if (onAction != null && check.severity != ReadinessSeverity.PASS) {
                TextButton(onClick = onAction, contentPadding = PaddingValues(0.dp)) {
                    Text(
                        when (check.id) {
                            "LOCATION_PERMISSION", "NOTIFICATIONS" -> "CONCEDER PERMISO"
                            else -> "ABRIR AJUSTES"
                        }
                    )
                }
            }
        }
    }
}
