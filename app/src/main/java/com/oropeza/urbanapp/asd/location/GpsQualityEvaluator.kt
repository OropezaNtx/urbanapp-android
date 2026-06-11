package com.oropeza.urbanapp.asd.location

/**
 * Clasifica la calidad de un punto GPS con criterios simples, estables y auditables.
 *
 * Regla operativa importante:
 * - El tracking de campo debe intentar registrar la ruta cada 2 segundos.
 * - Esta clase NO decide cada cuánto guardar; solo clasifica calidad y razones.
 */
object GpsQualityEvaluator {

    enum class Quality {
        EXCELLENT,
        GOOD,
        USABLE,
        POOR,
        INVALID
    }

    data class Result(
        val quality: Quality,
        val label: String,
        val isValid: Boolean,
        val isUsableForTrack: Boolean,
        val isUsableForEvent: Boolean,
        val reason: String
    )

    fun evaluate(
        lat: Double,
        lon: Double,
        accM: Double,
        provider: String? = null
    ): Result {
        if (!lat.isFinite() || !lon.isFinite()) {
            return invalid("Coordenada no finita")
        }
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) {
            return invalid("Coordenada fuera de rango")
        }
        if (lat == 0.0 && lon == 0.0) {
            return invalid("Coordenada 0,0")
        }
        if (!accM.isFinite() || accM <= 0.0) {
            return invalid("Accuracy inválida")
        }

        val providerLabel = provider?.takeIf { it.isNotBlank() } ?: "unknown"

        return when {
            accM <= 10.0 -> Result(
                quality = Quality.EXCELLENT,
                label = "EXCELENTE",
                isValid = true,
                isUsableForTrack = true,
                isUsableForEvent = true,
                reason = "±${accM.toInt()}m / $providerLabel"
            )
            accM <= 25.0 -> Result(
                quality = Quality.GOOD,
                label = "BUENO",
                isValid = true,
                isUsableForTrack = true,
                isUsableForEvent = true,
                reason = "±${accM.toInt()}m / $providerLabel"
            )
            accM <= 45.0 -> Result(
                quality = Quality.USABLE,
                label = "USABLE",
                isValid = true,
                isUsableForTrack = true,
                isUsableForEvent = false,
                reason = "±${accM.toInt()}m / $providerLabel"
            )
            accM <= 80.0 -> Result(
                quality = Quality.POOR,
                label = "POBRE",
                isValid = true,
                isUsableForTrack = false,
                isUsableForEvent = false,
                reason = "±${accM.toInt()}m / $providerLabel"
            )
            else -> Result(
                quality = Quality.INVALID,
                label = "RECHAZADO",
                isValid = false,
                isUsableForTrack = false,
                isUsableForEvent = false,
                reason = "Accuracy mayor a 80m / $providerLabel"
            )
        }
    }

    private fun invalid(reason: String): Result = Result(
        quality = Quality.INVALID,
        label = "INVÁLIDO",
        isValid = false,
        isUsableForTrack = false,
        isUsableForEvent = false,
        reason = reason
    )
}
