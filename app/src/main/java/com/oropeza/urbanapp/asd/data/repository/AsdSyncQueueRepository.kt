package com.oropeza.urbanapp.asd.data.repository

import com.google.gson.Gson
import com.oropeza.urbanapp.asd.AsdGraph
import com.oropeza.urbanapp.asd.data.local.AsdSyncQueueDao
import com.oropeza.urbanapp.asd.data.local.AsdSyncQueueItem
import com.oropeza.urbanapp.asd.data.local.StopEvent
import com.oropeza.urbanapp.asd.data.local.Trip
import com.oropeza.urbanapp.core.identity.UrbanIdentityProvider
import com.oropeza.urbanapp.core.platform.UrbanCloudPaths
import com.oropeza.urbanapp.core.platform.UrbanPlatformCloudMapper
import com.oropeza.urbanapp.core.platform.UrbanPlatformService
import com.oropeza.urbanapp.core.platform.UrbanPlatformSettings

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
        val installation = UrbanPlatformService.buildCurrentInstallation(context)
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
        val heartbeat = UrbanPlatformService.buildHeartbeat(context, activeTripId?.toString())
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

    private suspend fun enqueue(
        type: String,
        operation: String,
        localId: Long,
        payload: Any,
        cloudPath: String? = null,
        priority: Int = 1
    ) {
        try {
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

            // ✅ Phase 6: Trigger cloud sync activation
            com.oropeza.urbanapp.core.platform.sync.UrbanCloudSyncScheduler.syncNow(AsdGraph.appContext)
        } catch (e: Exception) {
            android.util.Log.e("AsdSyncQueueRepo", "Failed to enqueue sync for $type $localId", e)
        }
    }
}
