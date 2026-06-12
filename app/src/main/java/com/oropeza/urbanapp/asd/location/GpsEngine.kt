package com.oropeza.urbanapp.asd.location

object GpsEngine {

    enum class Decision { ACCEPT, SMOOTH, HOLD, REJECT }
    enum class Mode { ACQUIRE, TRACK, STILL }

    data class Config(
        val requiredAccM: Double = 15.0,
        val usableAccM: Double = 35.0,
        val kalmanUseAccM: Double = 35.0,
        val goodFixNeeded: Int = 2,
        val hardRejectJumpM: Double = 80.0,
        val maxSpeedMs: Double = 45.0,
        val excellentStepM: Double = 4.0,
        val goodStepM: Double = 6.0,
        val usableStepM: Double = 9.0,
        val stillDistanceM: Double = 1.2,
        val stillCounterNeeded: Int = 8,
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
        if (!quality.isValid) return reject(input, quality, "invalid")

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
                reason = "hold"
            )
        }

        if (input.accM > config.usableAccM) return reject(input, quality, "poor_accuracy")

        var distanceM = 0.0
        var speedMs = 0.0
        var nextStillCounter = input.stillCounter
        var nextMode = if (input.mode == Mode.ACQUIRE) Mode.TRACK else input.mode

        val lastLat = input.lastAcceptedLat
        val lastLon = input.lastAcceptedLon
        val lastTime = input.lastAcceptedTimeMs
        val lastElapsed = input.lastAcceptedElapsedNanos

        if (lastLat != null && lastLon != null && lastTime != null) {
            val dtSec = if (input.elapsedNanos > 0L && lastElapsed != null) {
                ((input.elapsedNanos - lastElapsed).coerceAtLeast(1L)).toDouble() / 1_000_000_000.0
            } else {
                ((input.timeMs - lastTime).coerceAtLeast(1L)).toDouble() / 1000.0
            }
            distanceM = haversineMeters(lastLat, lastLon, input.lat, input.lon)
            speedMs = distanceM / dtSec

            if (speedMs > config.maxSpeedMs) return reject(input, quality, "speed")
            if (distanceM > config.hardRejectJumpM && input.accM >= config.requiredAccM) return reject(input, quality, "jump")

            nextStillCounter = if (distanceM < config.stillDistanceM && input.accM <= config.usableAccM) input.stillCounter + 1 else 0
            nextMode = if (nextStillCounter >= config.stillCounterNeeded) Mode.STILL else Mode.TRACK
        }

        val maxStepM = when (quality.quality) {
            GpsQualityEvaluator.Quality.EXCELLENT -> config.excellentStepM
            GpsQualityEvaluator.Quality.GOOD -> config.goodStepM
            GpsQualityEvaluator.Quality.USABLE -> config.usableStepM
            else -> config.usableStepM
        }

        val saveByTime = input.lastSavedTimeMs == null ||
                (input.timeMs - input.lastSavedTimeMs).coerceAtLeast(0L) >= config.saveEveryMs
        val decision = if (distanceM > maxStepM) Decision.SMOOTH else Decision.ACCEPT

        return Output(
            decision = decision,
            quality = quality,
            nextGoodFixStreak = nextGoodFixStreak,
            nextStillCounter = nextStillCounter,
            nextMode = nextMode,
            shouldArm = !input.recordingArmed && nextGoodFixStreak >= config.goodFixNeeded,
            shouldUseKalman = input.accM <= config.kalmanUseAccM,
            shouldSave = saveByTime,
            maxStepM = maxStepM,
            distanceM = distanceM,
            speedMs = speedMs,
            reason = decision.name.lowercase()
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
