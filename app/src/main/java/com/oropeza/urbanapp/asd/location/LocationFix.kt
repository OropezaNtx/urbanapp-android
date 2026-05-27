package com.oropeza.urbanapp.asd.location

data class LocationFix(
    val lat: Double,
    val lon: Double,
    val accM: Double,
    val provider: String,
    val fixTime: Long,
    val status: String // "FIX_OK" | "FIX_USABLE" | "NO_FIX"
)
