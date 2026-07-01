package com.oropeza.urbanapp.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oropeza.urbanapp.BuildConfig
import com.oropeza.urbanapp.core.runtime.UrbanRuntime
import com.oropeza.urbanapp.core.license.UrbanLicenseStatus
import com.oropeza.urbanapp.core.identity.UrbanDeviceIdentity
import com.oropeza.urbanapp.ui.components.*
import com.oropeza.urbanapp.ui.theme.LocalAforaColors
import com.oropeza.urbanapp.ui.theme.LocalAforaTypography
import com.oropeza.urbanapp.ui.theme.UrbanAppTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    UrbanAppTheme {
        HomeScreenContent(
            identity = UrbanDeviceIdentity(
                installationId = "PROD-INSTALL-ID-AFORA-123456",
                androidId = "android-id",
                manufacturer = "Afora",
                model = "Enterprise Pro",
                deviceName = "Field Device",
                androidVersion = "14",
                sdkInt = 34,
                appVersionName = "1.0.0-PROD",
                appVersionCode = 1,
                packageName = "com.oropeza.urbanapp",
                createdAt = 0,
                updatedAt = 0
            ),
            pendingSyncCount = 0,
            lastSyncTime = System.currentTimeMillis(),
            instStatus = com.oropeza.urbanapp.core.platform.InstallationStatus.ACTIVE,
            licenseStatus = UrbanLicenseStatus.ACTIVE,
            onOpenDashboard = {},
            onOpenAsd = {},
            onOpenCc = {},
            onOpenFov = {},
            onOpenLicense = {}
        )
    }
}

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
    
    val pendingSyncCount by UrbanRuntime.syncStatus().pendingSyncCountFlow().collectAsState(initial = 0)
    val lastSyncTime by UrbanRuntime.syncStatus().lastSyncTimeFlow().collectAsState(initial = null)

    val instStatus = remember { UrbanRuntime.installationStatus(context) }
    val licenseStatus = remember { UrbanRuntime.licenseStatus(context) }

    HomeScreenContent(
        identity = identity,
        pendingSyncCount = pendingSyncCount,
        lastSyncTime = lastSyncTime,
        instStatus = instStatus,
        licenseStatus = licenseStatus,
        onOpenDashboard = onOpenDashboard,
        onOpenAsd = onOpenAsd,
        onOpenCc = onOpenCc,
        onOpenFov = onOpenFov,
        onOpenLicense = onOpenLicense
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenContent(
    identity: UrbanDeviceIdentity,
    pendingSyncCount: Int,
    lastSyncTime: Long?,
    instStatus: com.oropeza.urbanapp.core.platform.InstallationStatus,
    licenseStatus: UrbanLicenseStatus,
    onOpenDashboard: () -> Unit,
    onOpenAsd: () -> Unit,
    onOpenCc: () -> Unit,
    onOpenFov: () -> Unit,
    onOpenLicense: () -> Unit
) {
    val colors = LocalAforaColors.current
    val typography = LocalAforaTypography.current
    
    val isLicenseActive = licenseStatus == UrbanLicenseStatus.ACTIVE
    val licenseStatusColor = when {
        isLicenseActive -> colors.Success
        licenseStatus == UrbanLicenseStatus.TRIAL -> colors.Warning
        else -> colors.Danger
    }

    Scaffold(
        containerColor = colors.Background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.Surface,
                    titleContentColor = colors.Secondary
                ),
                title = { Text("AFORA", style = typography.Headline, fontWeight = FontWeight.Black) }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Operational Status Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AforaSectionHeader("ESTADO OPERATIVO")
                AforaOperationalCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            val statusText = when {
                                licenseStatus == UrbanLicenseStatus.ACTIVE -> "EQUIPO ACTIVO"
                                instStatus == com.oropeza.urbanapp.core.platform.InstallationStatus.PENDING -> "PENDIENTE DE ACTIVACIÓN"
                                licenseStatus == UrbanLicenseStatus.REVOKED -> "ACCESO RESTRINGIDO"
                                licenseStatus == UrbanLicenseStatus.EXPIRED -> "LICENCIA VENCIDA"
                                else -> "VERIFICANDO ESTADO"
                            }
                            Text(
                                statusText,
                                style = typography.Title,
                                color = licenseStatusColor,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                if (isLicenseActive) "Listo para iniciar operaciones" else "Contacte a su supervisor",
                                style = typography.BodySmall,
                                color = colors.Secondary.copy(alpha = 0.6f)
                            )
                        }
                        AforaHealthIndicator(isHealthy = isLicenseActive)
                    }
                }
            }

            // Primary Action Section
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                AforaPrimaryButton(
                    text = "ABRIR PANEL OPERATIVO",
                    onClick = onOpenDashboard,
                    enabled = isLicenseActive
                )

                HorizontalDivider(color = colors.Outline.copy(alpha = 0.2f))

                AforaSectionHeader("MÓDULOS DE LEVANTAMIENTO")
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AforaSecondaryButton(text = "LEVANTAMIENTOS ASD", enabled = isLicenseActive, onClick = onOpenAsd)
                    AforaSecondaryButton(text = "LEVANTAMIENTOS CC", enabled = isLicenseActive, onClick = onOpenCc)
                    AforaSecondaryButton(text = "LEVANTAMIENTOS FOV", enabled = isLicenseActive, onClick = onOpenFov)
                }
            }

            Spacer(Modifier.weight(1f))

            // Configuration & Identity (Low Hierarchy)
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                AforaSecondaryButton(
                    text = "CONFIGURACIÓN DEL SISTEMA",
                    onClick = onOpenLicense,
                    isOutlined = false
                )

                AforaOperationalCard(containerColor = Color.Transparent, borderAlpha = 0.1f) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AforaMetadataRow(label = "ID DISPOSITIVO", value = identity.installationId.take(12).uppercase())
                        AforaMetadataRow(label = "VERSIÓN", value = "v${BuildConfig.VERSION_NAME}", valueColor = colors.Secondary.copy(alpha = 0.6f))
                        
                        if (pendingSyncCount > 0 || lastSyncTime != null) {
                            HorizontalDivider(color = colors.Outline.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 4.dp))
                            
                            val syncLabel = if (pendingSyncCount > 0) "PENDIENTES" else "ÚLTIMO RESPALDO"
                            val syncVal = if (pendingSyncCount > 0) "$pendingSyncCount" else SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(lastSyncTime ?: 0L))
                            val syncColor = if (pendingSyncCount > 0) colors.Warning else colors.Success
                            
                            AforaMetadataRow(
                                label = syncLabel, 
                                value = syncVal, 
                                labelColor = syncColor.copy(alpha = 0.6f),
                                valueColor = syncColor
                            )
                        }
                    }
                }
            }
        }
    }
}
