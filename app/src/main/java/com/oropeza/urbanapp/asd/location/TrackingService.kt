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
    }

    private val requiredAccM = 25.0
    private val usableAccM = 45.0
    private val kalmanUseAccM = 45.0
    private val goodFixNeeded = 1

    private val maxSpeedMs = 45.0
    private val jumpM = 45.0
    private val jumpAccM = 25.0

    // Regla de negocio: intentar conservar un punto de ruta cada 2 segundos.
    private val minSaveDistanceM = 0.0
    private val maxSaveIntervalMs = 2_000L

    private enum class Mode { ACQUIRE, TRACK, STILL }

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
    private var currentMode: Mode? = null

    override fun onCreate() {
        super.onCreate()
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
            if (!gps.hasPermission()) {
                Log.w(TAG, "Sin permisos de ubicación. No se inicia tracking.")
                return@launch
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
        timeFromLoc: Long,
        elapsedNanos: Long,
        provider: String?
    ) {
        val timeMs = if (timeFromLoc > 0L) timeFromLoc else System.currentTimeMillis()
        val gpsQuality = GpsQualityEvaluator.evaluate(lat, lon, accM, provider)

        if (!gpsQuality.isValid) {
            Log.d(TAG, "GPS rechazado: ${gpsQuality.reason}")
            return
        }

        val lastEN = lastAcceptedElapsedNanos
        if (lastEN != null && elapsedNanos > 0L && elapsedNanos == lastEN) return

        if (gpsQuality.isUsableForEvent) goodFixStreak++ else goodFixStreak = 0

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
                Log.i(TAG, "Recording ARMED rápido (${gpsQuality.label}: ${gpsQuality.reason})")
                switchMode(Mode.TRACK)
            } else if (!gpsQuality.isUsableForTrack) {
                if (currentMode != Mode.ACQUIRE) switchMode(Mode.ACQUIRE)
                return
            }
        }

        if (!gpsQuality.isUsableForTrack) return

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

            if (speedMs > maxSpeedMs) {
                Log.d(TAG, "GPS rechazado por velocidad improbable: ${"%.1f".format(speedMs)} m/s")
                return
            }
            if (distM > jumpM && accM > jumpAccM) {
                Log.d(TAG, "GPS rechazado por salto: ${"%.1f".format(distM)} m / ±${accM.toInt()}m")
                return
            }

            if (distM < 1.2 && accM <= usableAccM) stillCounter++ else stillCounter = 0
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

        val canSave = gpsQuality.isUsableForTrack && shouldSaveTrackPoint(latF, lonF, timeMs)
        if (canSave) {
            val modeTag = currentMode?.name ?: "NA"
            val qualityTag = if (recordingArmed) "ARMED" else "QUICK"
            val p = TrackPoint(
                tripId = tripId,
                timeMs = timeMs,
                lat = latF,
                lon = lonF,
                accM = accM,
                provider = ((provider ?: "fused") + if (useKalman) "+kalman" else "+raw") + "+$modeTag+$qualityTag+${gpsQuality.quality.name}"
            )
            lastSavedTimeMs = timeMs
            lastSavedLat = latF
            lastSavedLon = lonF
            scope.launch {
                try {
                    AsdGraph.db.trackDao().insert(p)
                    val completed = AsdGraph.repo.completePendingGpsEvents(tripId, p)
                    if (completed > 0) Log.i(TAG, "GPS backfill aplicado a $completed evento(s)")
                } catch (e: Exception) {
                    Log.e(TAG, "Error insertando TrackPoint o completando GPS pendiente", e)
                }
            }
        }
    }

    private fun shouldSaveTrackPoint(lat: Double, lon: Double, timeMs: Long): Boolean {
        val savedTime = lastSavedTimeMs
        val savedLat = lastSavedLat
        val savedLon = lastSavedLon
        if (savedTime == null || savedLat == null || savedLon == null) return true

        val distanceM = haversineMeters(savedLat, savedLon, lat, lon)
        val elapsedMs = (timeMs - savedTime).coerceAtLeast(0L)
        return distanceM >= minSaveDistanceM || elapsedMs >= maxSaveIntervalMs
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
