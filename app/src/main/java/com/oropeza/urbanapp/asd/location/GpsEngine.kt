package com.oropeza.urbanapp.asd.location

object GpsEngine {

    enum class Decision { ACCEPT, SMOOTH, HOLD, REJECT }
    enum class Mode { ACQUIRE, TRACK, STILL }

    data class Config(
        val requiredAccM: Double = 15.0,
        val usableAccM: Double = 45.0,
        val kalmanUseAccM: Double = 35.0,
        val goodFixNeeded: Int = 2,
        val hardRejectJumpM: Double = 250.0,
        val maxSpeedMs: Double = 35.0,      // ~126 km/h. Filtro principal para vehículos urbanos.
        val excellentStepM: Double = 35.0,
        val goodStepM: Double = 50.0,
        val usableStepM: Double = 80.0,
        val stillMinRadiusM: Double = 18.0,
        val stillAccuracyMultiplier: Double = 1.6,
        val stillExitMinDistanceM: Double = 25.0,
        val stillExitAccuracyMultiplier: Double = 2.2,
        val stillCounterNeeded: Int = 3,
        val movingSpeedExitMs: Double = 2.5,
        val saveEveryMs: Long = 2_000L
    )

    data class Input(
        val lat: Double,
        val lon: Double,
        val accM: Double,
        val provider: String?,
        val timeMs: Long,
        val elapsedNanos: Long,
        val lastAcceptedLat: Double?,
        val lastAcceptedLon: Double?,
        val lastAcceptedTimeMs: Long?,
        val lastAcceptedElapsedNanos: Long?,
        val lastSavedTimeMs: Long?,
        val lastSavedLat: Double?,
        val lastSavedLon: Double?,
        val recordingArmed: Boolean,
        val goodFixStreak: Int,
        val stillCounter: Int,
        val mode: Mode
    )

    data class Output(
        val decision: Decision,
        val quality: GpsQualityEvaluator.Result,
        val nextGoodFixStreak: Int,
        val nextStillCounter: Int,
        val nextMode: Mode,
        val shouldArm: Boolean,
        val shouldUseKalman: Boolean,
        val shouldSave: Boolean,
        val maxStepM: Double,
        val distanceM: Double,
        val speedMs: Double,
        val reason: String
    )

    fun evaluate(input: Input, config: Config = Config()): Output {
        val quality = GpsQualityEvaluator.evaluate(input.lat, input.lon, input.accM, input.provider)
        if (!quality.isValid) return reject(input, quality, "REJECT_INVALID")

        val nextGoodFixStreak = if (input.accM <= config.requiredAccM) input.goodFixStreak + 1 else 0

        if (!input.recordingArmed && nextGoodFixStreak < config.goodFixNeeded) {
            return Output(
                decision = Decision.HOLD,
                quality = quality,
                nextGoodFixStreak = nextGoodFixStreak,
                nextStillCounter = input.stillCounter,
                nextMode = Mode.ACQUIRE,
                shouldArm = false,
                shouldUseKalman = false,
                shouldSave = false,
                maxStepM = 0.0,
                distanceM = 0.0,
                speedMs = 0.0,
                reason = "HOLDING_FIX"
            )
        }

        if (input.accM > config.usableAccM) return reject(input, quality, "POOR_ACCURACY")

        var distanceM = 0.0
        var speedMs = 0.0
        var nextStillCounter = input.stillCounter
        var nextMode = if (input.mode == Mode.ACQUIRE) Mode.TRACK else input.mode
        var reason = "TRACK"

        val lastLat = input.lastAcceptedLat
        val lastLon = input.lastAcceptedLon
        val lastTime = input.lastAcceptedTimeMs
        val lastElapsed = input.lastAcceptedElapsedNanos

        // Distancia desde el último punto GUARDADO para lógica de reposo inteligente
        val lastSavedLat = input.lastSavedLat
        val lastSavedLon = input.lastSavedLon
        var distFromSavedM = 0.0
        if (lastSavedLat != null && lastSavedLon != null) {
            distFromSavedM = haversineMeters(lastSavedLat, lastSavedLon, input.lat, input.lon)
        }

        if (lastLat != null && lastLon != null && lastTime != null) {
            val dtSec = if (input.elapsedNanos > 0L && lastElapsed != null) {
                ((input.elapsedNanos - lastElapsed).coerceAtLeast(1L)).toDouble() / 1_000_000_000.0
            } else {
                ((input.timeMs - lastTime).coerceAtLeast(1L)).toDouble() / 1000.0
            }
            distanceM = haversineMeters(lastLat, lastLon, input.lat, input.lon)
            speedMs = distanceM / dtSec

            if (speedMs > config.maxSpeedMs) return reject(input, quality, "REJECT_JUMP")

            // Lógica de Reposo Inteligente (Bloqueo de Jitter)
            val jitterRadius = kotlin.math.max(config.stillMinRadiusM, input.accM * config.stillAccuracyMultiplier)
            val exitRadius = kotlin.math.max(config.stillExitMinDistanceM, input.accM * config.stillExitAccuracyMultiplier)
            
            val isJitter = distFromSavedM < jitterRadius
            val hasMovedClearly = distFromSavedM > exitRadius || speedMs > config.movingSpeedExitMs

            if (input.mode == Mode.STILL) {
                if (hasMovedClearly) {
                    nextStillCounter = 0
                    nextMode = Mode.TRACK
                    reason = "MOVING_ACCEPTED"
                } else {
                    nextStillCounter = input.stillCounter + 1
                    nextMode = Mode.STILL
                    // Si el punto es jitter, devolvemos HOLD para no actualizar última posición aceptada
                    if (isJitter) {
                        return Output(
                            decision = Decision.HOLD,
                            quality = quality,
                            nextGoodFixStreak = nextGoodFixStreak,
                            nextStillCounter = nextStillCounter,
                            nextMode = Mode.STILL,
                            shouldArm = false,
                            shouldUseKalman = false,
                            shouldSave = false,
                            maxStepM = 0.0,
                            distanceM = distanceM,
                            speedMs = speedMs,
                            reason = "jitter_ignored"
                        )
                    }
                    reason = "STILL_LOCK"
                }
            } else {
                if (isJitter && speedMs < config.movingSpeedExitMs) {
                    nextStillCounter = input.stillCounter + 1
                    if (nextStillCounter >= config.stillCounterNeeded) {
                        nextMode = Mode.STILL
                        reason = "STILL_LOCK"
                    }
                    
                    // Bloqueo inmediato de jitter incluso antes de entrar formalmente a STILL_LOCK
                    return Output(
                        decision = Decision.HOLD,
                        quality = quality,
                        nextGoodFixStreak = nextGoodFixStreak,
                        nextStillCounter = nextStillCounter,
                        nextMode = nextMode,
                        shouldArm = false,
                        shouldUseKalman = false,
                        shouldSave = false,
                        maxStepM = 0.0,
                        distanceM = distanceM,
                        speedMs = speedMs,
                        reason = "jitter_ignored"
                    )
                } else {
                    nextStillCounter = 0
                    nextMode = Mode.TRACK
                }
            }
        }

        val maxStepM = when (quality.quality) {
            GpsQualityEvaluator.Quality.EXCELLENT -> config.excellentStepM
            GpsQualityEvaluator.Quality.GOOD -> config.goodStepM
            GpsQualityEvaluator.Quality.USABLE -> config.usableStepM
            else -> config.usableStepM
        }

        val timeSinceLastSave = if (input.lastSavedTimeMs == null) Long.MAX_VALUE else (input.timeMs - input.lastSavedTimeMs)
        var shouldSave = timeSinceLastSave >= config.saveEveryMs
        
        // Evitar redundancia excesiva en STILL si ya hay un punto guardado recientemente
        if (nextMode == Mode.STILL && timeSinceLastSave < 30_000L) {
            shouldSave = false
        }

        val decision = if (distanceM > maxStepM && nextMode != Mode.STILL) Decision.SMOOTH else Decision.ACCEPT

        return Output(
            decision = decision,
            quality = quality,
            nextGoodFixStreak = nextGoodFixStreak,
            nextStillCounter = nextStillCounter,
            nextMode = nextMode,
            shouldArm = !input.recordingArmed && nextGoodFixStreak >= config.goodFixNeeded,
            shouldUseKalman = input.accM <= config.kalmanUseAccM,
            shouldSave = shouldSave,
            maxStepM = maxStepM,
            distanceM = distanceM,
            speedMs = speedMs,
            reason = reason
        )
    }

    fun clampStep(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double, maxStepM: Double): Pair<Double, Double> {
        val distanceM = haversineMeters(fromLat, fromLon, toLat, toLon)
        if (distanceM <= maxStepM || distanceM <= 0.0) return toLat to toLon
        val ratio = maxStepM / distanceM
        return (fromLat + (toLat - fromLat) * ratio) to (fromLon + (toLon - fromLon) * ratio)
    }

    private fun reject(input: Input, quality: GpsQualityEvaluator.Result, reason: String): Output = Output(
        decision = Decision.REJECT,
        quality = quality,
        nextGoodFixStreak = 0,
        nextStillCounter = input.stillCounter,
        nextMode = Mode.ACQUIRE,
        shouldArm = false,
        shouldUseKalman = false,
        shouldSave = false,
        maxStepM = 0.0,
        distanceM = 0.0,
        speedMs = 0.0,
        reason = reason
    )

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
                kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
                kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        return 2 * r * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    }
}
