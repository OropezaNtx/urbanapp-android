package com.oropeza.urbanapp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.oropeza.urbanapp.ui.theme.LocalAforaColors

@Composable
fun AforaOperationalCard(
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    borderAlpha: Float = 0.3f,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalAforaColors.current
    
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraSmall,
        colors = CardDefaults.cardColors(
            containerColor = containerColor ?: colors.Surface
        ),
        border = BorderStroke(1.dp, colors.Outline.copy(alpha = borderAlpha))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}
