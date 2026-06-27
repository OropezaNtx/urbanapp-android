package com.oropeza.urbanapp.license

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.oropeza.urbanapp.core.config.UrbanConfiguration
import com.oropeza.urbanapp.core.runtime.UrbanRuntime
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseDiagnosticsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val manager = remember { LicenseManager(context) }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(manager.getCachedState()) }
    var config by remember { mutableStateOf(UrbanRuntime.configuration(context)) }
    var configSource by remember { mutableStateOf(UrbanRuntime.configurationSource(context)) }
    var configLastFetchAt by remember { mutableStateOf(UrbanRuntime.configurationLastFetchAt(context)) }
    var loading by remember { mutableStateOf(false) }
    var loadingConfig by remember { mutableStateOf(false) }
    var loadingHeartbeat by remember { mutableStateOf(false) }
    var lastHeartbeatResult by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun refreshRemote() {
        loading = true
        error = null
        scope.launch {
            try {
                state = manager.checkLicense()
            } catch (e: Exception) {
                error = e.message ?: e.toString()
            } finally {
                loading = false
            }
        }
    }

    fun refreshConfig() {
        loadingConfig = true
        error = null
        scope.launch {
            try {
                config = UrbanRuntime.refreshConfiguration(context)
                configSource = UrbanRuntime.configurationSource(context)
                configLastFetchAt = UrbanRuntime.configurationLastFetchAt(context)
            } catch (e: Exception) {
                error = e.message ?: e.toString()
                config = UrbanRuntime.configuration(context)
                configSource = UrbanRuntime.configurationSource(context)
                configLastFetchAt = UrbanRuntime.configurationLastFetchAt(context)
            } finally {
                loadingConfig = false
            }
        }
    }

    fun publishHeartbeat() {
        loadingHeartbeat = true
        error = null
        lastHeartbeatResult = null
        scope.launch {
            val result = UrbanRuntime.publishPlatformHeartbeat(context)
            result
                .onSuccess { lastHeartbeatResult = "Heartbeat enviado correctamente a installations/{installationId}." }
                .onFailure { error = it.message ?: it.toString() }
            loadingHeartbeat = false
        }
    }

    LaunchedEffect(Unit) {
        if (state == null) refreshRemote()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Licencia y dispositivo") }) }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { refreshRemote() }, enabled = !loading, modifier = Modifier.weight(1f)) {
                    Text(if (loading) "Validando..." else "Validar licencia")
                }
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                    Text("Regresar")
                }
            }

            Button(onClick = { refreshConfig() }, enabled = !loadingConfig, modifier = Modifier.fillMaxWidth()) {
                Text(if (loadingConfig) "Actualizando configuración..." else "Actualizar app_config")
            }

            Button(onClick = { publishHeartbeat() }, enabled = !loadingHeartbeat, modifier = Modifier.fillMaxWidth()) {
                Text(if (loadingHeartbeat) "Enviando heartbeat..." else "Enviar heartbeat de plataforma")
            }

            lastHeartbeatResult?.let {
                StatusCard(title = "Heartbeat", value = it, color = Color(0xFF16A34A))
            }

            error?.let {
                StatusCard(title = "Error", value = it, color = MaterialTheme.colorScheme.error)
            }

            val current = state
            if (current == null) {
                StatusCard(title = "Estado", value = "Sin información de licencia", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LicenseSummary(current)
                LicenseModulesCard(current.license.modules)
                LicenseRawCard(current)
            }

            AppConfigCard(config, configSource, configLastFetchAt)
        }
    }
}

@Composable
private fun LicenseSummary(state: InstallationLicenseState) {
    val license = state.license
    val accessColor = when {
        state.canUseApp -> Color(0xFF16A34A)
        state.canUseOffline -> Color(0xFFEAB308)
        else -> MaterialTheme.colorScheme.error
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Resumen", style = MaterialTheme.typography.titleMedium)
            Text(
                text = when {
                    state.canUseApp -> "Licencia activa"
                    state.canUseOffline -> "Modo offline permitido por periodo de gracia"
                    else -> "Licencia no habilitada"
                },
                color = accessColor,
                style = MaterialTheme.typography.titleSmall
            )
            InfoLine("Installation ID", state.installationId)
            InfoLine("Estado instalación", state.installationStatus)
            InfoLine("Licencia", license.licenseId.ifBlank { "—" })
            InfoLine("Estado licencia", license.status)
            InfoLine("Cliente", license.customerId.ifBlank { "—" })
            InfoLine("Proyecto", license.projectId.ifBlank { "—" })
            InfoLine("Plan", license.plan.ifBlank { "—" })
            InfoLine("Fuente", license.source)
            InfoLine("Última validación", formatMs(license.lastCheckedAt))
            InfoLine("Expira", license.expiresAt?.let { formatMs(it) } ?: "—")
            InfoLine("Mensaje", state.message.ifBlank { "—" })
        }
    }
}

@Composable
private fun LicenseModulesCard(modules: LicenseModules) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Módulos habilitados", style = MaterialTheme.typography.titleMedium)
            ModuleLine("ASD", modules.asd)
            ModuleLine("Demoras", modules.delays)
            ModuleLine("Banderas", modules.flags)
            ModuleLine("Live", modules.live)
            ModuleLine("Exportaciones", modules.exports)
            ModuleLine("Garmin", modules.garmin)
            ModuleLine("Analytics", modules.analytics)
        }
    }
}

@Composable
private fun LicenseRawCard(state: InstallationLicenseState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Diagnóstico", style = MaterialTheme.typography.titleMedium)
            InfoLine("canUseApp", state.canUseApp.toString())
            InfoLine("canUseOffline", state.canUseOffline.toString())
            InfoLine("isExpiredByDate", state.license.isExpiredByDate.toString())
            InfoLine("gracePeriodDays", state.license.gracePeriodDays.toString())
            InfoLine("checkedAt", formatMs(state.checkedAt))
        }
    }
}

@Composable
private fun AppConfigCard(config: UrbanConfiguration, source: String, lastFetchAt: Long) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Urban Platform Core · app_config", style = MaterialTheme.typography.titleMedium)
            InfoLine("Fuente", source)
            InfoLine("Última descarga", formatMs(lastFetchAt))
            InfoLine("Ambiente", config.environment)
            InfoLine("Heartbeat", "${config.heartbeatIntervalSeconds} s")
            InfoLine("Sync", "${config.syncIntervalSeconds} s")
            InfoLine("Track chunk", config.trackChunkSize.toString())
            InfoLine("GPS profile", config.gpsProfile)
            InfoLine("Min app version", config.minSupportedAppVersion ?: "—")
            InfoLine("Módulos", config.enabledModules.joinToString(", "))
            Spacer(Modifier.height(4.dp))
            Text("Feature flags", style = MaterialTheme.typography.labelMedium)
            config.featureFlags.toSortedMap().forEach { (flag, enabled) ->
                ModuleLine(flag, enabled)
            }
        }
    }
}

@Composable
private fun StatusCard(title: String, value: String, color: Color) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(value, color = color)
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ModuleLine(label: String, enabled: Boolean) {
    val color = if (enabled) Color(0xFF16A34A) else MaterialTheme.colorScheme.error
    Text("${if (enabled) "✓" else "✕"} $label", color = color, style = MaterialTheme.typography.bodyMedium)
}

private fun formatMs(value: Long): String {
    if (value <= 0L) return "—"
    val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
    return fmt.format(Date(value))
}
