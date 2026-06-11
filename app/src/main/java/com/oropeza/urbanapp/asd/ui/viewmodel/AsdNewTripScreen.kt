package com.oropeza.urbanapp.asd.ui.viewmodel

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oropeza.urbanapp.asd.AsdGraph
import com.oropeza.urbanapp.asd.location.LocationProvider
import kotlinx.coroutines.launch

class AsdNewTripVM : ViewModel() {

    suspend fun createWithFix(
        planningRouteId: String,
        stopLat: Double,
        stopLon: Double,
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
        deviceNumber: String? = null
    ): Long {
        return AsdGraph.repo.createTripWithStartFix(
            planningRouteId = planningRouteId,
            stopLat = stopLat,
            stopLon = stopLon,
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
            deviceNumber = deviceNumber
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

    // Campos principales
    var planningRouteId by remember { mutableStateOf("") }
    var routeName by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    var vehicleEco by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var direction by remember { mutableStateOf("IDA") }

    // Encabezado opcional
    var routeNumberTxt by remember { mutableStateOf("") }
    var esFs by remember { mutableStateOf("") }
    var baseStart by remember { mutableStateOf("") }
    var baseEnd by remember { mutableStateOf("") }
    var plateNumber by remember { mutableStateOf("") }
    var vehicleType by remember { mutableStateOf("") }
    var seatCapacityTxt by remember { mutableStateOf("") }
    var aforador by remember { mutableStateOf("") }
    var supervisor by remember { mutableStateOf("") }
    var deviceNumber by remember { mutableStateOf("") }

    // Estados UI
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var gpsMsg by remember { mutableStateOf<String?>(null) }

    // ✅ Para no tener que presionar "Crear" 2 veces tras aceptar permisos
    var pendingCreate by remember { mutableStateOf(false) }

    // Permisos
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

        // ✅ Si el usuario aceptó permisos y veníamos de intentar crear, reintenta automáticamente
        if (pendingCreate) {
            pendingCreate = false
            scope.launch {
                createTripFlow(
                    vm, gps,
                    planningRouteId, routeName, company, vehicleEco, direction, notes,
                    routeNumberTxt, esFs, baseStart, baseEnd, plateNumber, vehicleType, seatCapacityTxt,
                    aforador, supervisor, deviceNumber,
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
                    routeNumberTxt, esFs, baseStart, baseEnd, plateNumber, vehicleType, seatCapacityTxt,
                    aforador, supervisor, deviceNumber,
                    onCreated = onCreated,
                    setLoading = { loading = it },
                    setGpsMsg = { gpsMsg = it },
                    setError = { error = it }
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nuevo recorrido ASD") },
                navigationIcon = {
                    TextButton(onClick = {
                        focusManager.clearFocus()
                        onBack()
                    }) { Text("Atrás") }
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            OutlinedTextField(
                value = planningRouteId,
                onValueChange = { planningRouteId = it.trim() },
                label = { Text("ID Planeación *") },
                supportingText = { Text("Obligatorio. ID fijo asignado a la ruta desde planeación.") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = routeName,
                onValueChange = { routeName = it },
                label = { Text("Ruta / Derrotero *") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = company,
                onValueChange = { company = it },
                label = { Text("Empresa (opcional)") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = vehicleEco,
                onValueChange = { vehicleEco = it },
                label = { Text("No. Económico (opcional)") },
                modifier = Modifier.fillMaxWidth()
            )

            Text("Sentido", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                    selected = direction == "IDA",
                    onClick = { direction = "IDA" },
                    label = { Text("IDA") }
                )
                FilterChip(
                    selected = direction == "REGRESO",
                    onClick = { direction = "REGRESO" },
                    label = { Text("REGRESO") }
                )
            }

            Divider()

            Text("Campos operativos", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = aforador,
                onValueChange = { aforador = it },
                label = { Text("Aforador") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = supervisor,
                onValueChange = { supervisor = it },
                label = { Text("Supervisor") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = deviceNumber,
                onValueChange = { deviceNumber = it },
                label = { Text("No. Dispositivo") },
                modifier = Modifier.fillMaxWidth()
            )

            Divider()

            Text("Encabezado (opcional, pero recomendado)", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = routeNumberTxt,
                onValueChange = { routeNumberTxt = it.filter { ch -> ch.isDigit() }.take(6) },
                label = { Text("No. Recorrido") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = esFs,
                onValueChange = { esFs = it },
                label = { Text("ES / FS") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = baseStart,
                onValueChange = { baseStart = it },
                label = { Text("Base de inicio") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = baseEnd,
                onValueChange = { baseEnd = it },
                label = { Text("Base final") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = plateNumber,
                onValueChange = { plateNumber = it },
                label = { Text("No. Placa") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = vehicleType,
                onValueChange = { vehicleType = it },
                label = { Text("Tipo de vehículo") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = seatCapacityTxt,
                onValueChange = { seatCapacityTxt = it.filter { ch -> ch.isDigit() }.take(4) },
                label = { Text("Capacidad de asientos") },
                modifier = Modifier.fillMaxWidth()
            )

            Divider()

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notas / Observaciones (opcional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            gpsMsg?.let { Text(it) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Spacer(Modifier.height(8.dp))

            Button(
                enabled = !loading,
                onClick = {
                    focusManager.clearFocus()
                    error = null
                    gpsMsg = null

                    val pid = planningRouteId.trim()
                    if (pid.isBlank()) {
                        error = "El ID de Planeación es obligatorio."
                        return@Button
                    }

                    val rn = routeName.trim()
                    if (rn.isBlank()) {
                        error = "La ruta/derrotero es obligatorio."
                        return@Button
                    }

                    requestPermsIfNeededAndCreateOrWait()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Creando...")
                } else {
                    Text("Crear recorrido")
                }
            }

            Spacer(Modifier.height(30.dp))
        }
    }
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
    routeNumberTxt: String,
    esFs: String,
    baseStart: String,
    baseEnd: String,
    plateNumber: String,
    vehicleType: String,
    seatCapacityTxt: String,
    aforador: String,
    supervisor: String,
    deviceNumber: String,
    onCreated: (Long) -> Unit,
    setLoading: (Boolean) -> Unit,
    setGpsMsg: (String?) -> Unit,
    setError: (String?) -> Unit
) {
    try {
        setLoading(true)
        setError(null)

        // ✅ Intento rápido primero (reduce frustración)
        setGpsMsg("Tomando ubicación (rápido)…")
        val quick = gps.getQuickFix(highAccuracy = true)

        // Si tenemos algo rápido, avanzamos con eso (aunque no sea perfecto)
        if (quick != null) {
            val acc = quick.accuracy.toDouble()
            val status = if (acc <= 10.0) "FIX_OK" else "FIX_USABLE"

            setGpsMsg(
                if (status == "FIX_OK") "GPS OK: ${acc.toInt()}m ✅"
                else "GPS usable: ${acc.toInt()}m (continuando) ✅"
            )

            val id = vm.createWithFix(
                planningRouteId = planningRouteId.trim(),
                stopLat = quick.latitude,
                stopLon = quick.longitude,
                stopAccM = acc,
                stopProvider = quick.provider ?: "fused",
                stopFixTime = if (quick.time > 0L) quick.time else System.currentTimeMillis(),
                locationStatus = status,
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
                deviceNumber = deviceNumber.ifBlank { null }
            )

            setLoading(false)
            onCreated(id)
            return
        }

        // ✅ Si no hubo quick fix, hacemos soft fix (pero sin bloquear indefinidamente)
        setGpsMsg("Buscando GPS (objetivo ≤10m; usable ≤25m)…")
        val fix = gps.getBestFixForEvent(
            targetAccM = 10.0,
            fallbackAccM = 25.0,
            timeoutMs = 10_000L
        )

        // ✅ CAMBIO CLAVE: si NO_FIX, no bloqueamos el inicio. Creamos el viaje sin coordenadas.
        if (fix.status == "NO_FIX") {
            setGpsMsg("Sin GPS por ahora (se creó el recorrido). Al iniciar tracking se seguirá ajustando señal… ⚠️")

            val id = vm.createWithFix(
                planningRouteId = planningRouteId.trim(),
                stopLat = 0.0,
                stopLon = 0.0,
                stopAccM = fix.accM,
                stopProvider = "none",
                stopFixTime = System.currentTimeMillis(),
                locationStatus = "NO_FIX",
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
                deviceNumber = deviceNumber.ifBlank { null }
            )

            setLoading(false)
            onCreated(id)
            return
        }

        setGpsMsg(
            when (fix.status) {
                "FIX_OK" -> "GPS OK: ${fix.accM.toInt()}m ✅"
                "FIX_USABLE" -> "GPS usable: ${fix.accM.toInt()}m (continuando) ✅"
                else -> "GPS: ${fix.accM.toInt()}m ✅"
            }
        )

        val id = vm.createWithFix(
            planningRouteId = planningRouteId.trim(),
            stopLat = fix.lat,
            stopLon = fix.lon,
            stopAccM = fix.accM,
            stopProvider = fix.provider,
            stopFixTime = fix.fixTime,
            locationStatus = fix.status,
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
            deviceNumber = deviceNumber.ifBlank { null }
        )

        setLoading(false)
        onCreated(id)

    } catch (e: Exception) {
        setLoading(false)
        setGpsMsg(null)
        setError(e.message ?: "Error al crear el recorrido.")
    }
}
