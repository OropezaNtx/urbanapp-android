package com.oropeza.urbanapp.cc.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

data class CcEventDraft(
    val eventType: String,          // "LLEGADA" o "SALIDA"
    val plate: String,
    val eco: String,
    val vehicleType: String,
    val pax: Int,
    val luggage: Int?,
    val timeMs: Long,
    val timeIsManual: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CcQuickCapturePanel(
    base: String,
    mode: String,                      // ✅ modo actual (se mantiene)
    onModeChange: (String) -> Unit,

    plate: String,
    onPlateChange: (String) -> Unit,
    eco: String,
    onEcoChange: (String) -> Unit,
    vehicleType: String,
    onVehicleTypeChange: (String) -> Unit,
    paxStr: String,
    onPaxChange: (String) -> Unit,
    luggageStr: String,
    onLuggageChange: (String) -> Unit,

    onSave: (CcEventDraft) -> Unit
) {
    val scope = rememberCoroutineScope()
    val plateFocus = remember { FocusRequester() }

    // Hora manual
    var useManualTime by remember { mutableStateOf(false) }
    val nowCal = remember { Calendar.getInstance() }
    var hh by remember { mutableStateOf(nowCal.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')) }
    var mm by remember { mutableStateOf(nowCal.get(Calendar.MINUTE).toString().padStart(2, '0')) }
    var ss by remember { mutableStateOf(nowCal.get(Calendar.SECOND).toString().padStart(2, '0')) }

    // Anti-doble-tap
    var clickLocked by remember { mutableStateOf(false) }
    suspend fun lockBriefly() {
        clickLocked = true
        delay(650L)
        clickLocked = false
    }

    fun manualTimeMs(): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, (hh.toIntOrNull() ?: 0).coerceIn(0, 23))
            set(Calendar.MINUTE, (mm.toIntOrNull() ?: 0).coerceIn(0, 59))
            set(Calendar.SECOND, (ss.toIntOrNull() ?: 0).coerceIn(0, 59))
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun submit() {
        if (clickLocked) return

        val pax = paxStr.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val luggage = if (base.uppercase(Locale.ROOT) == "PERIFERIA")
            (luggageStr.toIntOrNull()?.coerceAtLeast(0))
        else null

        val timeMs = if (useManualTime) manualTimeMs() else System.currentTimeMillis()

        scope.launch { lockBriefly() }

        onSave(
            CcEventDraft(
                eventType = mode,
                plate = plate.trim().uppercase(Locale.ROOT),
                eco = eco.trim(),
                vehicleType = vehicleType.trim(),
                pax = pax,
                luggage = luggage,
                timeMs = timeMs,
                timeIsManual = useManualTime
            )
        )
    }

    LaunchedEffect(Unit) { plateFocus.requestFocus() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Modo (se mantiene)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = mode == "LLEGADA",
                    onClick = { onModeChange("LLEGADA") },
                    label = { Text("LLEGADA") }
                )
                FilterChip(
                    selected = mode == "SALIDA",
                    onClick = { onModeChange("SALIDA") },
                    label = { Text("SALIDA") }
                )
            }

            OutlinedTextField(
                value = plate,
                onValueChange = { onPlateChange(it.uppercase(Locale.ROOT)) },
                label = { Text("Placa") },
                modifier = Modifier.fillMaxWidth().focusRequester(plateFocus)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = eco,
                    onValueChange = onEcoChange,
                    label = { Text("Eco") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = vehicleType,
                    onValueChange = onVehicleTypeChange,
                    label = { Text("Tipo vehículo") },
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = paxStr,
                    onValueChange = { onPaxChange(it.filter(Char::isDigit)) },
                    label = { Text("Pax") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                if (base.uppercase(Locale.ROOT) == "PERIFERIA") {
                    OutlinedTextField(
                        value = luggageStr,
                        onValueChange = { onLuggageChange(it.filter(Char::isDigit)) },
                        label = { Text("Maletero") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Switch(checked = useManualTime, onCheckedChange = { useManualTime = it })
                Spacer(Modifier.width(8.dp))
                Text("Editar hora manual")
            }

            if (useManualTime) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = hh,
                        onValueChange = { hh = it.filter(Char::isDigit).take(2) },
                        label = { Text("HH") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = mm,
                        onValueChange = { mm = it.filter(Char::isDigit).take(2) },
                        label = { Text("MM") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = ss,
                        onValueChange = { ss = it.filter(Char::isDigit).take(2) },
                        label = { Text("SS") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Botones: principal según modo + cambiar modo
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    modifier = Modifier.weight(1f),
                    enabled = !clickLocked,
                    onClick = { submit() }
                ) {
                    Text(
                        if (mode == "LLEGADA") "+ Guardar LLEGADA" else "+ Guardar SALIDA"
                    )
                }

                OutlinedButton(
                    enabled = !clickLocked,
                    onClick = { onModeChange(if (mode == "LLEGADA") "SALIDA" else "LLEGADA") }
                ) {
                    Text("Cambiar")
                }
            }
        }
    }
}
