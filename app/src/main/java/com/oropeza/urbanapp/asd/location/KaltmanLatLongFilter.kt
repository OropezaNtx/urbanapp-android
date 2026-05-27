package com.oropeza.urbanapp.asd.location

import kotlin.math.max
import kotlin.math.sqrt

/**
 * Kalman simple (2 x 1D) para lat/lon.
 *
 * - Pensado para estabilizar puntos cuando hay deriva / jitter.
 * - R (measurement noise) se calcula con base en accuracy (metros).
 * - Q (process noise) se ajusta con el tiempo y modo (stationary/moving).
 *
 * NOTA: Este NO es un Kalman de velocidad/acceleración completo (CV model),
 * pero en apps reales da un resultado muy bueno para estabilizar "fix" de evento.
 */
class KalmanLatLonFilter(
    private val stationaryQ: Double = 1e-10, // más bajo = más "pegado" (parado)
    private val movingQ: Double = 5e-10      // más alto = responde más (en movimiento)
) {
    private val latF = Kalman1D()
    private val lonF = Kalman1D()
    private var lastTimeMs: Long? = null

    fun reset() {
        latF.reset()
        lonF.reset()
        lastTimeMs = null
    }

    /**
     * @param lat latitude
     * @param lon longitude
     * @param accM accuracy en metros (1-sigma aprox)
     * @param timeMs timestamp
     * @param isStationary true si el evento se detectó "parado"
     */
    fun update(lat: Double, lon: Double, accM: Double, timeMs: Long, isStationary: Boolean): Pair<Double, Double> {
        val prev = lastTimeMs
        val dtSec = if (prev == null) 1.0 else max(0.001, (timeMs - prev).toDouble() / 1000.0)
        lastTimeMs = timeMs

        // Measurement noise: convertimos accuracy (m) a "ruido angular" aproximado.
        // 1 deg lat ≈ 111_320 m. Lon depende de lat pero aquí lo aproximamos igual;
        // para eventos (ventana corta) funciona bastante bien.
        val metersPerDeg = 111_320.0
        val rDeg = (max(3.0, accM) / metersPerDeg) // mínimo 3m para evitar R muy chico
        val r = rDeg * rDeg // varianza

        val qBase = if (isStationary) stationaryQ else movingQ
        val q = qBase * dtSec

        val latOut = latF.filter(measurement = lat, r = r, q = q)
        val lonOut = lonF.filter(measurement = lon, r = r, q = q)
        return latOut to lonOut
    }

    private class Kalman1D {
        private var x: Double? = null // state estimate
        private var p: Double = 1.0   // estimate covariance

        fun reset() {
            x = null
            p = 1.0
        }

        /**
         * @param measurement z
         * @param r measurement noise (variance)
         * @param q process noise (variance)
         */
        fun filter(measurement: Double, r: Double, q: Double): Double {
            val x0 = x
            if (x0 == null) {
                // Inicializa con la primera medición
                x = measurement
                p = max(1e-12, r)
                return measurement
            }

            // Predict
            p = p + q

            // Update
            val k = p / (p + r)           // Kalman gain
            val xNew = x0 + k * (measurement - x0)
            p = (1.0 - k) * p

            x = xNew
            return xNew
        }
    }
}
