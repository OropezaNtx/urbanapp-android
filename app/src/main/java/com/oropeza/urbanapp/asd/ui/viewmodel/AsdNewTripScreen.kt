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
import com.oropeza.urbanapp.asd.AsdGraph
import com.oropeza.urbanapp.asd.data.local.AsdCatalogSyncState
import com.oropeza.urbanapp.asd.data.local.AsdFieldPersonCatalogItem
import com.oropeza.urbanapp.asd.data.local.AsdRouteCatalogItem
import com.oropeza.urbanapp.asd.data.local.AsdVehicleTypeCatalogItem
import com.oropeza.urbanapp.asd.importer.AsdCatalogXlsxImporter
import com.oropeza.urbanapp.asd.location.LocationProvider
import com.oropeza.urbanapp.asd.sync.AsdCatalogFirestoreSync
import com.oropeza.urbanapp.core.runtime.UrbanRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AsdNewTripScreen(
    onCreated: (Long) -> Unit,
    onBack: () -> Unit
) {
    val vm: AsdNewTripVM = viewModel()
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val gps = remember { LocationProvider(context) }
    val syncState by vm.syncState.collectAsState(initial = null)

    var planningRouteId by remember { mutableStateOf("") }
    var routeName by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    var baseStart by remember { mutableStateOf("") }
    var baseEnd by remember { mutableStateOf("") }
    var routeNumberTxt by remember { mutableStateOf("") }
    var direction by remember { mutableStateOf("IDA") }
    var esFs by remember { mutableStateOf("ES") }
    var startWpAtOne by remember { mutableStateOf(false) }
    var vehicleType by remember { mutableStateOf("COMBI") }
    var seatCapacityTxt by remember { mutableStateOf("") }
    var vehicleEco by remember { mutableStateOf("") }
    var plateNumber by remember { mutableStateOf("") }
    var aforador by remember { mutableStateOf("") }
    var observerSex by remember { mutableStateOf<String?>(null) }
    var supervisor by remember { mutableStateOf("") }
    var deviceNumber by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var gpsMsg by remember { mutableStateOf<String?>(null) }
    var pendingCreate by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val bgApp = Color(0xFF07110F)
    val greenAcc = Color(0xFF35D36B)

    LaunchedEffect(planningRouteId, direction) {
        if (planningRouteId.length >= 3) {
            val route = vm.getRoute(planningRouteId, direction)
            if (route != null) {
                routeName = if (route.derrotero.isNullOrBlank()) route.routeName else "${route.routeName} (${route.derrotero})"
                company = route.company ?: ""
                baseStart = route.baseStart ?: ""
                baseEnd = route.baseEnd ?: ""
            }
        }
    }

    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) ||
                (result[Manifest.permission.ACCESS_COARSE_LOCATION] == true)
        if (!granted) {
            pendingCreate = false
            error = "Se requiere permiso de ubicación para registrar coordenadas."
            return@rememberLauncherForActivityResult
        }
        if (pendingCreate) {
            pendingCreate = false
            scope.launch { createTripFlow(vm, gps,
                planningRouteId, routeName, company, vehicleEco, direction, notes,
                aforador, supervisor, deviceNumber, observerSex ?: "",
                routeNumberTxt, esFs, baseStart, baseEnd, plateNumber, vehicleType, seatCapacityTxt,
                !startWpAtOne, onCreated, { loading = it }, { gpsMsg = it }, { error = it }
            ) }
        }
    }

    fun requestPermsIfNeededAndCreateOrWait() {
        if (!gps.hasPermission()) {
            pendingCreate = true
            permLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } else {
            scope.launch {
                createTripFlow(vm, gps,
                    planningRouteId, routeName, company, vehicleEco, direction, notes,
                    aforador, supervisor, deviceNumber, observerSex ?: "",
                    routeNumberTxt, esFs, baseStart, baseEnd, plateNumber, vehicleType, seatCapacityTxt,
                    !startWpAtOne, onCreated, { loading = it }, { gpsMsg = it }, { error = it }
                )
            }
        }
    }

    val licenseStatus = remember { UrbanRuntime.licenseStatus(context) }
    val isLicenseActive = licenseStatus == com.oropeza.urbanapp.core.license.UrbanLicenseStatus.ACTIVE
    val canCreateTrip = !loading && isLicenseActive

    Scaffold(
        containerColor = bgApp,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgApp, titleContentColor = Color.White),
                title = { Text("NUEVO LEVANTAMIENTO", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    TextButton(onClick = { focusManager.clearFocus(); onBack() }) { 
                        Text("ATRÁS", color = Color.White) 
                    }
                }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!isLicenseActive) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = "Acción bloqueada: Licencia no activa. Contacte a soporte.",
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            NewTripSection("CATÁLOGO") {
                syncState?.let { state ->
                    val statusColor = if(state.status == "READY") greenAcc else Color.Red
                    Text(if(state.status == "READY") "Catálogo verificado ✅" else "Error en catálogo ❌", color = statusColor, fontWeight = FontWeight.Bold)
                }
                
                OutlinedTextField(
                    value = planningRouteId,
                    onValueChange = { planningRouteId = it.uppercase() },
                    label = { Text("ID DE PLANIFICACIÓN") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = asdTextFieldColors(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                )
            }

            NewTripSection("DETALLES DE RUTA") {
                RouteDataItem("NOMBRE DE RUTA", routeName)
                RouteDataItem("SENTIDO", direction)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { direction = "IDA" }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = if(direction=="IDA") greenAcc else Color.DarkGray)) { Text("IDA") }
                    Button(onClick = { direction = "VUELTA" }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = if(direction=="VUELTA") greenAcc else Color.DarkGray)) { Text("VUELTA") }
                }
            }

            NewTripSection("DATOS DEL OPERADOR") {
                OutlinedTextField(
                    value = aforador,
                    onValueChange = { aforador = it.uppercase() },
                    label = { Text("NOMBRE DEL OPERADOR") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = asdTextFieldColors()
                )
            }

            gpsMsg?.let { Text(it, color = greenAcc, style = MaterialTheme.typography.bodySmall) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            Button(
                enabled = canCreateTrip,
                onClick = {
                    focusManager.clearFocus()
                    if (planningRouteId.isBlank() || aforador.isBlank()) { 
                        error = "ID de Planificación y Operador son obligatorios."
                        return@Button 
                    }
                    requestPermsIfNeededAndCreateOrWait()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = greenAcc),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (loading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black)
                else Text("INICIAR LEVANTAMIENTO", fontWeight = FontWeight.ExtraBold, color = Color.Black)
            }
        }
    }
}

@Composable
private fun RouteDataItem(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF35D36B), fontWeight = FontWeight.Bold)
        Text(value.ifBlank { "—" }, style = MaterialTheme.typography.bodyLarge, color = Color.White)
    }
}

@Composable
private fun NewTripSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1716)),
        border = BorderStroke(1.dp, Color(0xFF223A36)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFF35D36B))
            content()
        }
    }
}

@Composable
private fun asdTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color(0xFF35D36B),
    unfocusedBorderColor = Color(0xFF223A36),
    focusedLabelColor = Color(0xFF35D36B),
    unfocusedLabelColor = Color.White.copy(alpha = 0.4f),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White
)

private suspend fun createTripFlow(
    vm: AsdNewTripVM,
    gps: LocationProvider,
    planningRouteId: String,
    routeName: String,
    company: String,
    vehicleEco: String,
    direction: String,
    notes: String,
    aforador: String,
    supervisor: String,
    deviceNumber: String,
    observerSex: String,
    routeNumberTxt: String,
    esFs: String,
    baseStart: String,
    baseEnd: String,
    plateNumber: String,
    vehicleType: String,
    seatCapacityTxt: String,
    continueWaypoints: Boolean,
    onCreated: (Long) -> Unit,
    setLoading: (Boolean) -> Unit,
    setGpsMsg: (String?) -> Unit,
    setError: (String?) -> Unit
) {
    try {
        setLoading(true)
        setGpsMsg("Obteniendo ubicación inicial...")
        val fix = withContext(Dispatchers.IO) { gps.getBestFixStrict(50.0, 5000) }
        
        val id = vm.createWithFix(
            planningRouteId, fix.lat, fix.lon, fix.altM, fix.accM, fix.provider, fix.fixTime, fix.status,
            routeName, company, vehicleEco, direction, notes, routeNumberTxt.toIntOrNull(), esFs, baseStart, baseEnd,
            plateNumber, vehicleType, seatCapacityTxt.toIntOrNull(), aforador, supervisor, deviceNumber, observerSex, continueWaypoints
        )
        setLoading(false)
        onCreated(id)
    } catch (e: Exception) {
        setLoading(false)
        setError(e.message ?: "Error al crear el levantamiento.")
    }
}
