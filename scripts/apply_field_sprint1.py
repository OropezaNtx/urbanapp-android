from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise RuntimeError(f"No se encontró el bloque esperado: {label}")
    return text.replace(old, new, 1)


# 1) Crear recorrido inmediatamente, sin esperar GPS.
p = ROOT / "app/src/main/java/com/oropeza/urbanapp/asd/ui/viewmodel/AsdNewTripScreen.kt"
text = p.read_text(encoding="utf-8-sig")
marker = "private suspend fun createTripFlow("
if marker not in text:
    raise RuntimeError("No se encontró createTripFlow")
prefix = text.split(marker, 1)[0]
replacement = '''private suspend fun createTripFlow(
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
'''
p.write_text(prefix + replacement, encoding="utf-8")

# 2) Guardar eventos inmediatamente con el último fix ya disponible.
p = ROOT / "app/src/main/java/com/oropeza/urbanapp/asd/ui/viewmodel/AsdTripDetailScreen.kt"
text = p.read_text(encoding="utf-8-sig")
start = text.find("    fun saveEvent() {")
end = text.find("\n    val exportCsv", start)
if start < 0 or end < 0:
    raise RuntimeError("No se encontró el bloque saveEvent")
new_save = '''    fun saveEvent() {
        val err = validateCapture(summary, trip)
        if (err != null) {
            scope.launch { snackbarHostState.showSnackbar(err) }
            return
        }

        val now = System.currentTimeMillis()
        val endFix = currentFix(now)
        val startFix = LocationFix(
            activeDelayLat,
            activeDelayLon,
            activeDelayAccM,
            activeDelayAltM,
            activeDelayProvider.ifBlank { "pending" },
            if (activeDelayFixTime > 0L) activeDelayFixTime else now,
            activeDelayStatus
        )

        scope.launch {
            loadingGps = true
            gpsMsg = if (endFix.status == "GPS_PENDING") {
                "Guardando localmente; ubicación pendiente…"
            } else {
                "Guardando registro…"
            }
            try {
                vm.addStopDetailed(
                    tripId,
                    if (menUp + womenUp + menDown + womenDown > 0) "ASD" else "DEMORA",
                    activeDelayStartMs,
                    now,
                    stopName.trim(),
                    notes.trim(),
                    menUp,
                    womenUp,
                    menDown,
                    womenDown,
                    hasLuggage,
                    selectedDelayCodes.joinToString("/").ifBlank { null },
                    otherDelayDesc.trim().ifBlank { null },
                    startFix,
                    endFix
                )
                resetCapture()
                snackbarHostState.showSnackbar(
                    if (endFix.status == "GPS_PENDING")
                        "Registro guardado localmente; GPS pendiente ✅"
                    else
                        "Registro guardado ✅"
                )
            } catch (t: Throwable) {
                snackbarHostState.showSnackbar(
                    "No se pudo guardar el registro: ${t.message ?: "error desconocido"}"
                )
            } finally {
                loadingGps = false
                gpsMsg = null
            }
        }
    }
'''
text = text[:start] + new_save + text[end:]

old_effect = "    LaunchedEffect(tripId, trip?.endTime) { if (trip?.endTime == null) startTS() else stopTS() }"
new_effect = '''    LaunchedEffect(tripId, trip?.endTime) {
        if (trip?.endTime == null) {
            startTS()
        } else {
            stopTS()
            snackbarHostState.showSnackbar("Recorrido finalizado ✅")
            onBack()
        }
    }'''
text = replace_once(text, old_effect, new_effect, "navegación al finalizar")
p.write_text(text, encoding="utf-8")

# 3) Ampliar ventana de backfill GPS.
p = ROOT / "app/src/main/java/com/oropeza/urbanapp/asd/data/repository/repository.kt"
text = p.read_text(encoding="utf-8-sig")
text = replace_once(
    text,
    "getPendingGpsEvents(tripId, limit = 10)",
    "getPendingGpsEvents(tripId, limit = 50)",
    "límite de eventos GPS pendientes",
)
text = replace_once(
    text,
    "if (diffMs <= 30_000L)",
    "if (diffMs <= 5 * 60_000L)",
    "ventana de backfill GPS",
)
p.write_text(text, encoding="utf-8")

print("Sprint 1 aplicado correctamente.")
print("Archivos modificados:")
print("- AsdNewTripScreen.kt")
print("- AsdTripDetailScreen.kt")
print("- repository.kt")
