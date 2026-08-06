package com.oropeza.urbanapp.asd.location

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.oropeza.urbanapp.asd.AsdGraph
import com.oropeza.urbanapp.asd.location.engine.AforaGpsEngine
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

class TrackingService : Service() {

    private val NOTIF_ID = 1001
    private val CHANNEL_ID = "tracking_channel"
    private val fixedCaptureIntervalMs = 2_000L
    private val watchdogIntervalMs = 3_000L
    private val staleFixAfterMs = 6_000L

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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val engine = AforaGpsEngine()

    private var mainJob: Job? = null
    private var gpsUpdatesJob: Job? = null
    private var saverJob: Job? = null
    private var watchdogJob: Job? = null
    private var currentTripId: Long? = null
    private var shuttingDown = false

    private lateinit var gps: LocationProvider
    private lateinit var headingProvider: HeadingProvider

    override fun onCreate() {
        super.onCreate()
        gps = LocationProvider(this)
        headingProvider = HeadingProvider(this)
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Validando recorrido activo…"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            Log.w(TAG, "Servicio recreado sin intención; se detiene para evitar captura huérfana")
            stopTracking()
            return START_NOT_STICKY
        }

        when (intent.action) {
            ACTION_START -> {
                val tripId = intent.getLongExtra(EXTRA_TRIP_ID, -1L)
                if (tripId > 0) {
                    if (currentTripId != null && currentTripId != tripId) resetTrackingState()
                    startTracking(tripId)
                } else {
                    Log.w(TAG, "ACTION_START sin tripId válido")
                    stopTracking()
                }
            }

            ACTION_STOP -> stopTracking()
            else -> {
                Log.w(TAG, "Acción desconocida; se detiene el servicio")
                stopTracking()
            }
        }

        return START_NOT_STICKY
    }

    private fun startTracking(tripId: Long) {
        if (currentTripId == tripId && isRunning) return

        resetTrackingState()
        currentTripId = tripId
        shuttingDown = false

        mainJob = scope.launch {
            val trip = AsdGraph.repo.getTripOnce(tripId)
            if (trip == null || trip.endTime != null) {
                Log.w(TAG, "No existe recorrido activo para tracking: $tripId")
                stopTracking()
                return@launch
            }

            if (!gps.hasPermission()) {
                Log.w(TAG, "Sin permisos de ubicación. Se detiene tracking.")
                stopTracking()
                return@launch
            }

            isRunning = true
            headingProvider.start()
            engine.start()
            publishRuntimeState(engineState = null, modeOverride = "ACQUIRE")
            updateNotification("Tracking ASD activo")
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
        mainJob = null
        gpsUpdatesJob = null
        saverJob = null
        watchdogJob = null
        engine.reset()
        _runtimeGpsState.value = RuntimeGpsState(mode = "IDLE")
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
                val engineState = engine.onRawFix(
                    AforaGpsEngine.RawLocationFix(
                        lat = loc.latitude,
                        lon = loc.longitude,
                        accM = loc.accuracy.toDouble(),
                        fixTimeMs = loc.time,
                        elapsedNanos = loc.elapsedRealtimeNanos,
                        provider = loc.provider,
                        receivedAtMs = now
                    )
                )
                publishRuntimeState(engineState)
            }
        }
    }

    private fun startSaverLoop() {
        saverJob?.cancel()
        saverJob = scope.launch {
            while (true) {
                delay(fixedCaptureIntervalMs)
                val tripId = currentTripId ?: break

                // Room es la fuente de verdad: nunca se escribe un punto si el
                // recorrido ya no existe o fue finalizado.
                val trip = AsdGraph.repo.getTripOnce(tripId)
                if (trip == null || trip.endTime != null) {
                    Log.i(TAG, "Recorrido $tripId inactivo; deteniendo captura GPS")
                    stopTracking()
                    break
                }

                val sample = engine.sample(tripId, System.currentTimeMillis())
                persistSample(tripId, sample)
            }
        }
    }

    private fun startWatchdogLoop() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            while (true) {
                delay(watchdogIntervalMs)
                if (currentTripId == null || !isRunning) break
                val age = System.currentTimeMillis() - engine.lastFixReceivedAtMs
                if (engine.lastFixReceivedAtMs == 0L || age > staleFixAfterMs) {
                    Log.i(TAG, "GPS watchdog restarting updates. Last fix age: ${age}ms")
                    startGpsUpdates()
                }
            }
        }
    }

    private suspend fun persistSample(
        tripId: Long,
        sample: AforaGpsEngine.CaptureSample
    ) {
        try {
            // Segunda defensa ante una carrera entre el cierre y la escritura.
            val trip = AsdGraph.repo.getTripOnce(tripId)
            if (trip == null || trip.endTime != null) {
                Log.i(TAG, "Muestra descartada porque el recorrido $tripId ya terminó")
                stopTracking()
                return
            }

            AsdGraph.db.trackDao().insert(sample.point)
            publishRuntimeState(sample.state)

            if (sample.shouldBackfillEvents) {
                val completed = AsdGraph.repo.completePendingGpsEvents(tripId, sample.point)
                if (completed > 0) Log.i(TAG, "GPS backfill aplicado a $completed evento(s)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error insertando TrackPoint o completando GPS pendiente", e)
        }
    }

    private fun publishRuntimeState(
        engineState: AforaGpsEngine.RuntimeState?,
        modeOverride: String? = null
    ) {
        if (engineState == null) {
            _runtimeGpsState.value = _runtimeGpsState.value.copy(mode = modeOverride ?: "IDLE")
            return
        }

        _runtimeGpsState.value = RuntimeGpsState(
            hasFix = engineState.hasFix,
            lat = engineState.lat,
            lon = engineState.lon,
            accM = engineState.accM,
            provider = engineState.provider,
            fixTimeMs = engineState.fixTimeMs,
            receivedAtMs = engineState.receivedAtMs,
            savedAtMs = engineState.savedAtMs,
            mode = modeOverride ?: engineState.mode.name,
            isRecordingArmed = engineState.isRecordingArmed
        )
    }

    private fun stopTracking() {
        if (shuttingDown) return
        shuttingDown = true
        isRunning = false
        currentTripId = null
        resetTrackingState()
        try {
            if (::headingProvider.isInitialized) headingProvider.stop()
        } catch (_: Exception) {
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        isRunning = false
        currentTripId = null
        resetTrackingState()
        try {
            if (::headingProvider.isInitialized) headingProvider.stop()
        } catch (_: Exception) {
        }
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(text))
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
