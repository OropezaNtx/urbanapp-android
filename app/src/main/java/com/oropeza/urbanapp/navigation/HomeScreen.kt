package com.oropeza.urbanapp.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.oropeza.urbanapp.BuildConfig
import com.oropeza.urbanapp.core.runtime.UrbanRuntime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenDashboard: () -> Unit,
    onOpenAsd: () -> Unit,
    onOpenCc: () -> Unit,
    onOpenFov: () -> Unit,
    onOpenLicense: () -> Unit
) {
    val context = LocalContext.current
    val identity = remember { UrbanRuntime.identity(context) }
    val orgId = remember { UrbanRuntime.platformSettings().getOrganizationId(context) }
    val projId = remember { UrbanRuntime.platformSettings().getProjectId(context) }
    val env = remember { UrbanRuntime.platformSettings().getEnvironment(context) }
    
    val pendingSyncCount by UrbanRuntime.syncStatus().pendingSyncCountFlow().collectAsState(initial = 0)
    val lastSyncTime by UrbanRuntime.syncStatus().lastSyncTimeFlow().collectAsState(initial = null)

    val licenseStatus = remember { UrbanRuntime.licenseStatus(context) }
    val isLicenseActive = licenseStatus == com.oropeza.urbanapp.core.license.UrbanLicenseStatus.ACTIVE

    val canOpenDashboard = isLicenseActive
    val canOpenAsd = isLicenseActive
    val canOpenCc = isLicenseActive
    val canOpenFov = isLicenseActive

    val licenseStatusColor = when {
        isLicenseActive -> Color(0xFF16A34A)
        licenseStatus == com.oropeza.urbanapp.core.license.UrbanLicenseStatus.TRIAL -> Color(0xFFEAB308)
        else -> MaterialTheme.colorScheme.error
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("UrbanApp") }) }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Pantalla principal", style = MaterialTheme.typography.titleLarge)
            Text("Selecciona un módulo:")

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Estado de licencia", style = MaterialTheme.typography.labelMedium)
                    Text(
                        licenseStatus.name,
                        color = licenseStatusColor,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Button(
                onClick = onOpenDashboard,
                enabled = canOpenDashboard,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Dashboard Operativo")
            }

            OutlinedButton(
                onClick = onOpenAsd,
                enabled = canOpenAsd,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Ascensos y Descensos (ASD)")
            }

            OutlinedButton(
                onClick = onOpenCc,
                enabled = canOpenCc,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cierres de Circuito (CC)")
            }

            OutlinedButton(
                onClick = onOpenFov,
                enabled = canOpenFov,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("FOV (Frecuencia Observable)")
            }

            OutlinedButton(onClick = onOpenLicense, modifier = Modifier.fillMaxWidth()) {
                Text("Licencia y dispositivo")
            }

            Spacer(Modifier.weight(1f))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Acerca de UrbanApp", style = MaterialTheme.typography.titleMedium)
                    Text("Módulo ASD / FOV / CC", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Text("Versión: ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
                    Text("Build: ${BuildConfig.VERSION_CODE}", style = MaterialTheme.typography.bodyMedium)
                    
                    val dateFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                    val buildDate = dateFmt.format(Date(BuildConfig.BUILD_TIME))
                    Text("Fecha compilación: $buildDate", style = MaterialTheme.typography.bodySmall)
                    
                    Spacer(Modifier.height(4.dp))
                    Text("Device ID: ${identity.installationId.take(8).uppercase()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    Text("Org: $orgId · Proj: $projId", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    Text("Env: ${env.uppercase()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    
                    if (pendingSyncCount > 0) {
                        Text("Pendientes nube: $pendingSyncCount", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    } else if (lastSyncTime != null) {
                        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
                        Text("Sincronizado: ${timeFmt.format(Date(lastSyncTime!!))}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF35D36B))
                    }
                }
            }
        }
    }
}
