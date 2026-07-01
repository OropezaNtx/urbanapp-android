package com.oropeza.urbanapp.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

object AforaColors {
    // Core Palettes
    val Primary = Color(0xFF0056D2)
    val Secondary = Color(0xFF002B5C)
    val Surface = Color(0xFFFFFFFF)
    val Background = Color(0xFFF8F9FA)
    val OnPrimary = Color(0xFFFFFFFF)
    val OnSurface = Color(0xFF1A1C1E)

    // Semantic Status
    val Success = Color(0xFF008542)
    val Warning = Color(0xFFE57200)
    val Danger = Color(0xFFD50032)
    val Information = Color(0xFF00A3E0)
    val Offline = Color(0xFF6C757D)
    val Sync = Color(0xFF0D6EFD)
    val Disabled = Color(0xFFDEE2E6)
    
    // Borders
    val Outline = Color(0xFFC4C7C5)
}

val LocalAforaColors = staticCompositionLocalOf { AforaColors }
