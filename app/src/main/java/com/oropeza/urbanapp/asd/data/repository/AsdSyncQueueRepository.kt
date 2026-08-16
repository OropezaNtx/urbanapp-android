package com.oropeza.urbanapp.asd.data.repository

import com.google.gson.Gson
import com.oropeza.urbanapp.asd.AsdGraph
import com.oropeza.urbanapp.asd.data.local.AsdSyncQueueDao
import com.oropeza.urbanapp.asd.data.local.AsdSyncQueueItem
import com.oropeza.urbanapp.asd.data.local.StopEvent
import com.oropeza.urbanapp.asd.data.local.Trip
import com.oropeza.urbanapp.asd.location.TrackingService
import com.oropeza.urbanapp.core.identity.UrbanIdentityProvider
import com.oropeza.urbanapp.core.platform.UrbanCloudPaths
import com.oropeza.urbanapp.core.platform.UrbanPlatformCloudMapper
import com.oropeza.urbanapp.core.platform.UrbanPlatformService
import com.oropeza.urbanapp.core.platform.UrbanPlatformSettings
import com.oropeza.urbanapp.core.runtime.UrbanRuntime

class AsdSyncQueueRepository(private val dao: AsdSyncQueueDao) {

    private val gson = Gson()

    private fun getCloudTripId(context: android.content.Context, localId: Long): String {
        val deviceId = UrbanIdentityProvider.getIdentity(context).installationId
        return "${deviceId}_$localId"
    }

    private fun getCloudEventId(context: android.content.Context, localId: Long): String {
        val deviceId = UrbanIdentityProvider.getIdentity(context).installationId
        return "${deviceId}_$localId"
    }

    suspend fun enqueueInstallationRegister(context: android.content.Context) {
        val installation = UrbanPlatformService.buildCurrentInstallation(context).copy(
            status = UrbanRuntime.installationStatus(context).name
        )
        val path = UrbanCloudPaths.installationPath(
            workspaceId = installation.workspaceId
                ?.takeIf { it.isNotBlank() }
                ?: UrbanPlatformSettings.DEFAULT_ORGANIZATION_ID,
            projectId = installation.projectId
                ?.takeIf { it.isNotBlank() }
                ?: UrbanPlatformSettings.DEFAULT_PROJECT_ID,
            installation.installationId
        )
        enqueue(
            type = "INSTALLATION",
            operation = "UPSERT",
            localId = 0L,
            payload = UrbanPlatformCloudMapper.installationToMap(installation),
            cloudPath = path,
            priority = 0
        )
    }

    suspend fun enqueueHeartbeat(context: android.content.Context, activeTripId: Long? = null) {
        val base = UrbanPlatformService.buildHeartbeat(context, activeTripId?.toString())
        val gps = TrackingService.runtimeGpsState.value
        val hasUsableFix = gps.hasFix && gps.lat != 0.0 && gps.lon != 0.0
        val heartbeat = base.copy(
            gpsStatus = when {
                hasUsableFix -> "OK"
                activeTripId != null -> "PENDING"
                else -> "IDLE"
            },
            lastLat = gps.lat.takeIf { hasUsableFix },
            lastLon = gps.lon.takeIf { hasUsableFix },
            lastFixTime = gps.fixTimeMs.takeIf { hasUsableFix && it > 0L },
            status = if (activeTripId != null) "ACTIVE" else "IDLE",
            updatedAt = System.currentTimeMillis()
        )
        val path = UrbanCloudPaths.heartbeatPath(
            workspaceId = heartbeat.workspaceId
                ?.takeIf { it.isNotBlank() }
                ?: UrbanPlatformSettings.DEFAULT_ORGANIZATION_ID,
            projectId = heartbeat.projectId
                ?.takeIf { it.isNotBlank() }
                ?: UrbanPlatformSettings.DEFAULT_PROJECT_ID,
            installationId = heartbeat.installationId
        )
        enqueue(
            type = "HEARTBEAT",
            operation = "UPSERT",
            localId = 0L,
            payload = UrbanPlatformCloudMapper.heartbeatToMap(heartbeat),
            cloudPath = path,
            priority = 1
        )
    }

    suspend fun enqueueHeartbeatNow(context: android.content.Context) {
        enqueueHeartbeat(context)
    }

    suspend fun enqueueTripUpsert(context: android.content.Context, operation: String, trip: Trip) {
        val workspaceId = UrbanPlatformSettings.getWorkspaceId(context)
            .ifBlank { UrbanPlatformSettings.DEFAULT_ORGANIZATION_ID }
        val projId = UrbanPlatformSettings.getProjectId(context)
            .ifBlank { UrbanPlatformSettings.DEFAULT_PROJECT_ID }
        val cloudTripId = getCloudTripId(context, trip.tripId)
        val path = UrbanCloudPaths.tripPath(workspaceId, projId, cloudTripId)

        enqueue("TRIP", operation, trip.tripId, trip, cloudPath = path, priority = 0)
    }

    suspend fun enqueueEventUpsert(context: android.content.Context, event: StopEvent) {
        val workspaceId = UrbanPlatformSettings.getWorkspaceId(context)
            .ifBlank { UrbanPlatformSettings.DEFAULT_ORGANIZATION_ID }
        val projId = UrbanPlatformSettings.getProjectId(context)
            .ifBlank { UrbanPlatformSettings.DEFAULT_PROJECT_ID }
        val cloudTripId = getCloudTripId(context, event.tripId)
        val cloudEventId = getCloudEventId(context, event.eventId)
        val path = "${UrbanCloudPaths.tripPath(workspaceId, projId, cloudTripId)}/events/$cloudEventId"

        enqueue("EVENT", "UPSERT", event.eventId, event, cloudPath = path, priority = 0)
    }

    suspend fun enqueueTripClose(context: android.content.Context, trip: Trip) {
        enqueueTripUpsert(context, "CLOSE", trip)
    }

    /**
     * Presence is latest-state data, not an append-only business event. If bootstrap or
     * lifecycle races request INSTALLATION/HEARTBEAT repeatedly before the worker drains,
     * keep only the newest unsent state for that deterministic cloud document.
     *
     * TRIP/EVENT/TRACK_CHUNK are intentionally excluded from this coalescing path.
     */
    private fun dropSupersededPendingPresence(type: String, cloudPath: String?) {
        if (cloudPath.isNullOrBlank() || type !in setOf("INSTALLATION", "HEARTBEAT")) return

        val db = AsdGraph.db.openHelper.writableDatabase
        val statement = db.compileStatement(
            """
                DELETE FROM sync_queue
                WHERE entityType = ?
                  AND cloudPath = ?
                  AND status IN ('PENDING', 'FAILED')
            """.trimIndent()
        )
        statement.bindString(1, type)
        statement.bindString(2, cloudPath)
        val removed = statement.executeUpdateDelete()
        if (removed > 0) {
            android.util.Log.i(
                "CloudSyncIntegrity",
                "SYNC_PRESENCE_COALESCED type=$type removed=$removed path=$cloudPath"
            )
        }
    }

    private suspend fun enqueue(
        type: String,
        operation: String,
        localId: Long,
        payload: Any,
        cloudPath: String? = null,
        priority: Int = 1
    ) {
        try {
            dropSupersededPendingPresence(type, cloudPath)

            val item = AsdSyncQueueItem(
                entityType = type,
                operation = operation,
                entityLocalId = localId,
                payloadJson = gson.toJson(payload),
                cloudPath = cloudPath,
                priority = priority,
                status = "PENDING"
            )
            dao.insert(item)

            com.oropeza.urbanapp.core.platform.sync.UrbanCloudSyncScheduler.syncNow(AsdGraph.appContext)
        } catch (e: Exception) {
            android.util.Log.e("AsdSyncQueueRepo", "Failed to enqueue sync for $type $localId", e)
        }
    }
}
