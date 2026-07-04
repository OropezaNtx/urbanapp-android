package com.oropeza.urbanapp.asd.location.engine

import com.oropeza.urbanapp.asd.data.local.TrackPoint
import com.oropeza.urbanapp.asd.location.KalmanLatLonFilter
import java.util.ArrayDeque
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Afora GPS Engine V3.
 *
 * This class is intentionally Android-free. It owns the capture contract:
 * while a trip is active, the caller may ask for one sample every fixed cadence,
 * and the engine will return a TrackPoint instead of filtering it out.
 *
 * Filtering decisions are represented as tags for now because TrackPoint does not
 * yet have formal quality columns. A later Room migration should promote these
 * tags to explicit fields.
 */
class AforaGpsEngine(
    private val config: Config = Config()
) {

    data class Config(
        val requiredAccM: Double = 25.0,
        val usableAccM: Double = 45.0,
        val kalmanUseAccM: Double = 45.0,
        val goodFixNeeded: Int = 1,
        val maxSpeedMs: Double = 45.0,
        val jumpM: Double = 45.0,
        val jumpAccM: Double = 25.0,
        val stationaryBaseRadiusM: Double = 14.0,
        val staleFixAfterMs: Long = 6_000L,
        val rawBufferMaxSize: Int = 20
    )

    enum class Mode { IDLE, ACQUIRE, TRACK, STILL }

    data class RawLocationFix(
        val lat: Double,
        val lon: Double,
        val accM: Double,
        val fixTimeMs: Long,
        val elapsedNanos: Long,
        val provider: String?,
        val receivedAtMs: Long
    )

    data class RuntimeState(
        val hasFix: Boolean = false,
        val lat: Double = 0.0,
        val lon: Double = 0.0,
        val accM: Double = 0.0,
        val provider: String = "none",
        val fixTimeMs: Long = 0L,
        val receivedAtMs: Long = 0L,
        val savedAtMs: Long = 0L,
        val mode: Mode = Mode.IDLE,
        val isRecordingArmed: Boolean = false,
        val rawBufferSize: Int = 0
    )

    data class CaptureSample(
        val point: TrackPoint,
        val state: RuntimeState,
        val shouldBackfillEvents: Boolean
    )

    private data class Classification(
        val sampleTag: String,
        val qualityTag: String,
        val geometryTag: String,
        val shouldUseKalman: Boolean,
        val shouldUpdateAcceptedAnchor: Boolean,
        val shouldBackfillEvents: Boolean
    )

    private val rawBuffer = ArrayDeque<RawLocationFix>()
    private val kalmanTrack = KalmanLatLonFilter()

    private var mode: Mode = Mode.IDLE
    private var goodFixStreak: Int = 0
    private var recordingArmed: Boolean = false
    private var stillCounter: Int = 0

    private var lastAcceptedElapsedNanos: Long? = null
    private var lastAcceptedTimeMs: Long? = null
    private var lastAcceptedLat: Double? = null
    private var lastAcceptedLon: Double? = null

    private var lastSavedTimeMs: Long? = null
    private var lastSavedLat: Double? = null
    private var lastSavedLon: Double? = null

    val lastFixReceivedAtMs: Long
        get() = rawBuffer.peekLast()?.receivedAtMs ?: 0L

    fun reset() {
        rawBuffer.clear()
        kalmanTrack.reset()
        mode = Mode.IDLE
        goodFixStreak = 0
        recordingArmed = false
        stillCounter = 0
        lastAcceptedElapsedNanos = null
        lastAcceptedTimeMs = null
        lastAcceptedLat = null
        lastAcceptedLon = null
        lastSavedTimeMs = null
        lastSavedLat = null
        lastSavedLon = null
    }

    fun start() {
        reset()
        mode = Mode.ACQUIRE
    }

    fun onRawFix(fix: RawLocationFix): RuntimeState {
        rawBuffer.addLast(fix)
        while (rawBuffer.size > config.rawBufferMaxSize) rawBuffer.removeFirst()

        return runtimeState(
            lat = fix.lat,
            lon = fix.lon,
            accM = fix.accM,
            provider = fix.provider ?: "unknown",
            fixTimeMs = if (fix.fixTimeMs > 0L) fix.fixTimeMs else fix.receivedAtMs,
            receivedAtMs = fix.receivedAtMs,
            hasFix = true
        )
    }

    fun sample(tripId: Long, sampleTimeMs: Long): CaptureSample {
        val fix = rawBuffer.peekLast()
        if (fix == null) {
            mode = Mode.ACQUIRE
            val p = TrackPoint(
                tripId = tripId,
                timeMs = sampleTimeMs,
                lat = 0.0,
                lon = 0.0,
                accM = 9999.0,
                provider = "none+raw+ACQUIRE+QUICK+NO_FIX+NO_FIX+NO_FIX"
            )
            rememberSaved(p)
            return CaptureSample(
                point = p,
                state = runtimeStateFromPoint(p, sampleTimeMs),
                shouldBackfillEvents = false
            )
        }

        val isStale = sampleTimeMs - fix.receivedAtMs > config.staleFixAfterMs
        val lat = fix.lat
        val lon = fix.lon
        val accM = fix.accM

        updateArming(isStale = isStale, accM = accM)

        val classification = classifyFix(
            lat = lat,
            lon = lon,
            accM = accM,
            sampleTimeMs = sampleTimeMs,
            elapsedNanos = fix.elapsedNanos,
            isStale = isStale
        )

        val isStationary = mode == Mode.STILL || stillCounter >= 3
        val (latOut, lonOut) = if (classification.shouldUseKalman) {
            kalmanTrack.update(
                lat = lat,
                lon = lon,
                accM = accM,
                timeMs = sampleTimeMs,
                isStationary = isStationary
            )
        } else {
            lat to lon
        }

        if (classification.shouldUpdateAcceptedAnchor) {
            lastAcceptedElapsedNanos = if (fix.elapsedNanos > 0L) fix.elapsedNanos else lastAcceptedElapsedNanos
            lastAcceptedTimeMs = sampleTimeMs
            lastAcceptedLat = latOut
            lastAcceptedLon = lonOut
        }

        val modeTag = mode.name
        val armingTag = if (recordingArmed) "ARMED" else "QUICK"
        val filterTag = if (classification.shouldUseKalman) "kalman" else "raw"
        val provider = fix.provider ?: "fused"

        val p = TrackPoint(
            tripId = tripId,
            timeMs = sampleTimeMs,
            lat = latOut,
            lon = lonOut,
            accM = accM,
            provider = "$provider+$filterTag+$modeTag+$armingTag+${classification.sampleTag}+${classification.qualityTag}+${classification.geometryTag}"
        )
        rememberSaved(p)

        return CaptureSample(
            point = p,
            state = runtimeStateFromPoint(p, sampleTimeMs),
            shouldBackfillEvents = classification.shouldBackfillEvents
        )
    }

    private fun updateArming(isStale: Boolean, accM: Double) {
        if (!isStale && accM <= config.requiredAccM) goodFixStreak++ else goodFixStreak = 0

        if (!recordingArmed && goodFixStreak >= config.goodFixNeeded) {
            recordingArmed = true
            kalmanTrack.reset()
            lastAcceptedElapsedNanos = null
            lastAcceptedTimeMs = null
            lastAcceptedLat = null
            lastAcceptedLon = null
            lastSavedTimeMs = null
            lastSavedLat = null
            lastSavedLon = null
            stillCounter = 0
            mode = Mode.TRACK
        } else if (!recordingArmed) {
            mode = Mode.ACQUIRE
        }
    }

    private fun classifyFix(
        lat: Double,
        lon: Double,
        accM: Double,
        sampleTimeMs: Long,
        elapsedNanos: Long,
        isStale: Boolean
    ): Classification {
        if (lat == 0.0 && lon == 0.0) {
            return Classification(
                sampleTag = "NO_FIX",
                qualityTag = "NO_FIX",
                geometryTag = "NO_FIX",
                shouldUseKalman = false,
                shouldUpdateAcceptedAnchor = false,
                shouldBackfillEvents = false
            )
        }

        val sampleTag = if (isStale) "STALE" else "LIVE"
        val qualityTag = when {
            accM <= config.requiredAccM -> "GOOD_ACCURACY"
            accM <= config.usableAccM -> "USABLE_ACCURACY"
            accM <= 80.0 -> "LOW_ACCURACY"
            else -> "VERY_LOW_ACCURACY"
        }

        var geometryTag = "GEOMETRY_OK"
        var shouldUpdateAcceptedAnchor = !isStale

        val lastT = lastAcceptedTimeMs
        val lastLat = lastAcceptedLat
        val lastLon = lastAcceptedLon
        val isFirst = lastT == null || lastLat == null || lastLon == null

        if (!isFirst) {
            val dtSec = if (elapsedNanos > 0L && lastAcceptedElapsedNanos != null) {
                ((elapsedNanos - lastAcceptedElapsedNanos!!).coerceAtLeast(1L)).toDouble() / 1_000_000_000.0
            } else {
                ((sampleTimeMs - lastT!!).coerceAtLeast(1L)).toDouble() / 1000.0
            }

            val distM = haversineMeters(lastLat!!, lastLon!!, lat, lon)
            val speedMs = distM / dtSec
            val noiseRadiusM = maxOf(config.stationaryBaseRadiusM, accM * 1.4)

            geometryTag = when {
                speedMs > config.maxSpeedMs -> "SUSPECT_SPEED"
                distM > config.jumpM && accM > config.jumpAccM -> "SUSPECT_JUMP"
                else -> "GEOMETRY_OK"
            }

            if (distM < noiseRadiusM && speedMs < 1.2 && accM <= config.usableAccM && !isStale) {
                stillCounter++
            } else if (!isStale) {
                stillCounter = 0
            }

            if (stillCounter >= 8) mode = Mode.STILL
            if (stillCounter == 0 && mode == Mode.STILL) mode = Mode.TRACK

            if (geometryTag != "GEOMETRY_OK") shouldUpdateAcceptedAnchor = false
        }

        val shouldUseKalman = !isStale && accM <= config.kalmanUseAccM && geometryTag == "GEOMETRY_OK"
        val shouldBackfillEvents = !isStale && accM > 0.0 && accM <= 60.0 && geometryTag == "GEOMETRY_OK"

        return Classification(
            sampleTag = sampleTag,
            qualityTag = qualityTag,
            geometryTag = geometryTag,
            shouldUseKalman = shouldUseKalman,
            shouldUpdateAcceptedAnchor = shouldUpdateAcceptedAnchor,
            shouldBackfillEvents = shouldBackfillEvents
        )
    }

    private fun rememberSaved(point: TrackPoint) {
        lastSavedTimeMs = point.timeMs
        lastSavedLat = point.lat
        lastSavedLon = point.lon
    }

    private fun runtimeStateFromPoint(point: TrackPoint, savedAtMs: Long): RuntimeState {
        return runtimeState(
            lat = point.lat,
            lon = point.lon,
            accM = point.accM,
            provider = point.provider,
            fixTimeMs = point.timeMs,
            receivedAtMs = lastFixReceivedAtMs,
            savedAtMs = savedAtMs,
            hasFix = point.lat != 0.0 || point.lon != 0.0
        )
    }

    private fun runtimeState(
        lat: Double,
        lon: Double,
        accM: Double,
        provider: String,
        fixTimeMs: Long,
        receivedAtMs: Long,
        savedAtMs: Long = 0L,
        hasFix: Boolean
    ): RuntimeState {
        return RuntimeState(
            hasFix = hasFix,
            lat = lat,
            lon = lon,
            accM = accM,
            provider = provider,
            fixTimeMs = fixTimeMs,
            receivedAtMs = receivedAtMs,
            savedAtMs = savedAtMs,
            mode = mode,
            isRecordingArmed = recordingArmed,
            rawBufferSize = rawBuffer.size
        )
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
