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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("NO SE PUEDE INICIAR", fontWeight = FontWeight.Black)
                Text(
                    "Corrige lo siguiente para comenzar el levantamiento.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 340.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(report.blockers, key = { it.id }) { check ->
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
                            "STORAGE" -> ({
                                runCatching { context.startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)) }
                            })
                            else -> null
                        },
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onRefresh) {
                Text("VOLVER A COMPROBAR")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCELAR") }
        },
    )
}

@Composable
private fun ReadinessRow(check: ReadinessCheck, onAction: (() -> Unit)?) {
    val tone = MaterialTheme.colorScheme.error
    Surface(
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, tone.copy(alpha = 0.35f)),
        color = tone.copy(alpha = 0.05f),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("×", color = tone, fontWeight = FontWeight.Black)
                Spacer(Modifier.width(8.dp))
                Text(check.title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                check.value?.let { Text(it, color = tone, fontWeight = FontWeight.Bold) }
            }
            Text(check.summary, style = MaterialTheme.typography.bodySmall)
            if (onAction != null) {
                TextButton(onClick = onAction, contentPadding = PaddingValues(0.dp)) {
                    Text(
                        when (check.id) {
                            "LOCATION_PERMISSION" -> "CONCEDER PERMISO"
                            else -> "ABRIR AJUSTES"
                        }
                    )
                }
            }
        }
    }
}
