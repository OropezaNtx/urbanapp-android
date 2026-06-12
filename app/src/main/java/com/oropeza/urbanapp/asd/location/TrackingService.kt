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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class TrackingService : Service() {
    private val NOTIF_ID = 1001
    private val CHANNEL_ID = "tracking_channel"

    companion object {
        const val ACTION_START = "TRACK_START"
        const val ACTION_STOP = "TRACK_STOP"
        const val EXTRA_TRIP_ID = "trip_id"
        private const val TAG = "TrackingService"
    }

    private val gpsConfig = GpsEngine.Config()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var currentTripId: Long? = null
    private lateinit var gps: LocationProvider
    private lateinit var headingProvider: HeadingProvider
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
    private var updatesJob: Job? = null
    private var currentMode: GpsEngine.Mode? = null

    override fun onCreate() {
        super.onCreate()
        gps = LocationProvider(this)
        headingProvider = HeadingProvider(this)
        headingProvider.start()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Tracking ASD active"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val tripId = intent.getLongExtra(EXTRA_TRIP_ID, -1L)
                if (tripId > 0) {
                    if (job != null && currentTripId != tripId) stopTracking()
                    startTracking(tripId)
                }
            }
            ACTION_STOP -> stopTracking()
        }
        return START_STICKY
    }

    private fun startTracking(tripId: Long) {
        if (job != null && currentTripId == tripId) return
        currentTripId = tripId
        resetState()
        job = scope.launch {
            if (!gps.hasPermission()) return@launch
            switchMode(GpsEngine.Mode.ACQUIRE)
            while (true) delay(1000L)
        }
        job?.invokeOnCompletion { job = null; currentTripId = null }
    }

    private fun resetState() {
        recordingArmed = false
        goodFixStreak = 0
        stillCounter = 0
        lastAcceptedElapsedNanos = null
        lastAcceptedTimeMs = null
        lastAcceptedLat = null
        lastAcceptedLon = null
        lastSavedTimeMs = null
        lastSavedLat = null
        lastSavedLon = null
        kalmanTrack.reset()
        currentMode = null
        updatesJob = null
    }

    private fun stopTracking() {
        updatesJob?.cancel()
        updatesJob = null
        job?.cancel()
        job = null
        currentTripId = null
        resetState()
        try { headingProvider.stop() } catch (_: Exception) {}
        stopSelf()
    }

    override fun onDestroy() {
        stopTracking()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun switchMode(mode: GpsEngine.Mode) {
        if (currentMode == mode && updatesJob != null) return
        currentMode = mode
        updatesJob?.cancel()
        updatesJob = scope.launch {
            val tripId = currentTripId ?: return@launch
            val params = when (mode) {
                GpsEngine.Mode.ACQUIRE -> Params(700L, 350L, 0f, 0L, true)
                GpsEngine.Mode.TRACK -> Params(2000L, 1000L, 0f, 0L, true)
                GpsEngine.Mode.STILL -> Params(2000L, 1000L, 0f, 0L, true)
            }
            gps.locationUpdates(params.intervalMs, params.minUpdateMs, params.minDistanceM, params.maxWaitTimeMs, params.highAccuracy)
                .catch { e -> Log.e(TAG, "locationUpdates error", e) }
                .collect { loc ->
                    handleLocation(tripId, loc.latitude, loc.longitude, loc.accuracy.toDouble(), loc.time, loc.elapsedRealtimeNanos, loc.provider)
                }
        }
    }

    private data class Params(
        val intervalMs: Long,
        val minUpdateMs: Long,
        val minDistanceM: Float,
        val maxWaitTimeMs: Long,
        val highAccuracy: Boolean
    )

    private fun handleLocation(tripId: Long, lat: Double, lon: Double, accM: Double, timeFromLoc: Long, elapsedNanos: Long, provider: String?) {
        val timeMs = if (timeFromLoc > 0L) timeFromLoc else System.currentTimeMillis()
        val lastEN = lastAcceptedElapsedNanos
        if (lastEN != null && elapsedNanos > 0L && elapsedNanos == lastEN) return
        val mode = currentMode ?: GpsEngine.Mode.ACQUIRE
        val result = GpsEngine.evaluate(
            GpsEngine.Input(
                lat, lon, accM, provider, timeMs, elapsedNanos,
                lastAcceptedLat, lastAcceptedLon, lastAcceptedTimeMs, lastAcceptedElapsedNanos,
                lastSavedTimeMs, recordingArmed, goodFixStreak, stillCounter, mode
            ),
            gpsConfig
        )
        goodFixStreak = result.nextGoodFixStreak
        stillCounter = result.nextStillCounter
        when (result.decision) {
            GpsEngine.Decision.HOLD -> { updateNotification("GPS preparing ${result.quality.label} ${accM.toInt()}m"); return }
            GpsEngine.Decision.REJECT -> { updateNotification("GPS rejected ${result.reason}"); return }
            else -> Unit
        }
        if (result.shouldArm) {
            recordingArmed = true
            kalmanTrack.reset()
            lastAcceptedElapsedNanos = null
            lastAcceptedTimeMs = null
            lastAcceptedLat = null
            lastAcceptedLon = null
            lastSavedTimeMs = null
            lastSavedLat = null
            lastSavedLon = null
        }
        if (currentMode != result.nextMode) switchMode(result.nextMode)
        val isStationary = result.nextMode == GpsEngine.Mode.STILL || stillCounter >= 3
        val candidate = if (result.shouldUseKalman) kalmanTrack.update(lat, lon, accM, timeMs, isStationary) else lat to lon
        val savedLat = lastSavedLat
        val savedLon = lastSavedLon
        val filtered = if (savedLat != null && savedLon != null && result.decision == GpsEngine.Decision.SMOOTH) {
            GpsEngine.clampStep(savedLat, savedLon, candidate.first, candidate.second, result.maxStepM)
        } else candidate
        lastAcceptedElapsedNanos = if (elapsedNanos > 0L) elapsedNanos else lastAcceptedElapsedNanos
        lastAcceptedTimeMs = timeMs
        lastAcceptedLat = filtered.first
        lastAcceptedLon = filtered.second
        if (result.shouldSave) savePoint(tripId, timeMs, filtered.first, filtered.second, accM, provider, result)
    }

    private fun savePoint(tripId: Long, timeMs: Long, lat: Double, lon: Double, accM: Double, provider: String?, result: GpsEngine.Output) {
        val modeTag = currentMode?.name ?: "NA"
        val p = TrackPoint(
            tripId = tripId,
            timeMs = timeMs,
            lat = lat,
            lon = lon,
            accM = accM,
            provider = ((provider ?: "fused") + if (result.shouldUseKalman) "+kalman" else "+raw") + "+$modeTag+ARMED+${result.quality.quality.name}+${result.decision.name}"
        )
        lastSavedTimeMs = timeMs
        lastSavedLat = lat
        lastSavedLon = lon
        updateNotification("GPS ${result.quality.label} ${accM.toInt()}m $modeTag ${result.decision.name.lowercase()}")
        scope.launch {
            try {
                AsdGraph.db.trackDao().insert(p)
                AsdGraph.repo.completePendingGpsEvents(tripId, p)
            } catch (e: Exception) {
                Log.e(TAG, "TrackPoint insert error", e)
            }
        }
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(text))
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
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Tracking", NotificationManager.IMPORTANCE_LOW))
        }
    }
}
