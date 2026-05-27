package com.oropeza.urbanapp.asd.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

// ======================================
// ✅ Wrapper viejo (compatibilidad)
// ======================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopEventDialog(
    title: String,
    initialCount: Int = 1,
    onDismiss: () -> Unit,
    onConfirm: (count: Int, stopName: String?, notes: String?) -> Unit
) {
    var countText by remember { mutableStateOf(initialCount.toString()) }
    var stopName by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun parseCount(): Int = countText.trim().toIntOrNull() ?: 0

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = {
                val n = parseCount()
                if (n <= 0) {
                    error = "La cantidad debe ser mayor a 0."
                    return@Button
                }
                onConfirm(
                    n,
                    stopName.trim().ifBlank { null },
                    notes.trim().ifBlank { null }
                )
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Cantidad", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = {
                        val n = parseCount()
                        countText = (if (n <= 1) 1 else n - 1).toString()
                    }) { Text("−") }

                    OutlinedTextField(
                        value = countText,
                        onValueChange = { countText = it.filter { ch -> ch.isDigit() }.take(4) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text("Personas") }
                    )

                    OutlinedButton(onClick = {
                        val n = parseCount()
                        countText = (if (n <= 0) 1 else n + 1).toString()
                    }) { Text("+") }
                }

                OutlinedTextField(
                    value = stopName,
                    onValueChange = { stopName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Parada / referencia (opcional)") },
                    singleLine = true
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Notas (opcional)") }
                )

                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    )
}

// ======================================
// ✅ NUEVO: diálogo unificado campo
// ======================================
enum class EventKind { ASCENSO, DESCENSO, DEMORA }

data class UnifiedEventInput(
    val kind: EventKind,
    val men: Int,
    val women: Int,
    val delaySet: Set<String>,
    val otherDesc: String?,
    val hasLuggage: Boolean,
    val stopName: String?,
    val notes: String?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedEventDialog(
    title: String = "Registrar evento",
    onDismiss: () -> Unit,
    onConfirm: (UnifiedEventInput) -> Unit
) {
    var kind by remember { mutableStateOf(EventKind.ASCENSO) }
    var men by remember { mutableStateOf(0) }
    var women by remember { mutableStateOf(0) }

    val allCodes = listOf("AD", "C", "S", "TM", "CND", "O")
    var selected by remember { mutableStateOf(setOf<String>()) }

    var otherDesc by remember { mutableStateOf("") }
    var hasLuggage by remember { mutableStateOf(false) }
    var stopName by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    // ✅ En ASC/DESC, AD forzado
    val adForced = (kind == EventKind.ASCENSO || kind == EventKind.DESCENSO)

    // ✅ Cuando cambias a DEMORA: quitar AD automáticamente (tu regla)
    LaunchedEffect(kind) {
        if (kind == EventKind.DEMORA) {
            selected = selected - "AD"
        } else {
            // si es asc/desc, asegúrate que AD esté
            selected = selected + "AD"
        }
    }

    fun toggle(code: String) {
        selected = if (selected.contains(code)) selected - code else selected + code
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                Text("Tipo de evento", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = kind == EventKind.ASCENSO, onClick = { kind = EventKind.ASCENSO }, label = { Text("ASCENSO") })
                    FilterChip(selected = kind == EventKind.DESCENSO, onClick = { kind = EventKind.DESCENSO }, label = { Text("DESCENSO") })
                    FilterChip(selected = kind == EventKind.DEMORA, onClick = { kind = EventKind.DEMORA }, label = { Text("DEMORA") })
                }

                Text("Pax por sexo", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Counter("Hombres", men, onMinus = { if (men > 0) men-- }, onPlus = { men++ }, modifier = Modifier.weight(1f))
                    Counter("Mujeres", women, onMinus = { if (women > 0) women-- }, onPlus = { women++ }, modifier = Modifier.weight(1f))
                }

                Text("Tipo(s) de demora (se exportan con /)", style = MaterialTheme.typography.titleSmall)

                // ✅ AD deshabilitado si estás en DEMORA
                val disableAdInDemora = (kind == EventKind.DEMORA)

                ChipsGrid(
                    items = allCodes,
                    selected = selected,
                    forcedAd = adForced,
                    onToggle = { code ->
                        if (adForced && code == "AD") return@ChipsGrid
                        if (disableAdInDemora && code == "AD") return@ChipsGrid
                        toggle(code)
                    },
                    disableAd = disableAdInDemora
                )

                if (selected.contains("O")) {
                    OutlinedTextField(
                        value = otherDesc,
                        onValueChange = { otherDesc = it },
                        label = { Text("Descripción de 'Otro' (obligatoria)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Switch(checked = hasLuggage, onCheckedChange = { hasLuggage = it })
                    Text("Porta maleta / bulto voluminoso")
                }

                OutlinedTextField(
                    value = stopName,
                    onValueChange = { stopName = it },
                    label = { Text("Parada / referencia (opcional)") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Observaciones (opcional)") },
                    modifier = Modifier.fillMaxWidth()
                )

                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                error = null

                // ✅ ASC/DESC requieren pax
                if ((kind == EventKind.ASCENSO || kind == EventKind.DESCENSO) && (men + women) <= 0) {
                    error = "En ASCENSO/DESCENSO debes registrar al menos 1 persona."
                    return@Button
                }

                // ✅ O requiere descripción
                if (selected.contains("O") && otherDesc.trim().isBlank()) {
                    error = "Describe la demora 'O' (Otro)."
                    return@Button
                }

                // ✅ Si es DEMORA y no seleccionó nada: por defecto CND (tu + mi sugerencia)
                val fixedSelected = if (kind == EventKind.DEMORA && selected.isEmpty()) setOf("CND") else selected

                onConfirm(
                    UnifiedEventInput(
                        kind = kind,
                        men = men,
                        women = women,
                        delaySet = fixedSelected,
                        otherDesc = otherDesc.trim().ifBlank { null },
                        hasLuggage = hasLuggage,
                        stopName = stopName.trim().ifBlank { null },
                        notes = notes.trim().ifBlank { null }
                    )
                )
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChipsGrid(
    items: List<String>,
    selected: Set<String>,
    forcedAd: Boolean,
    onToggle: (String) -> Unit,
    disableAd: Boolean = false
) {
    val row1 = items.take(3)
    val row2 = items.drop(3)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row1.forEach { code ->
                val isAd = code == "AD"
                FilterChip(
                    selected = selected.contains(code) || (forcedAd && isAd),
                    onClick = { onToggle(code) },
                    label = { Text(code) },
                    enabled = !((forcedAd && isAd) || (disableAd && isAd))
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row2.forEach { code ->
                FilterChip(
                    selected = selected.contains(code),
                    onClick = { onToggle(code) },
                    label = { Text(code) }
                )
            }
        }
    }
}


@Composable
private fun Counter(
    label: String,
    value: Int,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onMinus, modifier = Modifier.weight(1f)) { Text("-") }
                Text(value.toString(), modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onPlus, modifier = Modifier.weight(1f)) { Text("+") }
            }
        }
    }
}


