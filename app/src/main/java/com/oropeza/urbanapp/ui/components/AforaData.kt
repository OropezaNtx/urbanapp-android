package com.oropeza.urbanapp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oropeza.urbanapp.ui.theme.LocalAforaColors
import com.oropeza.urbanapp.ui.theme.LocalAforaTypography

@Composable
fun AforaSectionHeader(
    text: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalAforaColors.current
    val typography = LocalAforaTypography.current
    
    Text(
        text = text.uppercase(),
        style = typography.Label,
        color = colors.Secondary.copy(alpha = 0.5f),
        fontWeight = FontWeight.Bold,
        modifier = modifier
    )
}

@Composable
fun AforaMetadataRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    labelColor: Color? = null,
    valueColor: Color? = null
) {
    val colors = LocalAforaColors.current
    val typography = LocalAforaTypography.current
    
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label.uppercase(),
            style = typography.Label,
            color = labelColor ?: colors.Secondary.copy(alpha = 0.4f)
        )
        Text(
            text = value,
            style = typography.Data,
            color = valueColor ?: colors.Secondary
        )
    }
}
