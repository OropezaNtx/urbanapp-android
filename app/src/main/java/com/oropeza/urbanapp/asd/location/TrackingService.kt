package com.oropeza.urbanapp.asd.location

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
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

    private val notificationId = 1001
    private val channelId = "tracking_channel"

    private val fixedCaptureIntervalMs = 2_000L
    private val roomInsertRetryDelayMs = 150L
    private val roomInsertMaxAttempts = 2
    private val sessionHeartbeatEverySamples = 5L

    private val gpsRequestIntervalMs = 2_000L
    private val gpsMinUpdateIntervalMs = 1_000L

    private val watchdogIntervalMs = 10_000L
    private val watchdogRestartAfterMs = 60_000L
    private val watchdogRestartCooldownMs = 30_000L

    companion object {
        const val ACTION_START = "TRACK_START"
        const val ACTION_STOP = "TRACK_STOP"
        const val EXTRA_TRIP_ID = "trip_id"

        private const val TAG = "TrackingService"
        private const val PREFS_NAME = "asd_tracking_recovery"
        private const val PREF_SESSION_ACTIVE = "session_active"
        private const val PREF_TRIP_ID = "trip_id"
        private const val PREF_LAST_HEARTBEAT_MS = "last_heartbeat_ms"

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
            val isRecordingArmed: Boolean = false,
            val rawFixCount: Long = 0L,
            val persistedSampleCount: Long = 0L,
            val watchdogRestartCount: Int = 0
        )

        data class TrackingMetricsSnapshot(
            val tripId: Long? = null,
            val active: Boolean = false,
            val elapsedMs: Long = 0L,
            val rawFixCount: Long = 0L,
            val persistedSampleCount: Long = 0L,
            val liveSampleCount: Long = 0L,
            val noFixSampleCount: Long = 0L,
            val averageAccuracyM: Double = 0.0,
            val maxRawFixGapMs: Long = 0L,
            val averageRoomInsertMs: Double = 0.0,
            val maxRoomInsertMs: Double = 0.0,
            val averageTickDriftMs: Double = 0.0,
            val maxTickDriftMs: Long = 0L,
            val missedTickCount: Long = 0L,
            val roomInsertFailureCount: Long = 0L,
            val consecutiveRoomInsertFailures: Int = 0,
            val lastRoomInsertError: String? = null,
            val degraded: Boolean = false,
            val watchdogRestartCount: Int = 0,
            val recoveryCount: Int = 0,
            val lastRecoveryGapMs: Long = 0L,
            val recoveredAfterProcessDeath: Boolean = false,
            val engineMode: String = "IDLE",
            val updatedAtMs: Long = 0L
        )

        private val _runtimeGpsState = MutableStateFlow(RuntimeGpsState())
        val runtimeGpsState: StateFlow<RuntimeGpsState> = _runtimeGpsState.asStateFlow()

        private val _trackingMetrics = MutableStateFlow(TrackingMetricsSnapshot())
        val trackingMetrics: StateFlow<TrackingMetricsSnapshot> = _trackingMetrics.asStateFlow()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val engine = AforaGpsEngine()

    private var mainJob: Job? = null
    private var gpsUpdatesJob: Job? = null
    private var saverJob: Job? = null
    private var watchdogJob: Job? = null
    private var currentTripId: Long? = null
    private var shuttingDown = false
    private var recoveryInProgress = false

    private var rawFixCount = 0L
    private var persistedSampleCount = 0L
    private var liveSampleCount = 0L
    private var noFixSampleCount = 0L
    private var accuracySumM = 0.0
    private var accuracySampleCount = 0L
    private var maxRawFixGapMs = 0L
    private var lastRawFixReceivedAtMs = 0L
    private var totalRoomInsertNs = 0L
    private var maxRoomInsertNs = 0L
    private var roomInsertCount = 0L
    private var totalTickDriftMs = 0L
    private var tickMeasurementCount = 0L
    private var maxTickDriftMs = 0L
    private var missedTickCount = 0L
    private var roomInsertFailureCount = 0L
    private var consecutiveRoomInsertFailures = 0
    private var lastRoomInsertError: String? = null
    private var degraded = false
    private var watchdogRestartCount = 0
    private var lastGpsRestartAtMs = 0L
    private var trackingStartedElapsedMs = 0L
    private var lastNotificationText: String? = null
    private var recoveryCount = 0
    private var lastRecoveryGapMs = 0L
    private var recoveredAfterProcessDeath = false

    private lateinit var gps: LocationProvider

    override fun onCreate() {
        super.onCreate()
        gps = LocationProvider(this)
        createNotificationChannel()
        startForeground(notificationId, buildNotification("Validando recorrido activo…"))
        lastNotificationText = "Validando recorrido activo…"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            Log.w(TAG, "Servicio recreado por Android sin Intent; iniciando reconciliación local")
            recoverAfterProcessRecreation()
            return START_STICKY
        }

        when (intent.action) {
            ACTION_START -> {
                val tripId = intent.getLongExtra(EXTRA_TRIP_ID, -1L)
                if (tripId > 0L) {
                    if (currentTripId != null && currentTripId != tripId) resetTrackingState()
                    startTracking(tripId, recovered = false, recoveryGapMs = 0L)
                } else {
                    Log.w(TAG, "ACTION_START sin tripId válido")
                    stopTracking(clearRecoveryMarker = true)
                }
            }

            ACTION_STOP -> stopTracking(clearRecoveryMarker = true)
            else -> {
                Log.w(TAG, "Acción desconocida; se detiene el servicio")
                stopTracking(clearRecoveryMarker = true)
            }
        }

        return START_STICKY
    }

    private fun recoverAfterProcessRecreation() {
        if (recoveryInProgress || mainJob?.isActive == true || isRunning) return
        recoveryInProgress = true

        scope.launch {
            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val markerActive = prefs.getBoolean(PREF_SESSION_ACTIVE, false)
            val markedTripId = prefs.getLong(PREF_TRIP_ID, -1L)
            val lastHeartbeatMs = prefs.getLong(PREF_LAST_HEARTBEAT_MS, 0L)

            val activeTrips = AsdGraph.db.tripDao().getAllOnce().filter { it.endTime == null }
            val activeTrip = activeTrips.singleOrNull()

            val canRecover = markerActive &&
                markedTripId > 0L &&
                activeTrip != null &&
                activeTrip.tripId == markedTripId

            if (!canRecover) {
                Log.w(
                    TAG,
                    "Recuperación rechazada: markerActive=$markerActive markedTripId=$markedTripId " +
                        "activeTripCount=${activeTrips.size} activeTripId=${activeTrip?.tripId}"
                )
                recoveryInProgress = false
                clearSessionMarker()
                stopTracking(clearRecoveryMarker = false)
                return@launch
            }

            val now = System.currentTimeMillis()
            val gapMs = if (lastHeartbeatMs > 0L) {
                (now - lastHeartbeatMs).coerceAtLeast(0L)
            } else {
                0L
            }

            Log.w(TAG, "Recuperando tracking trip=${activeTrip.tripId} suspensiónMs=$gapMs")
            recoveryInProgress = false
            startTracking(activeTrip.tripId, recovered = true, recoveryGapMs = gapMs)
        }
    }

    private fun startTracking(tripId: Long, recovered: Boolean, recoveryGapMs: Long) {
        if (currentTripId == tripId && isRunning) return

        resetTrackingState()
        currentTripId = tripId
        shuttingDown = false
        resetMetrics(tripId)
        recoveredAfterProcessDeath = recovered
        lastRecoveryGapMs = recoveryGapMs
        if (recovered) recoveryCount++

        mainJob = scope.launch {
            val trip = AsdGraph.repo.getTripOnce(tripId)
            if (trip == null || trip.endTime != null) {
                Log.w(TAG, "No existe recorrido activo para tracking: $tripId")
                stopTracking(clearRecoveryMarker = true)
                return@launch
            }

            if (!gps.hasPermission()) {
                Log.w(TAG, "Sin permisos de ubicación. Se detiene tracking.")
                stopTracking(clearRecoveryMarker = true)
                return@launch
            }

            isRunning = true
            engine.start()
            writeSessionHeartbeat(tripId)
            publishRuntimeState(engineState = null, modeOverride = "ACQUIRE")
            publishMetrics(engineMode = "ACQUIRE")
            updateNotification(
                if (recovered) {
                    "Tracking reanudado tras ${formatGap(recoveryGapMs)}"
                } else {
                    "Tracking ASD activo"
                }
            )
            if (recovered) logMetrics("recovered")
            startGpsUpdates()
            startSaverLoop()
            startWatchdogLoop()
        }
    }

    private fun resetMetrics(tripId: Long) {
        rawFixCount = 0L
        persistedSampleCount = 0L
        liveSampleCount = 0L
        noFixSampleCount = 0L
        accuracySumM = 0.0
        accuracySampleCount = 0L
        maxRawFixGapMs = 0L
        lastRawFixReceivedAtMs = 0L
        totalRoomInsertNs = 0L
        maxRoomInsertNs = 0L
        roomInsertCount = 0L
        totalTickDriftMs = 0L
        tickMeasurementCount = 0L
        maxTickDriftMs = 0L
        missedTickCount = 0L
        roomInsertFailureCount = 0L
        consecutiveRoomInsertFailures = 0
        lastRoomInsertError = null
        degraded = false
        watchdogRestartCount = 0
        lastGpsRestartAtMs = 0L
        trackingStartedElapsedMs = SystemClock.elapsedRealtime()
        recoveryCount = 0
        lastRecoveryGapMs = 0L
        recoveredAfterProcessDeath = false
        _trackingMetrics.value = TrackingMetricsSnapshot(
            tripId = tripId,
            active = false,
            updatedAtMs = System.currentTimeMillis()
        )
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

    private fun startGpsUpdates(fromWatchdog: Boolean = false) {
        val now = System.currentTimeMillis()
        if (fromWatchdog && now - lastGpsRestartAtMs < watchdogRestartCooldownMs) return

        gpsUpdatesJob?.cancel()
        lastGpsRestartAtMs = now
        if (fromWatchdog) watchdogRestartCount++

        gpsUpdatesJob = scope.launch {
            Log.i(
                TAG,
                if (fromWatchdog) "Reiniciando listener GPS por watchdog" else "Iniciando listener GPS"
            )
            gps.locationUpdates(
                intervalMs = gpsRequestIntervalMs,
                minUpdateMs = gpsMinUpdateIntervalMs,
                minDistanceM = 0f,
                maxWaitTimeMs = 0L,
                highAccuracy = true
            ).catch { e ->
                Log.e(TAG, "Error en locationUpdates()", e)
            }.collect { loc ->
                val receivedAt = System.currentTimeMillis()
                if (lastRawFixReceivedAtMs > 0L) {
                    val gapMs = (receivedAt - lastRawFixReceivedAtMs).coerceAtLeast(0L)
                    if (gapMs > maxRawFixGapMs) maxRawFixGapMs = gapMs
                }
                lastRawFixReceivedAtMs = receivedAt
                rawFixCount++
                if (loc.accuracy > 0f) {
                    accuracySumM += loc.accuracy.toDouble()
                    accuracySampleCount++
                }

                val engineState = engine.onRawFix(
                    AforaGpsEngine.RawLocationFix(
                        lat = loc.latitude,
                        lon = loc.longitude,
                        accM = loc.accuracy.toDouble(),
                        fixTimeMs = loc.time,
                        elapsedNanos = loc.elapsedRealtimeNanos,
                        provider = loc.provider,
                        receivedAtMs = receivedAt
                    )
                )
                publishRuntimeState(engineState)
            }
        }
    }

    private fun startSaverLoop() {
        saverJob?.cancel()
        saverJob = scope.launch {
            var nextTickElapsedMs = SystemClock.elapsedRealtime() + fixedCaptureIntervalMs

            while (true) {
                val waitMs = nextTickElapsedMs - SystemClock.elapsedRealtime()
                if (waitMs > 0L) delay(waitMs)

                val actualTickElapsedMs = SystemClock.elapsedRealtime()
                val tickDriftMs = (actualTickElapsedMs - nextTickElapsedMs).coerceAtLeast(0L)
                recordTickDrift(tickDriftMs)

                if (tickDriftMs >= fixedCaptureIntervalMs) {
                    val missed = tickDriftMs / fixedCaptureIntervalMs
                    missedTickCount += missed
                    nextTickElapsedMs = actualTickElapsedMs + fixedCaptureIntervalMs
                } else {
                    nextTickElapsedMs += fixedCaptureIntervalMs
                }

                val tripId = currentTripId ?: break
                val trip = AsdGraph.repo.getTripOnce(tripId)
                if (trip == null || trip.endTime != null) {
                    Log.i(TAG, "Recorrido $tripId inactivo; deteniendo captura GPS")
                    stopTracking(clearRecoveryMarker = true)
                    break
                }

                val sample = engine.sample(tripId, System.currentTimeMillis())
                persistSample(tripId, sample)
            }
        }
    }

    private fun recordTickDrift(driftMs: Long) {
        totalTickDriftMs += driftMs
        tickMeasurementCount++
        if (driftMs > maxTickDriftMs) maxTickDriftMs = driftMs
    }

    private fun startWatchdogLoop() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            while (true) {
                delay(watchdogIntervalMs)
                val tripId = currentTripId ?: break
                if (!isRunning) break

                writeSessionHeartbeat(tripId)

                val listenerInactive = gpsUpdatesJob?.isActive != true
                val lastFixAt = engine.lastFixReceivedAtMs
                val fixAgeMs = if (lastFixAt > 0L) {
                    System.currentTimeMillis() - lastFixAt
                } else {
                    System.currentTimeMillis() - lastGpsRestartAtMs
                }

                if (listenerInactive || fixAgeMs > watchdogRestartAfterMs) {
                    Log.w(TAG, "Watchdog GPS: listenerInactive=$listenerInactive, fixAgeMs=$fixAgeMs")
                    startGpsUpdates(fromWatchdog = true)
                    publishMetrics()
                }
            }
        }
    }

    private suspend fun persistSample(
        tripId: Long,
        sample: AforaGpsEngine.CaptureSample
    ) {
        if (!insertTrackPointWithRetry(sample)) return

        persistedSampleCount++
        if (persistedSampleCount % sessionHeartbeatEverySamples == 0L) {
            writeSessionHeartbeat(tripId)
        }

        if (sample.point.sampleStatus == "NO_FIX") {
            noFixSampleCount++
        } else {
            liveSampleCount++
        }

        publishRuntimeState(sample.state)
        publishMetrics(engineMode = sample.state.mode.name)

        if (sample.shouldBackfillEvents) {
            try {
                val completed = AsdGraph.repo.completePendingGpsEvents(tripId, sample.point)
                if (completed > 0) Log.i(TAG, "GPS backfill aplicado a $completed evento(s)")
            } catch (e: Exception) {
                Log.e(TAG, "TrackPoint persistido, pero falló el backfill GPS", e)
            }
        }

        if (persistedSampleCount % 300L == 0L) {
            logMetrics("periodic")
        }
    }

    private suspend fun insertTrackPointWithRetry(sample: AforaGpsEngine.CaptureSample): Boolean {
        var lastError: Exception? = null

        repeat(roomInsertMaxAttempts) { attemptIndex ->
            val insertStartedNs = SystemClock.elapsedRealtimeNanos()
            try {
                val rowId = AsdGraph.db.trackDao().insert(sample.point)
                if (rowId == -1L) {
                    throw IllegalStateException("Room ignoró TrackPoint sin insertarlo")
                }

                val insertDurationNs =
                    (SystemClock.elapsedRealtimeNanos() - insertStartedNs).coerceAtLeast(0L)
                totalRoomInsertNs += insertDurationNs
                roomInsertCount++
                if (insertDurationNs > maxRoomInsertNs) maxRoomInsertNs = insertDurationNs

                consecutiveRoomInsertFailures = 0
                if (degraded) {
                    degraded = false
                    updateNotification("Tracking ASD activo")
                    Log.i(TAG, "Persistencia Room recuperada")
                }
                publishMetrics(engineMode = sample.state.mode.name)
                return true
            } catch (e: Exception) {
                lastError = e
                roomInsertFailureCount++
                consecutiveRoomInsertFailures++
                lastRoomInsertError = e.message ?: e.javaClass.simpleName
                Log.e(TAG, "Fallo Room intento ${attemptIndex + 1}/$roomInsertMaxAttempts", e)
                publishMetrics(engineMode = sample.state.mode.name)

                if (attemptIndex + 1 < roomInsertMaxAttempts) {
                    delay(roomInsertRetryDelayMs)
                }
            }
        }

        degraded = true
        updateNotification("Tracking degradado: error de almacenamiento")
        publishMetrics(engineMode = sample.state.mode.name)
        Log.e(TAG, "No se pudo persistir TrackPoint tras reintento", lastError)
        return false
    }

    private fun publishRuntimeState(
        engineState: AforaGpsEngine.RuntimeState?,
        modeOverride: String? = null
    ) {
        if (engineState == null) {
            _runtimeGpsState.value = _runtimeGpsState.value.copy(
                mode = modeOverride ?: "IDLE",
                rawFixCount = rawFixCount,
                persistedSampleCount = persistedSampleCount,
                watchdogRestartCount = watchdogRestartCount
            )
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
            isRecordingArmed = engineState.isRecordingArmed,
            rawFixCount = rawFixCount,
            persistedSampleCount = persistedSampleCount,
            watchdogRestartCount = watchdogRestartCount
        )
    }

    private fun publishMetrics(
        engineMode: String = _runtimeGpsState.value.mode,
        activeOverride: Boolean? = null
    ) {
        val elapsedMs = if (trackingStartedElapsedMs > 0L) {
            (SystemClock.elapsedRealtime() - trackingStartedElapsedMs).coerceAtLeast(0L)
        } else {
            0L
        }
        val averageAccuracy = if (accuracySampleCount > 0L) {
            accuracySumM / accuracySampleCount.toDouble()
        } else {
            0.0
        }
        val averageInsertMs = if (roomInsertCount > 0L) {
            totalRoomInsertNs.toDouble() / roomInsertCount.toDouble() / 1_000_000.0
        } else {
            0.0
        }
        val averageTickDrift = if (tickMeasurementCount > 0L) {
            totalTickDriftMs.toDouble() / tickMeasurementCount.toDouble()
        } else {
            0.0
        }

        _trackingMetrics.value = TrackingMetricsSnapshot(
            tripId = currentTripId ?: _trackingMetrics.value.tripId,
            active = activeOverride ?: isRunning,
            elapsedMs = elapsedMs,
            rawFixCount = rawFixCount,
            persistedSampleCount = persistedSampleCount,
            liveSampleCount = liveSampleCount,
            noFixSampleCount = noFixSampleCount,
            averageAccuracyM = averageAccuracy,
            maxRawFixGapMs = maxRawFixGapMs,
            averageRoomInsertMs = averageInsertMs,
            maxRoomInsertMs = maxRoomInsertNs.toDouble() / 1_000_000.0,
            averageTickDriftMs = averageTickDrift,
            maxTickDriftMs = maxTickDriftMs,
            missedTickCount = missedTickCount,
            roomInsertFailureCount = roomInsertFailureCount,
            consecutiveRoomInsertFailures = consecutiveRoomInsertFailures,
            lastRoomInsertError = lastRoomInsertError,
            degraded = degraded,
            watchdogRestartCount = watchdogRestartCount,
            recoveryCount = recoveryCount,
            lastRecoveryGapMs = lastRecoveryGapMs,
            recoveredAfterProcessDeath = recoveredAfterProcessDeath,
            engineMode = engineMode,
            updatedAtMs = System.currentTimeMillis()
        )
    }

    private fun logMetrics(reason: String) {
        val metrics = _trackingMetrics.value
        Log.i(
            TAG,
            "metrics reason=$reason trip=${metrics.tripId} active=${metrics.active} " +
                "elapsedMs=${metrics.elapsedMs} rawFixes=${metrics.rawFixCount} " +
                "persisted=${metrics.persistedSampleCount} live=${metrics.liveSampleCount} " +
                "noFix=${metrics.noFixSampleCount} avgAccM=${"%.2f".format(metrics.averageAccuracyM)} " +
                "maxFixGapMs=${metrics.maxRawFixGapMs} " +
                "avgRoomInsertMs=${"%.3f".format(metrics.averageRoomInsertMs)} " +
                "maxRoomInsertMs=${"%.3f".format(metrics.maxRoomInsertMs)} " +
                "avgTickDriftMs=${"%.2f".format(metrics.averageTickDriftMs)} " +
                "maxTickDriftMs=${metrics.maxTickDriftMs} missedTicks=${metrics.missedTickCount} " +
                "roomFailures=${metrics.roomInsertFailureCount} " +
                "consecutiveRoomFailures=${metrics.consecutiveRoomInsertFailures} " +
                "degraded=${metrics.degraded} watchdogRestarts=${metrics.watchdogRestartCount} " +
                "recoveryCount=${metrics.recoveryCount} recoveryGapMs=${metrics.lastRecoveryGapMs} " +
                "recovered=${metrics.recoveredAfterProcessDeath} mode=${metrics.engineMode}"
        )
    }

    private fun writeSessionHeartbeat(tripId: Long) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_SESSION_ACTIVE, true)
            .putLong(PREF_TRIP_ID, tripId)
            .putLong(PREF_LAST_HEARTBEAT_MS, System.currentTimeMillis())
            .apply()
    }

    private fun clearSessionMarker() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().clear().apply()
    }

    private fun formatGap(gapMs: Long): String {
        val seconds = gapMs / 1_000L
        return if (seconds < 60L) {
            "${seconds}s"
        } else {
            "${seconds / 60L}m ${seconds % 60L}s"
        }
    }

    private fun stopTracking(clearRecoveryMarker: Boolean) {
        if (shuttingDown) return
        shuttingDown = true
        isRunning = false
        if (clearRecoveryMarker) clearSessionMarker()
        publishMetrics(activeOverride = false)
        logMetrics("stop")
        currentTripId = null
        resetTrackingState()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        isRunning = false
        publishMetrics(activeOverride = false)
        currentTripId = null
        resetTrackingState()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateNotification(text: String) {
        if (lastNotificationText == text) return
        lastNotificationText = text
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(notificationId, buildNotification(text))
    }

    private fun buildNotification(text: String): android.app.Notification {
        return NotificationCompat.Builder(this, channelId)
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
                channelId,
                "Tracking",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
