package com.oropeza.urbanapp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oropeza.urbanapp.ui.theme.LocalAforaColors
import com.oropeza.urbanapp.ui.theme.LocalAforaTypography

@Composable
fun AforaPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = LocalAforaColors.current
    val typography = LocalAforaTypography.current
    
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp),
        shape = MaterialTheme.shapes.extraSmall,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.Primary,
            contentColor = colors.OnPrimary,
            disabledContainerColor = colors.Disabled
        )
    ) {
        Text(
            text = text.uppercase(),
            style = typography.BodyLarge,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
fun AforaSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isOutlined: Boolean = true
) {
    val colors = LocalAforaColors.current
    val typography = LocalAforaTypography.current
    
    if (isOutlined) {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = MaterialTheme.shapes.extraSmall,
            border = BorderStroke(
                width = 1.dp,
                color = if (enabled) colors.Primary.copy(alpha = 0.5f) else colors.Disabled
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = colors.Primary,
                disabledContentColor = colors.Secondary.copy(alpha = 0.38f)
            )
        ) {
            Text(
                text = text.uppercase(),
                style = typography.BodySmall,
                fontWeight = FontWeight.Bold
            )
        }
    } else {
        // Variant for configuration-style buttons
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = MaterialTheme.shapes.extraSmall,
            border = BorderStroke(1.dp, colors.Outline),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = colors.Secondary
            )
        ) {
            Text(
                text = text.uppercase(),
                style = typography.BodySmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
