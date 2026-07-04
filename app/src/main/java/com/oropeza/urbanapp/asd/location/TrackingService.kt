package com.oropeza.urbanapp.asd.location

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.oropeza.urbanapp.asd.AsdGraph
import com.oropeza.urbanapp.asd.data.local.TrackPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class TrackingService : Service() {

    private val NOTIF_ID = 1001
    private val CHANNEL_ID = "tracking_channel"

    companion object {
        const val ACTION_START = "TRACK_START"
        const val ACTION_STOP = "TRACK_STOP"
        const val EXTRA_TRIP_ID = "trip_id"
        private const val TAG = "TrackingService"
        var isRunning = false
            private set

        data class RuntimeGpsState(
            val hasFix: Boolean = false,
            val lat: Double = 0.0,
            val lon: Double = 0.0,
            val accM: Double = 0.0,
            val provider: String = "none",
            val fixTimeMs: Long = 0L,
            val receivedAtMs: Long = 0L,
            val savedAtMs: Long = 0L,
            val mode: String = "IDLE",
            val isRecordingArmed: Boolean = false
        )

        private val _runtimeGpsState = MutableStateFlow(RuntimeGpsState())
        val runtimeGpsState: StateFlow<RuntimeGpsState> = _runtimeGpsState.asStateFlow()
    }

    private data class LatestFix(
        val lat: Double,
        val lon: Double,
        val accM: Double,
        val timeMs: Long,
        val elapsedNanos: Long,
        val provider: String?,
        val receivedAtMs: Long
    )

    private enum class Mode { ACQUIRE, TRACK, STILL }

    private data class GpsClassification(
        val sampleTag: String,
        val qualityTag: String,
        val geometryTag: String,
        val shouldUseKalman: Boolean,
        val shouldUpdateAcceptedAnchor: Boolean,
        val shouldBackfillEvents: Boolean
    )

    private val requiredAccM = 25.0
    private val usableAccM = 45.0
    private val kalmanUseAccM = 45.0
    private val goodFixNeeded = 1

    private val maxSpeedMs = 45.0
    private val jumpM = 45.0
    private val jumpAccM = 25.0
    private val stationaryBaseRadiusM = 14.0

    private val fixedCaptureIntervalMs = 2_000L
    private val staleFixAfterMs = 6_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var mainJob: Job? = null
    private var gpsUpdatesJob: Job? = null
    private var saverJob: Job? = null
    private var watchdogJob: Job? = null

    private var currentTripId: Long? = null

    private lateinit var gps: LocationProvider
    private lateinit var headingProvider: HeadingProvider

    private var latestFix: LatestFix? = null
    private var latestFixReceivedAtMs: Long = 0L

    private var goodFixStreak = 0
    private var recordingArmed = false

    private var lastAcceptedElapsedNanos: Long? = null
    private var lastAcceptedTimeMs: Long? = null
    private var lastAcceptedLat: Double? = null
    private var lastAcceptedLon: Double? = null

    private var lastSavedTimeMs: Long? = null
    private var lastSavedLat: Double? = null
    private var lastSavedLon: Double? = null

    private var stillCounter = 0
    private val kalmanTrack = KalmanLatLonFilter()
    private var currentMode: Mode? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        gps = LocationProvider(this)
        headingProvider = HeadingProvider(this)
        headingProvider.start()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Tracking ASD activo"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_STICKY

        when (intent.action) {
            ACTION_START -> {
                val tripId = intent.getLongExtra(EXTRA_TRIP_ID, -1L)
                if (tripId > 0) {
                    if (currentTripId != null && currentTripId != tripId) stopTracking()
                    startTracking(tripId)
                } else {
                    Log.w(TAG, "ACTION_START sin tripId válido")
                }
            }
            ACTION_STOP -> stopTracking()
        }

        return START_STICKY
    }

    private fun startTracking(tripId: Long) {
        if (currentTripId == tripId) return

        resetTrackingState()
        currentTripId = tripId
        currentMode = Mode.ACQUIRE

        mainJob = scope.launch {
            if (!gps.hasPermission()) {
                Log.w(TAG, "Sin permisos de ubicación. No se inicia tracking.")
                return@launch
            }

            startGpsUpdates()
            startSaverLoop()
            startWatchdogLoop()
        }
    }

    private fun resetTrackingState() {
        mainJob?.cancel()
        gpsUpdatesJob?.cancel()
        saverJob?.cancel()
        watchdogJob?.cancel()

        _runtimeGpsState.value = RuntimeGpsState(mode = "IDLE")

        recordingArmed = false
        goodFixStreak = 0
        stillCounter = 0
        latestFix = null
        latestFixReceivedAtMs = 0L
        lastAcceptedElapsedNanos = null
        lastAcceptedTimeMs = null
        lastAcceptedLat = null
        lastAcceptedLon = null
        lastSavedTimeMs = null
        lastSavedLat = null
        lastSavedLon = null
        kalmanTrack.reset()
        currentMode = null
    }

    private fun startGpsUpdates() {
        gpsUpdatesJob?.cancel()
        gpsUpdatesJob = scope.launch {
            Log.i(TAG, "Starting GPS updates listener")
            gps.locationUpdates(
                intervalMs = 1000L,
                minUpdateMs = 500L,
                minDistanceM = 0f,
                maxWaitTimeMs = 0L,
                highAccuracy = true
            ).catch { e ->
                Log.e(TAG, "Error en locationUpdates()", e)
            }.collect { loc ->
                val now = System.currentTimeMillis()
                latestFix = LatestFix(
                    lat = loc.latitude,
                    lon = loc.longitude,
                    accM = loc.accuracy.toDouble(),
                    timeMs = loc.time,
                    elapsedNanos = loc.elapsedRealtimeNanos,
                    provider = loc.provider,
                    receivedAtMs = now
                )
                latestFixReceivedAtMs = now

                _runtimeGpsState.value = _runtimeGpsState.value.copy(
                    hasFix = true,
                    lat = loc.latitude,
                    lon = loc.longitude,
                    accM = loc.accuracy.toDouble(),
                    provider = loc.provider ?: "unknown",
                    fixTimeMs = if (loc.time > 0L) loc.time else now,
                    receivedAtMs = now,
                    mode = currentMode?.name ?: "NA",
                    isRecordingArmed = recordingArmed
                )
            }
        }
    }

    private fun restartGpsUpdates() {
        val age = System.currentTimeMillis() - latestFixReceivedAtMs
        Log.i(TAG, "GPS watchdog restarting updates. Last fix age: ${age}ms")
        startGpsUpdates()
    }

    private fun startSaverLoop() {
        saverJob?.cancel()
        saverJob = scope.launch {
            while (true) {
                delay(fixedCaptureIntervalMs)
                val tripId = currentTripId ?: continue
                saveTrackPointSample(tripId, latestFix)
            }
        }
    }

    private fun startWatchdogLoop() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            while (true) {
                delay(3000L)
                if (System.currentTimeMillis() - latestFixReceivedAtMs > staleFixAfterMs) {
                    restartGpsUpdates()
                }
            }
        }
    }

    private suspend fun saveTrackPointSample(tripId: Long, fix: LatestFix?) {
        val timeMs = System.currentTimeMillis()

        if (fix == null) {
            currentMode = Mode.ACQUIRE
            persistTrackPoint(
                tripId = tripId,
                point = TrackPoint(
                    tripId = tripId,
                    timeMs = timeMs,
                    lat = 0.0,
                    lon = 0.0,
                    accM = 9999.0,
                    provider = "none+raw+ACQUIRE+NO_FIX+NO_FIX+NO_FIX"
                ),
                shouldBackfillEvents = false
            )
            return
        }

        val ageMs = timeMs - fix.receivedAtMs
        val isStale = ageMs > staleFixAfterMs
        val lat = fix.lat
        val lon = fix.lon
        val accM = fix.accM
        val provider = fix.provider ?: "fused"

        if (!isStale && accM <= requiredAccM) goodFixStreak++ else goodFixStreak = 0

        if (!recordingArmed && goodFixStreak >= goodFixNeeded) {
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
            currentMode = Mode.TRACK
            Log.i(TAG, "Recording ARMED (acc <= $requiredAccM)")
        } else if (!recordingArmed) {
            currentMode = Mode.ACQUIRE
        }

        val classification = classifyFix(
            lat = lat,
            lon = lon,
            accM = accM,
            timeMs = timeMs,
            elapsedNanos = fix.elapsedNanos,
            isStale = isStale
        )

        val useKalman = classification.shouldUseKalman
        val isStationary = (currentMode == Mode.STILL) || (stillCounter >= 3)
        val (latOut, lonOut) = if (useKalman) {
            kalmanTrack.update(
                lat = lat,
                lon = lon,
                accM = accM,
                timeMs = timeMs,
                isStationary = isStationary
            )
        } else {
            lat to lon
        }

        if (classification.shouldUpdateAcceptedAnchor) {
            lastAcceptedElapsedNanos = if (fix.elapsedNanos > 0L) fix.elapsedNanos else lastAcceptedElapsedNanos
            lastAcceptedTimeMs = timeMs
            lastAcceptedLat = latOut
            lastAcceptedLon = lonOut
        }

        val modeTag = currentMode?.name ?: "NA"
        val armingTag = if (recordingArmed) "ARMED" else "QUICK"
        val filterTag = if (useKalman) "kalman" else "raw"

        val p = TrackPoint(
            tripId = tripId,
            timeMs = timeMs,
            lat = latOut,
            lon = lonOut,
            accM = accM,
            provider = "$provider+$filterTag+$modeTag+$armingTag+${classification.sampleTag}+${classification.qualityTag}+${classification.geometryTag}"
        )

        lastSavedTimeMs = timeMs
        lastSavedLat = latOut
        lastSavedLon = lonOut

        persistTrackPoint(
            tripId = tripId,
            point = p,
            shouldBackfillEvents = classification.shouldBackfillEvents
        )
    }

    private fun classifyFix(
        lat: Double,
        lon: Double,
        accM: Double,
        timeMs: Long,
        elapsedNanos: Long,
        isStale: Boolean
    ): GpsClassification {
        if (lat == 0.0 && lon == 0.0) {
            return GpsClassification(
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
            accM <= requiredAccM -> "GOOD_ACCURACY"
            accM <= usableAccM -> "USABLE_ACCURACY"
            accM <= 80.0 -> "LOW_ACCURACY"
            else -> "VERY_LOW_ACCURACY"
        }

        var geometryTag = "GEOMETRY_OK"
        var shouldUpdateAcceptedAnchor = !isStale

        val lastT = lastAcceptedTimeMs
        val lastLat = lastAcceptedLat
        val lastLon = lastAcceptedLon
        val isFirst = (lastT == null || lastLat == null || lastLon == null)

        if (!isFirst) {
            val dtSec = if (elapsedNanos > 0L && lastAcceptedElapsedNanos != null) {
                ((elapsedNanos - lastAcceptedElapsedNanos!!).coerceAtLeast(1L)).toDouble() / 1_000_000_000.0
            } else {
                ((timeMs - lastT!!).coerceAtLeast(1L)).toDouble() / 1000.0
            }

            val distM = haversineMeters(lastLat!!, lastLon!!, lat, lon)
            val speedMs = distM / dtSec
            val noiseRadiusM = maxOf(stationaryBaseRadiusM, accM * 1.4)

            geometryTag = when {
                speedMs > maxSpeedMs -> "SUSPECT_SPEED"
                distM > jumpM && accM > jumpAccM -> "SUSPECT_JUMP"
                else -> "GEOMETRY_OK"
            }

            if (distM < noiseRadiusM && speedMs < 1.2 && accM <= usableAccM && !isStale) {
                stillCounter++
            } else if (!isStale) {
                stillCounter = 0
            }

            if (stillCounter >= 8) currentMode = Mode.STILL
            if (stillCounter == 0 && currentMode == Mode.STILL) currentMode = Mode.TRACK

            if (geometryTag != "GEOMETRY_OK") {
                shouldUpdateAcceptedAnchor = false
            }
        }

        val shouldUseKalman = !isStale && accM <= kalmanUseAccM && geometryTag == "GEOMETRY_OK"
        val shouldBackfillEvents = !isStale && accM > 0.0 && accM <= 60.0 && geometryTag == "GEOMETRY_OK"

        return GpsClassification(
            sampleTag = sampleTag,
            qualityTag = qualityTag,
            geometryTag = geometryTag,
            shouldUseKalman = shouldUseKalman,
            shouldUpdateAcceptedAnchor = shouldUpdateAcceptedAnchor,
            shouldBackfillEvents = shouldBackfillEvents
        )
    }

    private suspend fun persistTrackPoint(
        tripId: Long,
        point: TrackPoint,
        shouldBackfillEvents: Boolean
    ) {
        try {
            AsdGraph.db.trackDao().insert(point)
            _runtimeGpsState.value = _runtimeGpsState.value.copy(
                lat = point.lat,
                lon = point.lon,
                accM = point.accM,
                provider = point.provider,
                savedAtMs = System.currentTimeMillis(),
                mode = currentMode?.name ?: "NA",
                isRecordingArmed = recordingArmed
            )

            if (shouldBackfillEvents) {
                val completed = AsdGraph.repo.completePendingGpsEvents(tripId, point)
                if (completed > 0) Log.i(TAG, "GPS backfill aplicado a $completed evento(s)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error insertando TrackPoint o completando GPS pendiente", e)
        }
    }

    private fun stopTracking() {
        resetTrackingState()
        currentTripId = null
        try { headingProvider.stop() } catch (_: Exception) {}
        stopSelf()
    }

    override fun onDestroy() {
        isRunning = false
        stopTracking()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

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

    private fun buildNotification(text: String): android.app.Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("UrbanApp ASD")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Tracking",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }
}
