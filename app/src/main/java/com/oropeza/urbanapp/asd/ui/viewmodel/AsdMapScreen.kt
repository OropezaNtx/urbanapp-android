package com.oropeza.urbanapp.asd.ui.viewmodel

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun AsdMapScreen(
    tripId: Long,
    onBack: () -> Unit
) {
    Text("Mapa ASD pendiente: $tripId")
}
