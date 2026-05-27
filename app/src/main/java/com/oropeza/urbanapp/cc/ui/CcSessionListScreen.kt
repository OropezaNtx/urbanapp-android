package com.oropeza.urbanapp.cc.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.oropeza.urbanapp.asd.AsdGraph
import com.oropeza.urbanapp.asd.data.local.CcSession
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CcSessionListScreen(
    onNew: () -> Unit,
    onOpen: (Long) -> Unit,
    onBackHome: () -> Unit
) {
    val sessions by AsdGraph.repo.ccSessionsFlow.collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CC - Cierres de Circuito") },
                navigationIcon = {
                    TextButton(onClick = onBackHome) { Text("Home") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNew) { Text("+") }
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(sessions) { s ->
                CcSessionCard(s) { onOpen(s.sessionId) }
            }
        }
    }
}

@Composable
private fun CcSessionCard(s: CcSession, onClick: () -> Unit) {
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale("es", "MX")) }
    val dateStr = remember(s.dateDayMs) {
        if (s.dateDayMs == 0L) "(sin fecha)" else fmt.format(Date(s.dateDayMs))
    }

    Card(Modifier.fillMaxWidth().clickable { onClick() }) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("ID ${s.planningId} • ${s.base}", style = MaterialTheme.typography.titleMedium)
            Text("${s.derrotero} • ${s.companyName}", style = MaterialTheme.typography.bodyMedium)
            Text("${s.terminalOrigin} → ${s.terminalDestination} • ${s.direction}", style = MaterialTheme.typography.bodySmall)
            Text("${s.locationName} • ${s.aforador} • $dateStr", style = MaterialTheme.typography.bodySmall)
        }
    }
}
