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

    private val requiredAccM = 25.0
    private val usableAccM = 45.0
    private val kalmanUseAccM = 45.0
    private val goodFixNeeded = 1

    private val maxSpeedMs = 45.0
    private val jumpM = 45.0
    private val jumpAccM = 25.0

    private val minSaveDistanceM = 4.0
    private val stationaryBaseRadiusM = 14.0
    private val maxSaveIntervalMs = 2_000L

    private enum class Mode { ACQUIRE, TRACK, STILL }

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
                delay(2000L)
                val tripId = currentTripId ?: continue
                val fix = latestFix ?: continue
                
                saveTrackPointIfNeeded(tripId, fix)
            }
        }
    }

    private fun startWatchdogLoop() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            while (true) {
                delay(3000L)
                if (System.currentTimeMillis() - latestFixReceivedAtMs > 6000L) {
                    restartGpsUpdates()
                }
            }
        }
    }

    private suspend fun saveTrackPointIfNeeded(tripId: Long, fix: LatestFix) {
        val lat = fix.lat
        val lon = fix.lon
        val accM = fix.accM
        val timeMs = System.currentTimeMillis()
        val elapsedNanos = fix.elapsedNanos
        val provider = fix.provider

        if (accM > 80.0) return

        if (accM <= requiredAccM) goodFixStreak++ else goodFixStreak = 0

        if (!recordingArmed) {
            if (goodFixStreak >= goodFixNeeded) {
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
            } else if (accM > usableAccM) {
                currentMode = Mode.ACQUIRE
                return
            }
        }

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

            if (speedMs > maxSpeedMs) return
            if (distM > jumpM && accM > jumpAccM) return

            val noiseRadiusM = maxOf(stationaryBaseRadiusM, accM * 1.4)

            if (distM < noiseRadiusM && speedMs < 1.2 && accM <= usableAccM) {
                stillCounter++
            } else {
                stillCounter = 0
            }
            
            if (stillCounter >= 8) currentMode = Mode.STILL
            if (stillCounter == 0 && currentMode == Mode.STILL) currentMode = Mode.TRACK
        }

        val useKalman = accM <= kalmanUseAccM
        val isStationary = (currentMode == Mode.STILL) || (stillCounter >= 3)
        val (latF, lonF) = if (useKalman) {
            kalmanTrack.update(lat = lat, lon = lon, accM = accM, timeMs = timeMs, isStationary = isStationary)
        } else {
            lat to lon
        }

        lastAcceptedElapsedNanos = if (elapsedNanos > 0L) elapsedNanos else lastAcceptedElapsedNanos
        lastAcceptedTimeMs = timeMs
        lastAcceptedLat = latF
        lastAcceptedLon = lonF

        if (latF != 0.0 && lonF != 0.0) {
            val modeTag = currentMode?.name ?: "NA"
            val qualityTag = if (recordingArmed) "ARMED" else "QUICK"
            val isStale = System.currentTimeMillis() - fix.receivedAtMs > 6000L
            val staleTag = if (isStale) "STALE" else "LIVE"

            val p = TrackPoint(
                tripId = tripId,
                timeMs = timeMs,
                lat = latF,
                lon = lonF,
                accM = accM,
                provider = ((provider ?: "fused") + if (useKalman) "+kalman" else "+raw") + "+$modeTag+$qualityTag+$staleTag"
            )
            lastSavedTimeMs = timeMs
            lastSavedLat = latF
            lastSavedLon = lonF
            
            try {
                AsdGraph.db.trackDao().insert(p)
                _runtimeGpsState.value = _runtimeGpsState.value.copy(savedAtMs = System.currentTimeMillis())
                val completed = AsdGraph.repo.completePendingGpsEvents(tripId, p)
                if (completed > 0) Log.i(TAG, "GPS backfill aplicado a $completed evento(s)")
            } catch (e: Exception) {
                Log.e(TAG, "Error insertando TrackPoint o completando GPS pendiente", e)
            }
        }
    }

    private fun shouldSaveTrackPoint(
        lat: Double,
        lon: Double,
        timeMs: Long,
        accM: Double
    ): Boolean {
        val savedTime = lastSavedTimeMs
        val savedLat = lastSavedLat
        val savedLon = lastSavedLon
        if (savedTime == null || savedLat == null || savedLon == null) return true

        val distanceM = haversineMeters(savedLat, savedLon, lat, lon)
        val elapsedMs = (timeMs - savedTime).coerceAtLeast(0L)

        val noiseRadiusM = maxOf(stationaryBaseRadiusM, accM * 1.4)
        val isStill = currentMode == Mode.STILL || stillCounter >= 3

        if (isStill && distanceM < noiseRadiusM) {
            return false
        }

        return distanceM >= minSaveDistanceM || elapsedMs >= maxSaveIntervalMs
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
