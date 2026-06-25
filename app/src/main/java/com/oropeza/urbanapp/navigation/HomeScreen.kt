package com.oropeza.urbanapp.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.oropeza.urbanapp.BuildConfig
import com.oropeza.urbanapp.asd.AsdGraph
import com.oropeza.urbanapp.core.platform.UrbanPlatformSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenDashboard: () -> Unit,
    onOpenAsd: () -> Unit,
    onOpenCc: () -> Unit,
    onOpenFov: () -> Unit
) {
    val context = LocalContext.current
    val identity = remember { AsdGraph.getDeviceIdentity(context) }
    val orgId = remember { UrbanPlatformSettings.getOrganizationId(context) }
    val projId = remember { UrbanPlatformSettings.getProjectId(context) }
    val env = remember { UrbanPlatformSettings.getEnvironment(context) }

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

            Button(onClick = onOpenDashboard, modifier = Modifier.fillMaxWidth()) {
                Text("Dashboard Operativo")
            }

            OutlinedButton(onClick = onOpenAsd, modifier = Modifier.fillMaxWidth()) {
                Text("Ascensos y Descensos (ASD)")
            }

            OutlinedButton(onClick = onOpenCc, modifier = Modifier.fillMaxWidth()) {
                Text("Cierres de Circuito (CC)")
            }

            OutlinedButton(onClick = onOpenFov, modifier = Modifier.fillMaxWidth()) {
                Text("FOV (Frecuencia Observable)")
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
                }
            }
        }
    }
}
