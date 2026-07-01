package com.oropeza.urbanapp.asd.ui.viewmodel

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oropeza.urbanapp.BuildConfig
import com.oropeza.urbanapp.asd.AsdGraph
import com.oropeza.urbanapp.asd.data.local.AsdCatalogSyncState
import com.oropeza.urbanapp.asd.data.local.AsdFieldPersonCatalogItem
import com.oropeza.urbanapp.asd.data.local.AsdRouteCatalogItem
import com.oropeza.urbanapp.asd.data.local.AsdVehicleTypeCatalogItem
import com.oropeza.urbanapp.asd.importer.AsdCatalogXlsxImporter
import com.oropeza.urbanapp.asd.location.LocationProvider
import com.oropeza.urbanapp.asd.sync.AsdCatalogFirestoreSync
import com.oropeza.urbanapp.core.runtime.UrbanRuntime
import com.oropeza.urbanapp.ui.components.*
import com.oropeza.urbanapp.ui.theme.LocalAforaColors
import com.oropeza.urbanapp.ui.theme.LocalAforaTypography
import com.oropeza.urbanapp.ui.theme.UrbanAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.tooling.preview.Preview

class AsdNewTripVM : ViewModel() {
    val syncState: Flow<AsdCatalogSyncState?> = AsdGraph.repo.catalogSyncStateFlow()
    val vehicleTypesCatalog: Flow<List<AsdVehicleTypeCatalogItem>> = AsdGraph.repo.activeAsdVehicleTypesFlow()
    fun peopleByRole(role: String): Flow<List<AsdFieldPersonCatalogItem>> = AsdGraph.repo.activeAsdPeopleByRoleFlow(role)
    suspend fun getRoute(id: String, dir: String): AsdRouteCatalogItem? = AsdGraph.repo.getAsdRouteByCatalogIdAndDirection(id, dir)
    suspend fun getLastTripNextWaypoint(): Int? = AsdGraph.repo.getLastTripNextWaypoint()

    suspend fun createWithFix(
        planningRouteId: String,
        stopLat: Double,
        stopLon: Double,
        stopAltM: Double,
        stopAccM: Double,
        stopProvider: String,
        stopFixTime: Long,
        locationStatus: String,
        routeName: String,
        company: String?,
        vehicleEco: String?,
        direction: String,
        notes: String?,
        routeNumber: Int?,
        esFs: String?,
        baseStart: String?,
        baseEnd: String?,
        plateNumber: String?,
        vehicleType: String?,
        seatCapacity: Int?,
        aforador: String?,
        supervisor: String?,
        deviceNumber: String?,
        observerSex: String?,
        continueWaypoints: Boolean
    ): Long {
        return AsdGraph.repo.createTripWithStartFix(
            planningRouteId, stopLat, stopLon, stopAltM, stopAccM, stopProvider, stopFixTime, locationStatus,
            routeName, company, vehicleEco, direction, notes, routeNumber, esFs, baseStart, baseEnd,
            plateNumber, vehicleType, seatCapacity, aforador, supervisor, deviceNumber, observerSex, continueWaypoints
        )
    }
}

@Composable
fun AsdNewTripScreen(
    onCreated: (Long) -> Unit,
    onBack: () -> Unit,
    vm: AsdNewTripVM = viewModel()
) {
    val syncState by vm.syncState.collectAsState(initial = null)
    val context = LocalContext.current
    val gps = remember { LocationProvider(context) }
    val scope = rememberCoroutineScope()

    AsdNewTripContent(
        syncState = syncState,
        isLicenseActive = UrbanRuntime.licenseStatus(context) == com.oropeza.urbanapp.core.license.UrbanLicenseStatus.ACTIVE,
        onBack = onBack,
        onGetRoute = { id, dir -> vm.getRoute(id, dir) },
        onCreateTrip = { pId, rN, comp, vE, dir, nts, af, sup, dN, oS, rNum, esfs, bS, bE, pN, vT, sC, cW, setL, setG, setE ->
            scope.launch { 
                createTripFlow(vm, gps, pId, rN, comp, vE, dir, nts, af, sup, dN, oS, rNum, esfs, bS, bE, pN, vT, sC, cW, onCreated, setL, setG, setE) 
            }
        },
        hasGpsPermission = { gps.hasPermission() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AsdNewTripContent(
    syncState: AsdCatalogSyncState?,
    isLicenseActive: Boolean,
    onBack: () -> Unit,
    onGetRoute: suspend (String, String) -> AsdRouteCatalogItem?,
    onCreateTrip: (String, String, String, String, String, String, String, String, String, String, String, String, String, String, String, String, String, Boolean, (Boolean) -> Unit, (String?) -> Unit, (String?) -> Unit) -> Unit,
    hasGpsPermission: () -> Boolean
) {
    val colors = LocalAforaColors.current
    val typography = LocalAforaTypography.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    var pId by remember { mutableStateOf("") }
    var rN by remember { mutableStateOf("") }
    var comp by remember { mutableStateOf("") }
    var bS by remember { mutableStateOf("") }
    var bE by remember { mutableStateOf("") }
    var rNum by remember { mutableStateOf("") }
    var dir by remember { mutableStateOf("IDA") }
    var af by remember { mutableStateOf("") }
    
    var loading by remember { mutableStateOf(false) }
    var gpsMsg by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pId, dir) {
        if (pId.length >= 3) {
            val route = onGetRoute(pId, dir)
            if (route != null) {
                rN = if (route.derrotero.isNullOrBlank()) route.routeName else "${route.routeName} (${route.derrotero})"
                comp = route.company ?: ""
                bS = route.baseStart ?: ""
                bE = route.baseEnd ?: ""
            }
        }
    }

    Scaffold(
        containerColor = colors.Background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.Surface, titleContentColor = colors.Secondary),
                title = { Text("NUEVO LEVANTAMIENTO", style = typography.Headline, fontWeight = FontWeight.Black) },
                navigationIcon = { TextButton(onClick = { focusManager.clearFocus(); onBack() }) { Text("ATRÁS", color = colors.Primary, fontWeight = FontWeight.Bold) } }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            if (!isLicenseActive) AforaInlineAlert("Licencia no activa. Contacte a soporte.", colors.Danger)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AforaSectionHeader("PROYECTO Y CATÁLOGO")
                AforaOperationalCard {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        syncState?.let { Text(if(it.status == "READY") "Catálogo verificado ✅" else "Error en catálogo ❌", style = typography.BodySmall, color = if(it.status == "READY") colors.Success else colors.Danger, fontWeight = FontWeight.Bold) }
                        AforaTextField(value = pId, onValueChange = { pId = it.uppercase() }, label = "ID DE RUTA", modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }))
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AforaSectionHeader("DETALLES DE LA RUTA")
                AforaOperationalCard {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        AforaMetadataRow("NOMBRE", rN.ifBlank { "SIN SELECCIONAR" })
                        AforaMetadataRow("EMPRESA", comp.ifBlank { "NO DISPONIBLE" })
                        HorizontalDivider(color = colors.Outline.copy(alpha = 0.1f))
                        Text("SENTIDO", style = typography.Label, color = colors.Secondary.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AforaSecondaryButton("IDA", { dir = "IDA" }, Modifier.weight(1f), isOutlined = dir != "IDA")
                            AforaSecondaryButton("VUELTA", { dir = "VUELTA" }, Modifier.weight(1f), isOutlined = dir != "VUELTA")
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AforaSectionHeader("PERSONAL OPERATIVO")
                AforaOperationalCard { AforaTextField(value = af, onValueChange = { af = it.uppercase() }, label = "NOMBRE DEL OPERADOR", modifier = Modifier.fillMaxWidth()) }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                gpsMsg?.let { AforaInlineAlert(it, colors.Success) }
                error?.let { AforaInlineAlert(it, colors.Danger) }
            }

            AforaPrimaryButton(enabled = !loading && isLicenseActive, text = if (loading) "INICIANDO..." else "INICIAR LEVANTAMIENTO", onClick = {
                focusManager.clearFocus()
                if (pId.isBlank() || af.isBlank()) { error = "ID de Ruta y Operador son obligatorios."; return@AforaPrimaryButton }
                onCreateTrip(pId, rN, comp, "", dir, "", af, "", "", "", rNum, "", bS, bE, "", "", "", false, { loading = it }, { gpsMsg = it }, { error = it })
            })
            
            AforaMetadataRow("VERSIÓN", "v${BuildConfig.VERSION_NAME}", valueColor = colors.Secondary.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun AforaTextField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, keyboardOptions: KeyboardOptions = KeyboardOptions.Default, keyboardActions: KeyboardActions = KeyboardActions.Default) {
    val colors = LocalAforaColors.current; val typography = LocalAforaTypography.current
    OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label) }, modifier = modifier, singleLine = true, shape = MaterialTheme.shapes.extraSmall, textStyle = typography.BodyLarge, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colors.Primary, unfocusedBorderColor = colors.Outline.copy(alpha = 0.5f), focusedLabelColor = colors.Primary, unfocusedLabelColor = colors.Secondary.copy(alpha = 0.4f), focusedTextColor = colors.Secondary, unfocusedTextColor = colors.Secondary), keyboardOptions = keyboardOptions, keyboardActions = keyboardActions)
}

private suspend fun createTripFlow(vm: AsdNewTripVM, gps: LocationProvider, pId: String, rN: String, comp: String, vE: String, dir: String, nts: String, af: String, sup: String, dN: String, oS: String, rNum: String, esfs: String, bS: String, bE: String, pN: String, vT: String, sC: String, cW: Boolean, onC: (Long) -> Unit, setL: (Boolean) -> Unit, setG: (String?) -> Unit, setE: (String?) -> Unit) {
    try {
        setL(true); setG("Obteniendo ubicación..."); val fix = withContext(Dispatchers.IO) { gps.getBestFixStrict(50.0, 5000) }
        val id = vm.createWithFix(pId, fix.lat, fix.lon, fix.altM, fix.accM, fix.provider, fix.fixTime, fix.status, rN, comp, vE, dir, nts, rNum.toIntOrNull(), esfs, bS, bE, pN, vT, sC.toIntOrNull(), af, sup, dN, oS, cW)
        setL(false); onC(id)
    } catch (e: Exception) { setL(false); setE(e.message ?: "Error al crear.") }
}

@Preview(showBackground = true) @Composable fun AsdNewTripScreenPreview() { UrbanAppTheme { AsdNewTripContent(null, true, {}, { _, _ -> null }, { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> }, { true }) } }
