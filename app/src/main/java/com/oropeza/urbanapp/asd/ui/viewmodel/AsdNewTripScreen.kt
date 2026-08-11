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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oropeza.urbanapp.asd.AsdGraph
import com.oropeza.urbanapp.asd.data.local.StopEvent
import com.oropeza.urbanapp.asd.data.local.TrackPoint
import com.oropeza.urbanapp.asd.data.local.Trip
import com.oropeza.urbanapp.core.events.UrbanEventFactory
import com.oropeza.urbanapp.core.events.UrbanEventTypes
import com.oropeza.urbanapp.core.runtime.UrbanRuntime
import com.oropeza.urbanapp.asd.importer.AsdCatalogXlsxImporter
import com.oropeza.urbanapp.asd.sync.AsdCatalogFirestoreSync
import com.oropeza.urbanapp.asd.location.LocationProvider
import com.oropeza.urbanapp.ui.theme.LocalAforaColors
import com.oropeza.urbanapp.ui.theme.LocalAforaTypography
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AsdNewTripVM : ViewModel() {
    val syncState = AsdGraph.repo.catalogSyncStateFlow()
    val vehicleTypesCatalog = AsdGraph.repo.activeAsdVehicleTypesFlow()
    fun peopleByRole(role: String) = AsdGraph.repo.activeAsdPeopleByRoleFlow(role)
    suspend fun getRoute(routeId: String, direction: String) = AsdGraph.repo.getAsdRouteByCatalogIdAndDirection(routeId, direction)
    suspend fun getLastTripNextWaypoint() = AsdGraph.repo.getLastTripNextWaypoint()

    suspend fun createWithFix(
        planningRouteId: String,
        stopLat: Double,
        stopLon: Double,
        stopAltM: Double = 0.0,
        stopAccM: Double,
        stopProvider: String,
        stopFixTime: Long,
        locationStatus: String,
        routeName: String,
        company: String?,
        vehicleEco: String?,
        direction: String,
        notes: String?,
        routeNumber: Int? = null,
        esFs: String? = null,
        baseStart: String? = null,
        baseEnd: String? = null,
        plateNumber: String? = null,
        vehicleType: String? = null,
        seatCapacity: Int? = null,
        aforador: String? = null,
        supervisor: String? = null,
        deviceNumber: String? = null,
        observerSex: String? = null,
        continueWaypoints: Boolean = false
    ): Long {
        return vmCreate(
            planningRouteId = planningRouteId,
            stopLat = stopLat,
            stopLon = stopLon,
            stopAltM = stopAltM,
            stopAccM = stopAccM,
            stopProvider = stopProvider,
            stopFixTime = stopFixTime,
            locationStatus = locationStatus,
            routeName = routeName,
            company = company,
            vehicleEco = vehicleEco,
            direction = direction,
            notes = notes,
            routeNumber = routeNumber,
            esFs = esFs,
            baseStart = baseStart,
            baseEnd = baseEnd,
            plateNumber = plateNumber,
            vehicleType = vehicleType,
            seatCapacity = seatCapacity,
            aforador = aforador,
            supervisor = supervisor,
            deviceNumber = deviceNumber,
            observerSex = observerSex,
            continueWaypoints = continueWaypoints
        )
    }

    private suspend fun vmCreate(
        planningRouteId: String,
        stopLat: Double,
        stopLon: Double,
        stopAltM: Double = 0.0,
        stopAccM: Double,
        stopProvider: String,
        stopFixTime: Long,
        locationStatus: String,
        routeName: String,
        company: String?,
        vehicleEco: String?,
        direction: String,
        notes: String?,
        routeNumber: Int? = null,
        esFs: String? = null,
        baseStart: String? = null,
        baseEnd: String? = null,
        plateNumber: String? = null,
        vehicleType: String? = null,
        seatCapacity: Int? = null,
        aforador: String? = null,
        supervisor: String? = null,
        deviceNumber: String? = null,
        observerSex: String? = null,
        continueWaypoints: Boolean = false
    ): Long {
        val id = AsdGraph.repo.createTripWithStartFix(
            planningRouteId = planningRouteId,
            stopLat = stopLat,
            stopLon = stopLon,
            stopAltM = stopAltM,
            stopAccM = stopAccM,
            stopProvider = stopProvider,
            stopFixTime = stopFixTime,
            locationStatus = locationStatus,
            routeName = routeName,
            company = company,
            vehicleEco = vehicleEco,
            direction = direction,
            notes = notes,
            routeNumber = routeNumber,
            esFs = esFs,
            baseStart = baseStart,
            baseEnd = baseEnd,
            plateNumber = plateNumber,
            vehicleType = vehicleType,
            seatCapacity = seatCapacity,
            aforador = aforador,
            supervisor = supervisor,
            deviceNumber = deviceNumber,
            observerSex = observerSex,
            continueWaypoints = continueWaypoints
        )

        if (id > 0) {
            UrbanRuntime.publishEvent(
                UrbanEventFactory.asd(
                    UrbanEventTypes.ASD_TRIP_CREATED,
                    mapOf("tripId" to id, "routeName" to routeName)
                )
            )
        }

        return id
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
    val colors = LocalAforaColors.current
    val typography = LocalAforaTypography.current
    val gps = remember { LocationProvider(context) }
    val syncState by vm.syncState.collectAsState(initial = null)

    // 1. CATÁLOGO
    var planningRouteId by remember { mutableStateOf("") }
    var catalogStatus by remember { mutableStateOf<String?>(null) }

    // 2. DATOS AUTOLLENADOS
    var routeName by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    var baseStart by remember { mutableStateOf("") }
    var baseEnd by remember { mutableStateOf("") }

    // 3. OPERACIÓN
    var routeNumberTxt by remember { mutableStateOf("") }
    var direction by remember { mutableStateOf("IDA") }
    var esFs by remember { mutableStateOf("ES") }
    var startWpAtOne by remember { mutableStateOf(false) }
    var customRouteNumber by remember { mutableStateOf(false) }

    var debugLastWp by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) {
        debugLastWp = vm.getLastTripNextWaypoint()
    }

    LaunchedEffect(Unit) {
        val calendar = Calendar.getInstance()
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        esFs = if (dayOfWeek in Calendar.MONDAY..Calendar.FRIDAY) "ES" else "FS"
    }

    // 4. UNIDAD
    val vehicleTypesCatalog by vm.vehicleTypesCatalog.collectAsState(initial = emptyList())
    var vehicleType by remember { mutableStateOf("COMBI") }
    var seatCapacityTxt by remember { mutableStateOf("") }
    var vehicleEco by remember { mutableStateOf("") }
    var plateNumber by remember { mutableStateOf("") }

    // 5. PERSONAL
    var aforador by remember { mutableStateOf("") }
    var observerSex by remember { mutableStateOf<String?>(null) }
    var supervisor by remember { mutableStateOf("") }
    var deviceNumber by remember { mutableStateOf("") }

    val observers by vm.peopleByRole("OBSERVADOR").collectAsState(initial = emptyList())
    val supervisors by vm.peopleByRole("SUPERVISOR").collectAsState(initial = emptyList())

    // 6. NOTAS
    var notes by remember { mutableStateOf("") }

    // Estados UI
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var gpsMsg by remember { mutableStateOf<String?>(null) }
    var importMsg by remember { mutableStateOf<String?>(null) }
    var pendingCreate by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val bgApp = colors.Background
    val cardBg = colors.Surface
    val borderCol = colors.Outline
    val primaryAcc = colors.Primary
    val textPrimary = colors.Secondary
    val textSecondary = colors.Secondary.copy(alpha = 0.62f)

    val vehicleTypesFallback = listOf(
        "COMBI", "VAN", "SPRINTER", "MICROBUS", "MIDIBUS", "BUS URBANO",
        "BUS FORANEO", "ARTICULADO", "TROLEBUS", "METROBUS",
        "TAXI COLECTIVO", "CAMIONETA", "OTRO"
    )

    LaunchedEffect(planningRouteId, direction) {
        if (planningRouteId.length >= 3) {
            val route = vm.getRoute(planningRouteId, direction)
            if (route != null) {
                routeName = if (route.derrotero.isNullOrBlank()) route.routeName else "${route.routeName} (${route.derrotero})"
                company = route.company ?: ""
                baseStart = route.baseStart ?: ""
                baseEnd = route.baseEnd ?: ""
                catalogStatus = "Catálogo encontrado ✅"
            } else {
                catalogStatus = "ID/Sentido no encontrado en catálogo ⚠️"
            }
        } else {
            catalogStatus = null
        }
    }

    val catalogLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                loading = true
                val result = AsdCatalogXlsxImporter.importFromUri(context, it)
                loading = false
                importMsg = "Catálogo importado: ${result.routesImported} rutas, ${result.peopleImported} personas."
                if (result.warnings.isNotEmpty()) {
                    importMsg += " Con ${result.warnings.size} advertencias."
                }
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
            scope.launch {
                createTripFlow(
                    vm, gps,
                    planningRouteId, routeName, company, vehicleEco, direction, notes,
                    aforador, supervisor, deviceNumber, observerSex ?: "",
                    routeNumberTxt, esFs, baseStart, baseEnd, plateNumber, vehicleType, seatCapacityTxt,
                    !startWpAtOne,
                    onCreated = onCreated,
                    setLoading = { loading = it },
                    setGpsMsg = { gpsMsg = it },
                    setError = { error = it }
                )
            }
        }
    }

    fun requestPermsIfNeededAndCreateOrWait() {
        if (!gps.hasPermission()) {
            pendingCreate = true
            permLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            scope.launch {
                createTripFlow(
                    vm, gps,
                    planningRouteId, routeName, company, vehicleEco, direction, notes,
                    aforador, supervisor, deviceNumber, observerSex ?: "",
                    routeNumberTxt, esFs, baseStart, baseEnd, plateNumber, vehicleType, seatCapacityTxt,
                    !startWpAtOne,
                    onCreated = onCreated,
                    setLoading = { loading = it },
                    setGpsMsg = { gpsMsg = it },
                    setError = { error = it }
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.Surface,
                    titleContentColor = colors.Secondary
                ),
                title = {
                    Text(
                        "NUEVO RECORRIDO ASD",
                        style = typography.Headline,
                        fontWeight = FontWeight.Black
                    )
                },
                navigationIcon = {
                    TextButton(onClick = { focusManager.clearFocus(); onBack() }) {
                        Text("ATRÁS", color = colors.Primary, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!isLicenseActive) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = colors.Danger.copy(alpha = 0.08f)),
                    border = BorderStroke(1.dp, colors.Danger.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Acción bloqueada: Licencia no válida (${licenseStatus.name}). Por favor contacte a soporte para activar su dispositivo.",
                        modifier = Modifier.padding(12.dp),
                        color = colors.Danger,
                        style = typography.BodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            NewTripSection("CATÁLOGO") {
                syncState?.let { state ->
                    val statusColor = when (state.status) {
                        "READY" -> colors.Success
                        "ERROR" -> colors.Danger
                        else -> colors.Offline
                    }
                    val statusText = when (state.status) {
                        "READY" -> "Catálogo listo ✅"
                        "ERROR" -> "Error en catálogo ❌"
                        else -> "Sin catálogo local ⚠️"
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            statusText,
                            style = typography.BodySmall,
                            color = statusColor,
                            fontWeight = FontWeight.Bold
                        )
                        if (state.lastSyncAt != null) {
                            val syncFmt = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault())
                            Text(
                                "Última actualización: ${syncFmt.format(Date(state.lastSyncAt))}",
                                style = typography.BodySmall,
                                color = textSecondary
                            )
                        }
                        Text(
                            "Rutas: ${state.routesCount} · Personas: ${state.peopleCount} · Unidades: ${state.vehicleTypesCount}",
                            style = typography.BodySmall,
                            color = textSecondary
                        )
                    }

                    HorizontalDivider(color = borderCol.copy(alpha = 0.6f))
                }

                if (syncState == null || syncState?.status != "READY") {
                    Text(
                        "No hay catálogo local disponible. Conecta el dispositivo a internet y sincroniza al menos una vez antes de iniciar operación.",
                        color = colors.Warning,
                        style = typography.BodySmall,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                OutlinedTextField(
                    value = planningRouteId,
                    onValueChange = { planningRouteId = it.uppercase() },
                    label = { Text("ID CATÁLOGO / PLANEACIÓN") },
                    supportingText = { Text("ID único de planeación.") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = asdTextFieldColors(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                )

                Button(
                    enabled = !loading,
                    onClick = {
                        scope.launch {
                            loading = true
                            val result = AsdCatalogFirestoreSync.syncFromFirestore()
                            loading = false
                            result.onSuccess {
                                snackbarHostState.showSnackbar(
                                    "Catálogo web sincronizado: ${it.routesCount} rutas, ${it.peopleCount} personas, ${it.vehicleTypesCount} unidades."
                                )
                            }.onFailure {
                                snackbarHostState.showSnackbar("Error: ${it.message}")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryAcc,
                        contentColor = colors.OnPrimary
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        "SINCRONIZAR WEB",
                        style = typography.Label,
                        fontWeight = FontWeight.Bold
                    )
                }

                catalogStatus?.let {
                    Text(
                        it,
                        style = typography.Label,
                        color = if (it.contains("encontrado ✅")) colors.Success else colors.Warning
                    )
                }

                var advancedExpanded by remember { mutableStateOf(false) }
                Column {
                    TextButton(
                        onClick = { advancedExpanded = !advancedExpanded },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(
                            if (advancedExpanded) "OCULTAR OPCIONES AVANZADAS" else "MOSTRAR OPCIONES AVANZADAS",
                            style = typography.Label,
                            color = textSecondary
                        )
                        Icon(
                            imageVector = if (advancedExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    if (advancedExpanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    catalogLauncher.launch(
                                        arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                border = BorderStroke(1.dp, borderCol),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    "IMPORTAR CATÁLOGO LOCAL (XLSX)",
                                    color = textPrimary,
                                    style = typography.Label
                                )
                            }
                            Text(
                                "Nota: Usar solo como respaldo cuando no sea posible sincronizar desde el servidor.",
                                style = typography.Label,
                                color = textSecondary,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
            }

            NewTripSection("DATOS DE RUTA") {
                if (routeName.isBlank()) {
                    Text(
                        "Ingresa ID y sentido para cargar datos de ruta.",
                        style = typography.BodyLarge,
                        color = textSecondary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        RouteDataItem("RUTA / DERROTERO", routeName)
                        RouteDataItem("EMPRESA", company.ifBlank { "NO ESPECIFICADA" })
                        RouteDataItem("BASES", "$baseStart  →  $baseEnd")
                    }
                }
            }

            NewTripSection("OPERACIÓN") {
                Text(
                    "NO. RECORRIDO",
                    style = typography.BodySmall,
                    color = textPrimary,
                    fontWeight = FontWeight.Bold
                )

                if (!customRouteNumber) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            (1..5).forEach { n ->
                                val selected = routeNumberTxt == n.toString()
                                OutlinedButton(
                                    onClick = { routeNumberTxt = n.toString() },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (selected) primaryAcc.copy(alpha = 0.10f) else cardBg,
                                        contentColor = if (selected) primaryAcc else textSecondary
                                    ),
                                    border = BorderStroke(1.dp, if (selected) primaryAcc else borderCol),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(
                                        n.toString(),
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            (6..10).forEach { n ->
                                val selected = routeNumberTxt == n.toString()
                                OutlinedButton(
                                    onClick = { routeNumberTxt = n.toString() },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (selected) primaryAcc.copy(alpha = 0.10f) else cardBg,
                                        contentColor = if (selected) primaryAcc else textSecondary
                                    ),
                                    border = BorderStroke(1.dp, if (selected) primaryAcc else borderCol),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(
                                        n.toString(),
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = routeNumberTxt,
                        onValueChange = { routeNumberTxt = it.filter { ch -> ch.isDigit() }.take(6) },
                        label = { Text("INGRESA NÚMERO") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        colors = asdTextFieldColors()
                    )
                }

                TextButton(
                    onClick = { customRouteNumber = !customRouteNumber },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        if (customRouteNumber) "Volver a rápidos" else "Ingresar personalizado",
                        color = primaryAcc,
                        style = typography.Label
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "SENTIDO",
                        modifier = Modifier.weight(1f),
                        style = typography.BodySmall,
                        color = textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    FilterChip(
                        selected = direction == "IDA",
                        onClick = { direction = "IDA" },
                        label = { Text("IDA") },
                        colors = asdChipColors()
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = direction == "REGRESO",
                        onClick = { direction = "REGRESO" },
                        label = { Text("REGRESO") },
                        colors = asdChipColors()
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "ES / FS",
                        modifier = Modifier.weight(1f),
                        style = typography.BodySmall,
                        color = textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    FilterChip(
                        selected = esFs == "ES",
                        onClick = { esFs = "ES" },
                        label = { Text("ES") },
                        colors = asdChipColors()
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = esFs == "FS",
                        onClick = { esFs = "FS" },
                        label = { Text("FS") },
                        colors = asdChipColors()
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = if (startWpAtOne) "REINICIAR WP EN 1" else "CONTINUAR WP ANTERIOR",
                            style = typography.BodySmall,
                            color = textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (startWpAtOne) {
                                "El recorrido iniciará en WP 1."
                            } else {
                                "El recorrido continuará la secuencia global (Siguiente: ${debugLastWp ?: 1})."
                            },
                            style = typography.BodySmall,
                            color = textSecondary
                        )
                    }
                    Switch(
                        checked = startWpAtOne,
                        onCheckedChange = { startWpAtOne = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.OnPrimary,
                            checkedTrackColor = primaryAcc
                        )
                    )
                }

                if (com.oropeza.urbanapp.BuildConfig.DEBUG) {
                    val wpBase = if (startWpAtOne) 1 else (debugLastWp ?: 1)
                    Text(
                        text = "DIAGNÓSTICO WP: Modo ${if (startWpAtOne) "REINICIAR" else "CONTINUAR"} | Inicio: $wpBase",
                        color = colors.Information,
                        style = typography.Label
                    )
                }
            }

            NewTripSection("UNIDAD") {
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = vehicleType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("TIPO DE VEHÍCULO") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        colors = asdTextFieldColors()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        if (vehicleTypesCatalog.isEmpty()) {
                            vehicleTypesFallback.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type) },
                                    onClick = {
                                        vehicleType = type
                                        expanded = false
                                    }
                                )
                            }
                        } else {
                            vehicleTypesCatalog.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(item.displayName) },
                                    onClick = {
                                        vehicleType = item.name
                                        if (seatCapacityTxt.isBlank()) {
                                            seatCapacityTxt = item.defaultSeatCapacity.toString()
                                        }
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = seatCapacityTxt,
                    onValueChange = { seatCapacityTxt = it.filter { ch -> ch.isDigit() }.take(4) },
                    label = { Text("CAPACIDAD DE ASIENTOS") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    colors = asdTextFieldColors()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = vehicleEco,
                        onValueChange = { vehicleEco = it.filter { ch -> ch.isDigit() }.take(6) },
                        label = { Text("NO. ECONÓMICO") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Right) }
                        ),
                        modifier = Modifier.weight(1f),
                        colors = asdTextFieldColors()
                    )
                    OutlinedTextField(
                        value = plateNumber,
                        onValueChange = { plateNumber = it.uppercase() },
                        label = { Text("NO. PLACA") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        modifier = Modifier.weight(1f),
                        colors = asdTextFieldColors()
                    )
                }
            }

            NewTripSection("PERSONAL") {
                var obsExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = obsExpanded,
                    onExpandedChange = { obsExpanded = it }
                ) {
                    OutlinedTextField(
                        value = aforador,
                        onValueChange = { aforador = it.uppercase() },
                        readOnly = observers.isNotEmpty(),
                        label = { Text("AFORADOR / OBSERVADOR *") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = obsExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        colors = asdTextFieldColors(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        )
                    )
                    if (observers.isNotEmpty()) {
                        ExposedDropdownMenu(
                            expanded = obsExpanded,
                            onDismissRequest = { obsExpanded = false }
                        ) {
                            observers.forEach { person ->
                                DropdownMenuItem(
                                    text = { Text(person.name) },
                                    onClick = {
                                        aforador = person.name
                                        person.defaultSex?.let { observerSex = it }
                                        obsExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Text(
                    "SEXO DEL OBSERVADOR *",
                    style = typography.BodySmall,
                    color = textPrimary,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(
                        selected = observerSex == "H",
                        onClick = { observerSex = "H" },
                        label = { Text("HOMBRE") },
                        colors = asdChipColors(),
                        leadingIcon = if (observerSex == "H") {
                            { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }
                        } else null
                    )
                    FilterChip(
                        selected = observerSex == "M",
                        onClick = { observerSex = "M" },
                        label = { Text("MUJER") },
                        colors = asdChipColors(),
                        leadingIcon = if (observerSex == "M") {
                            { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }
                        } else null
                    )
                }

                var supExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = supExpanded,
                    onExpandedChange = { supExpanded = it }
                ) {
                    OutlinedTextField(
                        value = supervisor,
                        onValueChange = { supervisor = it.uppercase() },
                        readOnly = supervisors.isNotEmpty(),
                        label = { Text("SUPERVISOR") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = supExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        colors = asdTextFieldColors(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        )
                    )
                    if (supervisors.isNotEmpty()) {
                        ExposedDropdownMenu(
                            expanded = supExpanded,
                            onDismissRequest = { supExpanded = false }
                        ) {
                            supervisors.forEach { person ->
                                DropdownMenuItem(
                                    text = { Text(person.name) },
                                    onClick = {
                                        supervisor = person.name
                                        supExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = deviceNumber,
                    onValueChange = { deviceNumber = it.uppercase() },
                    label = { Text("ID DISPOSITIVO") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = asdTextFieldColors(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    )
                )
            }

            NewTripSection("NOTAS") {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it.uppercase() },
                    label = { Text("OBSERVACIONES DE CAMPO") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    colors = asdTextFieldColors(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                )
            }

            importMsg?.let {
                Text(it, color = colors.Information, style = typography.BodySmall)
            }
            gpsMsg?.let {
                Text(it, color = colors.Success, style = typography.BodySmall)
            }
            error?.let {
                Text(it, color = colors.Danger, style = typography.BodySmall)
            }

            Spacer(Modifier.height(8.dp))

            Button(
                enabled = canCreateTrip,
                onClick = {
                    focusManager.clearFocus()
                    error = null
                    gpsMsg = null

                    if (planningRouteId.isBlank()) {
                        error = "El ID de Planeación es obligatorio."
                        return@Button
                    }
                    if (routeName.isBlank()) {
                        error = "La ruta/derrotero es obligatoria."
                        return@Button
                    }
                    if (aforador.isBlank()) {
                        error = "El nombre del observador es obligatorio."
                        return@Button
                    }
                    if (observerSex == null) {
                        error = "Selecciona el sexo del observador."
                        return@Button
                    }

                    requestPermsIfNeededAndCreateOrWait()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = primaryAcc,
                    contentColor = colors.OnPrimary,
                    disabledContainerColor = colors.Disabled,
                    disabledContentColor = colors.Offline
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = colors.OnPrimary
                    )
                } else {
                    Text(
                        "CREAR RECORRIDO",
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.OnPrimary
                    )
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun RouteDataItem(label: String, value: String) {
    val colors = LocalAforaColors.current
    val typography = LocalAforaTypography.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = typography.Label,
            color = colors.Primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value.ifBlank { "—" },
            style = typography.BodyLarge,
            color = colors.Secondary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun NewTripSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalAforaColors.current
    val typography = LocalAforaTypography.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.Surface),
        border = BorderStroke(1.dp, colors.Outline.copy(alpha = 0.65f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                title,
                style = typography.Label,
                fontWeight = FontWeight.ExtraBold,
                color = colors.Primary
            )
            content()
        }
    }
}

@Composable
private fun asdTextFieldColors() = LocalAforaColors.current.let { colors ->
    OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colors.Primary,
        unfocusedBorderColor = colors.Outline,
        focusedLabelColor = colors.Primary,
        unfocusedLabelColor = colors.Secondary.copy(alpha = 0.60f),
        focusedTextColor = colors.Secondary,
        unfocusedTextColor = colors.Secondary,
        cursorColor = colors.Primary,
        focusedSupportingTextColor = colors.Secondary.copy(alpha = 0.62f),
        unfocusedSupportingTextColor = colors.Secondary.copy(alpha = 0.62f),
        focusedContainerColor = colors.Surface,
        unfocusedContainerColor = colors.Surface
    )
}

@Composable
private fun asdChipColors() = LocalAforaColors.current.let { colors ->
    FilterChipDefaults.filterChipColors(
        selectedContainerColor = colors.Primary.copy(alpha = 0.10f),
        selectedLabelColor = colors.Primary,
        selectedLeadingIconColor = colors.Primary,
        labelColor = colors.Secondary.copy(alpha = 0.72f),
        containerColor = colors.Surface
    )
}

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
        setError(null)
        setGpsMsg("Recorrido creado. GPS pendiente de adquisición…")

        val now = System.currentTimeMillis()
        val id = vm.createWithFix(
            planningRouteId = planningRouteId.trim(),
            stopLat = 0.0,
            stopLon = 0.0,
            stopAltM = 0.0,
            stopAccM = 0.0,
            stopProvider = "pending",
            stopFixTime = now,
            locationStatus = "GPS_PENDING",
            routeName = routeName.trim(),
            company = company.ifBlank { null },
            vehicleEco = vehicleEco.ifBlank { null },
            direction = direction,
            notes = notes.ifBlank { null },
            routeNumber = routeNumberTxt.trim().toIntOrNull(),
            esFs = esFs.ifBlank { null },
            baseStart = baseStart.ifBlank { null },
            baseEnd = baseEnd.ifBlank { null },
            plateNumber = plateNumber.ifBlank { null },
            vehicleType = vehicleType.ifBlank { null },
            seatCapacity = seatCapacityTxt.trim().toIntOrNull(),
            aforador = aforador.ifBlank { null },
            supervisor = supervisor.ifBlank { null },
            deviceNumber = deviceNumber.ifBlank { null },
            observerSex = observerSex,
            continueWaypoints = continueWaypoints
        )

        setLoading(false)
        onCreated(id)
    } catch (e: Exception) {
        setLoading(false)
        setGpsMsg(null)
        setError(e.message ?: "Error al crear el recorrido.")
    }
}
