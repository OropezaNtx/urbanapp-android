package com.oropeza.urbanapp.asd.location

/**
 * Extrae diagnósticos desde el campo provider de TrackPoint.
 *
 * Actualmente TrackingService etiqueta provider así:
 * fused+kalman+TRACK+ARMED+GOOD
 *
 * Esto permite separar provider real, filtro, modo y calidad sin cambiar Room todavía.
 */
object GpsProviderDiagnostics {

    data class Parsed(
        val providerRaw: String,
        val filter: String,
        val mode: String,
        val state: String,
        val quality: String
    )

    fun parse(provider: String?): Parsed {
        val parts = provider
            ?.split("+")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()

        val qualityValues = setOf("EXCELLENT", "GOOD", "USABLE", "POOR", "INVALID")
        val modeValues = setOf("ACQUIRE", "TRACK", "STILL", "NA")
        val stateValues = setOf("ARMED", "QUICK")

        return Parsed(
            providerRaw = parts.firstOrNull().orEmpty(),
            filter = parts.firstOrNull { it == "kalman" || it == "raw" }.orEmpty(),
            mode = parts.firstOrNull { it in modeValues }.orEmpty(),
            state = parts.firstOrNull { it in stateValues }.orEmpty(),
            quality = parts.firstOrNull { it in qualityValues }.orEmpty()
        )
    }
}
