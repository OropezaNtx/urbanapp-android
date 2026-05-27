package com.oropeza.urbanapp.asd.location

import android.content.Context
import android.hardware.*
import kotlin.math.abs

/**
 * Heading estable para bajas velocidades.
 * - Usa accelerometer + magnetometer (y gyro si existe, pero aquí dejamos versión simple y estable).
 * - Devuelve heading en grados [0..360).
 */
class HeadingProvider(context: Context) : SensorEventListener {

    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnet = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val accelValues = FloatArray(3)
    private val magnetValues = FloatArray(3)
    private var hasAccel = false
    private var hasMagnet = false

    private var lastHeadingDeg: Double? = null
    private var lastUpdateMs: Long = 0L

    fun start() {
        // SENSOR_DELAY_GAME = suficiente para rumbo sin gastar demasiado
        accel?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        magnet?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() {
        sm.unregisterListener(this)
        hasAccel = false
        hasMagnet = false
    }

    /**
     * Devuelve heading filtrado (EMA) o null si no hay sensores aún.
     */
    fun getHeadingDeg(nowMs: Long = System.currentTimeMillis()): Double? {
        if (!hasAccel || !hasMagnet) return null

        val r = FloatArray(9)
        val i = FloatArray(9)

        val ok = SensorManager.getRotationMatrix(r, i, accelValues, magnetValues)
        if (!ok) return null

        val orientation = FloatArray(3)
        SensorManager.getOrientation(r, orientation)

        // azimuth rad -> deg
        var deg = Math.toDegrees(orientation[0].toDouble())
        if (deg < 0) deg += 360.0

        // EMA simple para evitar “brinco” (suaviza solo si actualiza rápido)
        val dt = nowMs - lastUpdateMs
        lastUpdateMs = nowMs

        val prev = lastHeadingDeg
        val alpha = if (dt in 1..2500) 0.18 else 0.35

        val out = if (prev == null) deg else {
            // ajuste por wrap-around 360 (evitar salto 359->0)
            val delta = shortestAngleDelta(prev, deg)
            val blended = prev + alpha * delta
            normalizeDeg(blended)
        }

        lastHeadingDeg = out
        return out
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, accelValues, 0, 3)
                hasAccel = true
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, magnetValues, 0, 3)
                hasMagnet = true
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun normalizeDeg(x: Double): Double {
        var v = x % 360.0
        if (v < 0) v += 360.0
        return v
    }

    private fun shortestAngleDelta(fromDeg: Double, toDeg: Double): Double {
        var d = (toDeg - fromDeg + 540.0) % 360.0 - 180.0
        // d queda en [-180, 180]
        return d
    }
}
