package com.oropeza.urbanapp.asd.location

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.oropeza.urbanapp.asd.AsdGraph
import com.oropeza.urbanapp.asd.data.local.TrackPoint
import com.oropeza.urbanapp.asd.sync.AsdCloudSyncWorker
import com.oropeza.urbanapp.asd.sync.AsdSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import com.oropeza.urbanapp.asd.live.LiveDevicePublisher
import com.oropeza.urbanapp.asd.live.LiveEventBus

class TrackingService : Service() {

    private val NOTIF_ID = 1001
    private val CHANNEL_ID = "tracking_channel"

    companion object {
        const val ACTION_START = "TRACK_START"
        const val ACTION_STOP = "TRACK_STOP"
        const val EXTRA_TRIP_ID = "trip_id"
        const val TAG = "TrackingService"
        var isRunning = false
            private set
    }

    private val requiredAccM = 25.0
    private val usableAccM = 45.0
    private val kalmanUseAccM = 45.0
    private val goodFixNeeded = 3

    private val maxSpeedMs = 45.0
    private val jumpM = 45.0
    private val jumpAccM = 25.0

    private val minSaveDistanceM = 8.0
    private val maxSaveIntervalMs = 2_000L

    private enum class Mode { ACQUIRE, TRACK, STILL }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var currentTripId: Long? = null

    private lateinit var gps: LocationProvider
    private lateinit var headingProvider: HeadingProvider
    private lateinit var livePublisher: LiveDevicePublisher
    private var liveEventsJob: Job? = null
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
    private var currentMode: Mode? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        gps = LocationProvider(this)
        headingProvider = HeadingProvider(this)
        headingProvider.start()
        livePublisher = LiveDevicePublisher(this)
        createNotificationChannel()

        val notification = buildNotification("Rastreo ASD activo")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_STICKY

        when (intent.action) {
            ACTION_START -> {
                val tripId = intent.getLongExtra(EXTRA_TRIP_ID, -1L)
                if (tripId > 0) {
                    if (job != null && currentTripId != tripId) stopTracking()
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
        if (job != null && currentTripId == tripId) return

        currentTripId = tripId
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

        job = scope.launch {
            val trip = AsdGraph.db.tripDao().getByIdOnce(tripId)
            if (trip != null) livePublisher.publishTripStart(trip)

            liveEventsJob?.cancel()
            liveEventsJob = launch {
                LiveEventBus.events.collect { signal ->
                    if (signal.tripId == tripId) {
                        val currentTrip = AsdGraph.db.tripDao().getByIdOnce(tripId) ?: return@collect
                        livePublisher.publishEvent(currentTrip, signal)
                    }
                }
            }
            if (!gps.hasPermission()) {
                Log.w(TAG, "Sin permisos de ubicación. No se inicia tracking.")
                return@launch
            }

            // ✅ Sync loops
            launch {
                while (true) {
                    delay(30_000L)
                    val lastP = lastSavedLat?.let { lat -> lastSavedLon?.let { lon -> lastSavedTimeMs?.let { t ->
                        TrackPoint(tripId = tripId, timeMs = t, lat = lat, lon = lon, accM = 0.0, provider = "SNAPSHOT")
                    } } }
                    AsdSyncManager.enqueueDeviceStatus(applicationContext, tripId, lastP)
                }
            }

            launch {
                while (true) {
                    delay(60_000L)
                    AsdSyncManager.enqueueTrackSummary(applicationContext, tripId)
                }
            }

            switchMode(Mode.ACQUIRE)
            while (true) delay(1000L)
        }

        job?.invokeOnCompletion {
            job = null
            currentTripId = null
            Log.i(TAG, "Tracking job finished")
        }
    }

    private fun stopTracking() {
        currentTripId?.let { tripId ->
            scope.launch {
                val trip = AsdGraph.db.tripDao().getByIdOnce(tripId)
                if (trip != null) livePublisher.publishTripEnd(trip)
            }
        }

        liveEventsJob?.cancel()
        liveEventsJob = null
        updatesJob?.cancel()
        updatesJob = null
        job?.cancel()
        job = null
        currentTripId = null
        kalmanTrack.reset()
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

    private fun switchMode(mode: Mode) {
        if (currentMode == mode && updatesJob != null) return
        currentMode = mode

        updatesJob?.cancel()
        updatesJob = scope.launch {
            val tripId = currentTripId ?: return@launch
            val params = when (mode) {
                Mode.ACQUIRE -> Params(1000L, 500L, 0f, 0L, true)
                Mode.TRACK -> Params(2000L, 1000L, 0f, 0L, true)
                Mode.STILL -> Params(2000L, 1000L, 0f, 0L, true)
            }

            gps.locationUpdates(
                intervalMs = params.intervalMs,
                minUpdateMs = params.minUpdateMs,
                minDistanceM = params.minDistanceM,
                maxWaitTimeMs = params.maxWaitTimeMs,
                highAccuracy = params.highAccuracy
            )
                .catch { e -> Log.e(TAG, "Error en locationUpdates()", e) }
                .collect { loc ->
                        handleLocation(
                            tripId = tripId,
                            lat = loc.latitude,
                            lon = loc.longitude,
                            accM = loc.accuracy.toDouble(),
                            altM = if (loc.hasAltitude()) loc.altitude else 0.0,
                            timeFromLoc = loc.time,
                            elapsedNanos = loc.elapsedRealtimeNanos,
                            provider = loc.provider
                        )
                }
        }

        Log.i(TAG, "Switched mode -> $mode")
    }

    private data class Params(
        val intervalMs: Long,
        val minUpdateMs: Long,
        val minDistanceM: Float,
        val maxWaitTimeMs: Long,
        val highAccuracy: Boolean
    )

    private fun handleLocation(
        tripId: Long,
        lat: Double,
        lon: Double,
        accM: Double,
        altM: Double,
        timeFromLoc: Long,
        elapsedNanos: Long,
        provider: String?
    ) {
        val timeMs = if (timeFromLoc > 0L) timeFromLoc else System.currentTimeMillis()

        val lastEN = lastAcceptedElapsedNanos
        if (lastEN != null && elapsedNanos > 0L && elapsedNanos == lastEN) return

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
                Log.i(TAG, "Recording ARMED rápido (acc <= $requiredAccM)")
                switchMode(Mode.TRACK)
            } else if (accM > usableAccM) {
                if (currentMode != Mode.ACQUIRE) switchMode(Mode.ACQUIRE)
                return
            }
        }

        if (accM > 80.0) return

        val lastT = lastAcceptedTimeMs
        val lastLat = lastAcceptedLat
        val lastLon = lastAcceptedLon
        val isFirst = (lastT == null || lastLat == null || lastLon == null)
        var currentSpeedMs: Double? = null

        if (!isFirst) {
            val dtSec = if (elapsedNanos > 0L && lastAcceptedElapsedNanos != null) {
                ((elapsedNanos - lastAcceptedElapsedNanos!!).coerceAtLeast(1L)).toDouble() / 1_000_000_000.0
            } else {
                ((timeMs - lastT!!).coerceAtLeast(1L)).toDouble() / 1000.0
            }

            val distM = haversineMeters(lastLat!!, lastLon!!, lat, lon)
            val speedMs = distM / dtSec
            currentSpeedMs = speedMs

            if (speedMs > maxSpeedMs) return
            if (distM > jumpM && accM > jumpAccM) return

            val stillRadius = kotlin.math.max(12.0, accM * 1.5)
            if (distM < stillRadius && speedMs < 1.2) {
                stillCounter++
            } else if (distM > kotlin.math.max(20.0, accM * 2.0) || speedMs > 2.5) {
                stillCounter = 0
            }

            if (stillCounter >= 8 && currentMode != Mode.STILL) switchMode(Mode.STILL)
            if (stillCounter == 0 && currentMode == Mode.STILL) switchMode(Mode.TRACK)
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

        val canSave = accM <= usableAccM && shouldSaveTrackPoint(latF, lonF, accM, timeMs)
        if (canSave) {
            val modeTag = currentMode?.name ?: "NA"
            val qualityTag = if (recordingArmed) "ARMED" else "QUICK"
            val eval = GpsQualityEvaluator.evaluate(latF, lonF, accM, provider)
            val p = TrackPoint(
                tripId = tripId,
                timeMs = timeMs,
                lat = latF,
                lon = lonF,
                altM = altM,
                accM = accM,
                provider = ((provider ?: "fused") + if (useKalman) "+kalman" else "+raw") + "+$modeTag+$qualityTag+${eval.quality.name}"
            )
            lastSavedTimeMs = timeMs
            lastSavedLat = latF
            lastSavedLon = lonF
            scope.launch {
                try {
                    AsdGraph.db.trackDao().insert(p)
                    val trip = AsdGraph.db.tripDao().getByIdOnce(tripId)
                    if (trip != null) {
                        livePublisher.maybePublishLocation(
                            trip = trip,
                            point = p,
                            heading = headingProvider.getHeadingDeg(timeMs),
                            speedMs = currentSpeedMs,
                            gpsStatus = gpsStatusFromAccuracy(accM)
                        )
                    }
                    val completed = AsdGraph.repo.completePendingGpsEvents(tripId, p)
                    if (completed > 0) Log.i(TAG, "GPS backfill aplicado a $completed evento(s)")
                } catch (e: Exception) {
                    Log.e(TAG, "Error insertando TrackPoint o completando GPS pendiente", e)
                }
            }
        }
    }

    private fun shouldSaveTrackPoint(
        lat: Double,
        lon: Double,
        accM: Double,
        timeMs: Long
    ): Boolean {
        val lastLat = lastSavedLat
        val lastLon = lastSavedLon
        val lastTime = lastSavedTimeMs

        if (lastLat == null || lastLon == null || lastTime == null) {
            return true
        }

        val elapsedMs = timeMs - lastTime
        val results = FloatArray(1)
        android.location.Location.distanceBetween(
            lastLat,
            lastLon,
            lat,
            lon,
            results
        )
        val distanceM = results[0].toDouble()

        val minTimeMovingMs = 5_000L
        val minDistanceMovingM = 5.0
        val maxStillIntervalMs = 30_000L
        val maxForceIntervalMs = 15_000L

        if (elapsedMs >= maxForceIntervalMs) {
            return true
        }

        if (currentMode == Mode.STILL) {
            return elapsedMs >= maxStillIntervalMs
        }

        if (elapsedMs >= minTimeMovingMs && distanceM >= minDistanceMovingM) {
            return true
        }

        return false
    }

    private fun gpsStatusFromAccuracy(accM: Double): String = when {
        accM <= 25.0 -> "OK"
        accM <= 45.0 -> "WEAK"
        else -> "POOR"
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
