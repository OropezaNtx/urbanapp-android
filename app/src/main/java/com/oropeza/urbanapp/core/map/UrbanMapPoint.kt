package com.oropeza.urbanapp.core.map

/**
 * Punto geográfico genérico para reutilizar el mapa en FOV, ASD, CC, Demoras y futuros módulos.
 */
data class UrbanMapPoint(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val lat: Double,
    val lon: Double,
    val accuracyM: Double? = null,
    val status: String? = null,
    val module: String? = null,
    val timestampMs: Long? = null,
    val metadata: Map<String, String> = emptyMap()
) {
    val hasValidCoordinates: Boolean
        get() = lat != 0.0 && lon != 0.0 && lat in -90.0..90.0 && lon in -180.0..180.0
}
