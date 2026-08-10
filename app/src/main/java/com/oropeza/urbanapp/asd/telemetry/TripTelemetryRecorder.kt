package com.oropeza.urbanapp.asd.telemetry

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.oropeza.urbanapp.asd.AsdGraph
import com.oropeza.urbanapp.asd.data.local.AsdSyncQueueItem
import com.oropeza.urbanapp.core.platform.UrbanCloudPaths
import com.oropeza.urbanapp.core.runtime.UrbanRuntime
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

object TripTelemetryRecorder {
    private const val TAG = "TripTelemetry"
    private const val HEARTBEAT_STALE_MS = 30_000L
    private const val PREFS_NAME = "asd_tracking_recovery"
    private const val PREF_LAST_HEARTBEAT_MS = "last_heartbeat_ms"

    private val mutex = Mutex()
    private val gson = Gson()
    private val touchedSyncTrips = Collections.newSetFromMap(ConcurrentHashMap<Long, Boolean>())
    private val seenRunTripKeys = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    suspend fun ensureStarted(context: Context, tripId: Long) = mutex.withLock {
        if (tripId <= 0L) return@withLock
        val dao = AsdGraph.db.tripTelemetryDao()
        if (dao.getByTripId(tripId) == null) {
            dao.upsert(TripTelemetry(tripId = tripId))
            Log.i(TAG, "TELEMETRY_STARTED trip=$tripId")
        }
    }

    suspend fun sampleDevice(
        context: Context,
        tripId: Long,
        batteryPct: Int?,
        charging: Boolean,
        networkConnected: Boolean,
        networkType: String,
        now: Long = System.currentTimeMillis(),
        finished: Boolean = false
    ) = mutex.withLock {
        if (tripId <= 0L) return@withLock
        val dao = AsdGraph.db.tripTelemetryDao()
        val current = dao.getByTripId(tripId) ?: TripTelemetry(tripId = tripId, startedAt = now)
        val lastHeartbeat = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(PREF_LAST_HEARTBEAT_MS, 0L)
            .takeIf { it > 0L }
        val heartbeatAge = lastHeartbeat?.let { (now - it).coerceAtLeast(0L) }

        val previousConnected = current.lastNetworkConnected
        val transition = previousConnected != null && previousConnected != networkConnected
        val reconnected = previousConnected == false && networkConnected
        val intervalMs = current.lastSampleAt?.let { (now - it).coerceIn(0L, 5 * 60_000L) } ?: 0L
        val offlineIncrement = if (previousConnected == false) intervalMs else 0L

        val validBattery = batteryPct?.takeIf { it in 0..100 }
        val batterySamples = current.batterySampleCount + if (validBattery != null) 1 else 0
        val batterySum = current.batterySumPct + (validBattery?.toLong() ?: 0L)
        val batteryMin = when {
            validBattery == null -> current.batteryMinPct
            current.batteryMinPct == null -> validBattery
            else -> min(current.batteryMinPct, validBattery)
        }
        val batteryMax = when {
            validBattery == null -> current.batteryMaxPct
            current.batteryMaxPct == null -> validBattery
            else -> max(current.batteryMaxPct, validBattery)
        }

        val updated = current.copy(
            finishedAt = if (finished) now else current.finishedAt,
            updatedAt = now,
            batterySampleCount = batterySamples,
            batterySumPct = batterySum,
            batteryStartPct = current.batteryStartPct ?: validBattery,
            batteryEndPct = if (finished) validBattery ?: current.batteryEndPct else current.batteryEndPct,
            batteryMinPct = batteryMin,
            batteryMaxPct = batteryMax,
            chargingSampleCount = current.chargingSampleCount + if (charging) 1 else 0,
            heartbeatSampleCount = current.heartbeatSampleCount + if (heartbeatAge != null) 1 else 0,
            heartbeatAgeSumMs = current.heartbeatAgeSumMs + (heartbeatAge ?: 0L),
            heartbeatMaxAgeMs = max(current.heartbeatMaxAgeMs, heartbeatAge ?: 0L),
            heartbeatStaleSamples = current.heartbeatStaleSamples + if (heartbeatAge != null && heartbeatAge >= HEARTBEAT_STALE_MS) 1 else 0,
            lastHeartbeatAt = lastHeartbeat ?: current.lastHeartbeatAt,
            networkSampleCount = current.networkSampleCount + 1,
            connectedSampleCount = current.connectedSampleCount + if (networkConnected) 1 else 0,
            offlineDurationMs = current.offlineDurationMs + offlineIncrement,
            networkTransitionCount = current.networkTransitionCount + if (transition) 1 else 0,
            reconnectionCount = current.reconnectionCount + if (reconnected) 1 else 0,
            lastNetworkConnected = networkConnected,
            lastNetworkType = networkType,
            lastSampleAt = now
        )
        dao.upsert(updated)
        Log.i(TAG, "TELEMETRY_SAMPLE trip=$tripId battery=${validBattery ?: -1} charging=$charging network=$networkType connected=$networkConnected heartbeatAgeMs=${heartbeatAge ?: -1L} reconnections=${updated.reconnectionCount} offlineMs=${updated.offlineDurationMs} finished=$finished")
    }

    suspend fun recordWatchdogHealthy(tripId: Long) = mutex.withLock {
        mutate(tripId) { it.copy(watchdogHealthyCount = it.watchdogHealthyCount + 1, updatedAt = System.currentTimeMillis()) }
    }

    suspend fun recordRecovery(tripId: Long, gapMs: Long, assisted: Boolean = false, fgsBlocked: Boolean = false) = mutex.withLock {
        val now = System.currentTimeMillis()
        mutate(tripId) {
            it.copy(
                recoveryCount = it.recoveryCount + 1,
                assistedRecoveryCount = it.assistedRecoveryCount + if (assisted) 1 else 0,
                fgsBlockedCount = it.fgsBlockedCount + if (fgsBlocked) 1 else 0,
                lastRecoveryAt = now,
                lastRecoveryGapMs = gapMs.coerceAtLeast(0L),
                updatedAt = now
            )
        }
        Log.w(TAG, "TELEMETRY_RECOVERY trip=$tripId gapMs=$gapMs assisted=$assisted fgsBlocked=$fgsBlocked")
    }

    suspend fun recordFgsBlocked(tripId: Long, gapMs: Long) = mutex.withLock {
        val now = System.currentTimeMillis()
        mutate(tripId) {
            it.copy(
                recoveryCount = it.recoveryCount + 1,
                assistedRecoveryCount = it.assistedRecoveryCount + 1,
                fgsBlockedCount = it.fgsBlockedCount + 1,
                lastRecoveryAt = now,
                lastRecoveryGapMs = gapMs.coerceAtLeast(0L),
                updatedAt = now
            )
        }
        Log.w(TAG, "TELEMETRY_FGS_BLOCKED trip=$tripId gapMs=$gapMs")
    }

    suspend fun recordSyncItemStarted(runId: String, tripId: Long, startedAt: Long) = mutex.withLock {
        if (tripId <= 0L) return@withLock
        val key = "$runId:$tripId"
        mutate(tripId) {
            it.copy(
                syncRunCount = it.syncRunCount + if (seenRunTripKeys.add(key)) 1 else 0,
                firstUploadStartedAt = it.firstUploadStartedAt ?: startedAt,
                updatedAt = startedAt
            )
        }
        touchedSyncTrips.add(tripId)
        Log.i(TAG, "TELEMETRY_SYNC_STARTED trip=$tripId runId=$runId")
    }

    suspend fun recordSyncConfirmed(tripId: Long, elapsedMs: Long, payloadBytes: Long, finishedAt: Long) = mutex.withLock {
        if (tripId <= 0L) return@withLock
        mutate(tripId) {
            it.copy(
                syncConfirmedItemCount = it.syncConfirmedItemCount + 1,
                syncPayloadBytes = it.syncPayloadBytes + payloadBytes.coerceAtLeast(0L),
                lastUploadFinishedAt = finishedAt,
                lastCloudConfirmedAt = finishedAt,
                totalCloudRoundTripMs = it.totalCloudRoundTripMs + elapsedMs.coerceAtLeast(0L),
                maxCloudRoundTripMs = max(it.maxCloudRoundTripMs, elapsedMs.coerceAtLeast(0L)),
                lastSyncResult = "SUCCESS",
                updatedAt = finishedAt
            )
        }
        touchedSyncTrips.add(tripId)
        Log.i(TAG, "TELEMETRY_SYNC_CONFIRMED trip=$tripId elapsedMs=$elapsedMs bytes=$payloadBytes")
    }

    suspend fun recordSyncFailure(tripId: Long, result: String, now: Long) = mutex.withLock {
        if (tripId <= 0L) return@withLock
        mutate(tripId) {
            it.copy(
                syncRetryCount = it.syncRetryCount + if (result == "RETRY") 1 else 0,
                syncFailedItemCount = it.syncFailedItemCount + 1,
                lastSyncResult = result,
                lastUploadFinishedAt = now,
                updatedAt = now
            )
        }
        touchedSyncTrips.add(tripId)
        Log.w(TAG, "TELEMETRY_SYNC_FAILED trip=$tripId result=$result")
    }

    suspend fun flushTouchedSyncTelemetry(context: Context): Int {
        val trips = touchedSyncTrips.toList()
        touchedSyncTrips.removeAll(trips.toSet())
        trips.forEach { enqueueCloudSnapshot(context, it) }
        seenRunTripKeys.clear()
        if (trips.isNotEmpty()) Log.i(TAG, "TELEMETRY_SYNC_FLUSH trips=${trips.size}")
        return trips.size
    }

    suspend fun enqueueCloudSnapshot(context: Context, tripId: Long) = mutex.withLock {
        val telemetry = AsdGraph.db.tripTelemetryDao().getByTripId(tripId) ?: return@withLock
        val identity = UrbanRuntime.identity(context)
        val workspace = UrbanRuntime.workspace(context)
        val cloudTripId = "${identity.installationId}_$tripId"
        val cloudPath = UrbanCloudPaths.tripTelemetryPath(workspace, cloudTripId)
        val payload = mapOf(
            "schemaVersion" to telemetry.schemaVersion,
            "tripId" to telemetry.tripId,
            "installationId" to identity.installationId,
            "startedAt" to telemetry.startedAt,
            "finishedAt" to telemetry.finishedAt,
            "updatedAt" to telemetry.updatedAt,
            "battery" to mapOf(
                "samples" to telemetry.batterySampleCount,
                "startPct" to telemetry.batteryStartPct,
                "endPct" to telemetry.batteryEndPct,
                "minPct" to telemetry.batteryMinPct,
                "maxPct" to telemetry.batteryMaxPct,
                "averagePct" to telemetry.batteryAveragePct,
                "chargingSamples" to telemetry.chargingSampleCount
            ),
            "heartbeat" to mapOf(
                "samples" to telemetry.heartbeatSampleCount,
                "averageAgeMs" to telemetry.heartbeatAverageAgeMs,
                "maxAgeMs" to telemetry.heartbeatMaxAgeMs,
                "staleSamples" to telemetry.heartbeatStaleSamples,
                "lastHeartbeatAt" to telemetry.lastHeartbeatAt
            ),
            "network" to mapOf(
                "samples" to telemetry.networkSampleCount,
                "connectedSamples" to telemetry.connectedSampleCount,
                "coveragePct" to telemetry.networkCoveragePct,
                "offlineDurationMs" to telemetry.offlineDurationMs,
                "transitions" to telemetry.networkTransitionCount,
                "reconnections" to telemetry.reconnectionCount,
                "lastConnected" to telemetry.lastNetworkConnected,
                "lastType" to telemetry.lastNetworkType
            ),
            "recovery" to mapOf(
                "count" to telemetry.recoveryCount,
                "assistedCount" to telemetry.assistedRecoveryCount,
                "fgsBlockedCount" to telemetry.fgsBlockedCount,
                "watchdogHealthyCount" to telemetry.watchdogHealthyCount,
                "lastRecoveryAt" to telemetry.lastRecoveryAt,
                "lastRecoveryGapMs" to telemetry.lastRecoveryGapMs
            ),
            "sync" to mapOf(
                "runs" to telemetry.syncRunCount,
                "retries" to telemetry.syncRetryCount,
                "failedItems" to telemetry.syncFailedItemCount,
                "confirmedItems" to telemetry.syncConfirmedItemCount,
                "payloadBytes" to telemetry.syncPayloadBytes,
                "firstUploadStartedAt" to telemetry.firstUploadStartedAt,
                "lastUploadFinishedAt" to telemetry.lastUploadFinishedAt,
                "lastCloudConfirmedAt" to telemetry.lastCloudConfirmedAt,
                "averageCloudRoundTripMs" to telemetry.averageCloudRoundTripMs,
                "maxCloudRoundTripMs" to telemetry.maxCloudRoundTripMs,
                "lastResult" to telemetry.lastSyncResult
            )
        )
        AsdGraph.db.asdSyncQueueDao().insert(
            AsdSyncQueueItem(
                entityType = "TELEMETRY",
                operation = "UPSERT",
                entityLocalId = tripId,
                parentTripId = tripId,
                cloudPath = cloudPath,
                payloadJson = gson.toJson(payload),
                priority = 2
            )
        )
        Log.i(TAG, "TELEMETRY_ENQUEUED trip=$tripId cloudPath=$cloudPath")
    }

    private suspend fun mutate(tripId: Long, transform: (TripTelemetry) -> TripTelemetry) {
        if (tripId <= 0L) return
        val dao = AsdGraph.db.tripTelemetryDao()
        val current = dao.getByTripId(tripId) ?: TripTelemetry(tripId = tripId)
        dao.upsert(transform(current))
    }
}
