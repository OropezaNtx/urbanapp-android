package com.oropeza.urbanapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oropeza.urbanapp.ui.theme.LocalAforaColors
import com.oropeza.urbanapp.ui.theme.LocalAforaTypography

@Composable
fun AforaStatusChip(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val typography = LocalAforaTypography.current
    
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(4.dp),
        modifier = modifier
    ) {
        Text(
            text = text.uppercase(),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = typography.Label,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun AforaHealthIndicator(
    isHealthy: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = LocalAforaColors.current
    
    Box(
        modifier = modifier
            .size(12.dp)
            .background(
                color = if (isHealthy) colors.Success else colors.Danger,
                shape = CircleShape
            )
    )
}
