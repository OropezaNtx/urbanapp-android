package com.oropeza.urbanapp.asd.location

import kotlin.math.cos

/**
 * Filtro Kalman 2D muy ligero para (lat,lon).
 * Trabaja internamente en metros (equirectangular) para que R/Q sean intuitivos.
 */
internal class Kalman2D(
    private val processNoiseM2: Double = 1.0, // Q: ruido de proceso (m^2)
    private val resetAfterMs: Long = 30_000L
) {

    private var initialized = false
    private var tLast: Long = 0L

    // referencia para proyección
    private var lat0: Double = 0.0
    private var lon0: Double = 0.0
    private var cosLat0: Double = 1.0

    // estado (m)
    private var x: Double = 0.0
    private var y: Double = 0.0

    // covarianza (m^2)
    private var pX: Double = 1000.0
    private var pY: Double = 1000.0

    fun reset() {
        initialized = false
    }

    fun filter(lat: Double, lon: Double, accM: Double, timeMs: Long): Pair<Double, Double> {
        // reinicio por tiempo (evita que un estado viejo “arrastre” al nuevo)
        if (initialized && (timeMs - tLast) > resetAfterMs) reset()

        if (!initialized) {
            lat0 = lat
            lon0 = lon
            cosLat0 = cos(Math.toRadians(lat0)).coerceAtLeast(0.1)

            val (xm, ym) = llToMeters(lat, lon)
            x = xm
            y = ym
            pX = (accM * accM).coerceAtLeast(25.0)
            pY = (accM * accM).coerceAtLeast(25.0)
            tLast = timeMs
            initialized = true
            return lat to lon
        }

        // predicción (modelo constante)
        val dt = ((timeMs - tLast).coerceAtLeast(0L)).toDouble() / 1000.0
        val q = processNoiseM2 * (1.0 + dt) // escala suave con dt
        pX += q
        pY += q

        // medida
        val (zx, zy) = llToMeters(lat, lon)
        val r = (accM * accM).coerceAtLeast(9.0) // R en m^2

        // update X
        val kx = pX / (pX + r)
        x = x + kx * (zx - x)
        pX = (1.0 - kx) * pX

        // update Y
        val ky = pY / (pY + r)
        y = y + ky * (zy - y)
        pY = (1.0 - ky) * pY

        tLast = timeMs

        // regreso a lat/lon
        return metersToLl(x, y)
    }

    private fun llToMeters(lat: Double, lon: Double): Pair<Double, Double> {
        val mPerDegLat = 111_320.0
        val mPerDegLon = 111_320.0 * cosLat0
        val dx = (lon - lon0) * mPerDegLon
        val dy = (lat - lat0) * mPerDegLat
        return dx to dy
    }

    private fun metersToLl(xm: Double, ym: Double): Pair<Double, Double> {
        val mPerDegLat = 111_320.0
        val mPerDegLon = 111_320.0 * cosLat0
        val lat = lat0 + (ym / mPerDegLat)
        val lon = lon0 + (xm / mPerDegLon)
        return lat to lon
    }
}
