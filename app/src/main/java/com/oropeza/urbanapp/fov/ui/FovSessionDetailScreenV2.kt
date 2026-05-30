package com.oropeza.urbanapp.fov.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun FovSessionDetailScreenV2(
    sessionId: Long,
    onBack: () -> Unit,
    onOpenMap: () -> Unit,
    vm: FovSessionVm = viewModel()
) {
    val session by vm.repo.sessionFlow(sessionId).collectAsState(initial = null)
    var exportMsg by remember { mutableStateOf<String?>(null) }
    var exportError by remember { mutableStateOf<String?>(null) }

    Box {
        FovSessionDetailScreen(
            sessionId = sessionId,
            onBack = onBack,
            vm = vm
        )

        session?.let { s ->
            ElevatedCard(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Button(onClick = onOpenMap) {
                        Text("Ver mapa")
                    }

                    Spacer(Modifier.height(8.dp))

                    FovExportCsvButton(
                        sessionId = sessionId,
                        estacion = s.estacion,
                        ubicacion = s.ubicacion,
                        sentido = s.sentido,
                        vm = vm,
                        onSuccess = {
                            exportError = null
                            exportMsg = "CSV exportado correctamente."
                        },
                        onError = { msg ->
                            exportMsg = null
                            exportError = msg
                        }
                    )

                    exportMsg?.let {
                        Text(it, color = MaterialTheme.colorScheme.primary)
                    }
                    exportError?.let {
                        Text("Error: $it", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
