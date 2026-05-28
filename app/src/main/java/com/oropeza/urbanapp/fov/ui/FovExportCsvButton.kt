package com.oropeza.urbanapp.fov.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FovExportCsvButton(
    sessionId: Long,
    estacion: String,
    ubicacion: String,
    sentido: String,
    vm: FovSessionVm,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    fun safePart(value: String): String {
        return value.trim()
            .ifBlank { "SIN_DATO" }
            .replace(" ", "_")
            .replace("/", "_")
            .replace("\\", "_")
            .replace("|", "_")
            .replace(":", "_")
            .replace(";", "_")
            .replace(",", "_")
            .take(40)
    }

    val fileName = remember(sessionId, estacion, ubicacion, sentido) {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        "FOV_${sessionId}_${safePart(estacion)}_${safePart(ubicacion)}_${safePart(sentido)}_$stamp.csv"
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult

        vm.exportSessionCsv(
            sessionId = sessionId,
            uri = uri,
            onDone = onSuccess,
            onError = onError
        )
    }

    Button(onClick = { launcher.launch(fileName) }) {
        Text("Exportar CSV")
    }
}
