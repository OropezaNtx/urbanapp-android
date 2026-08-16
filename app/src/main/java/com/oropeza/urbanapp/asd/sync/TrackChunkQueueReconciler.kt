package com.oropeza.urbanapp.asd.sync

import android.util.Log
import com.google.gson.JsonParser
import com.oropeza.urbanapp.asd.AsdGraph
import com.oropeza.urbanapp.asd.data.local.TrackPoint
import com.oropeza.urbanapp.asd.location.engine.TrackPointQuality
import com.oropeza.urbanapp.asd.sync.cloud.AsdCloudMapper
import com.oropeza.urbanapp.asd.sync.cloud.AsdTrackChunkCloudDto
import com.oropeza.urbanapp.core.platform.UrbanCloudPaths
import com.oropeza.urbanapp.core.runtime.UrbanRuntime

/**
 * Reconciles the final Room track of very recently closed trips with the durable sync queue.
 *
 * The automatic repair window is intentionally short. Once a trip has been closed long
 * enough to leave this window, its cloud geometry is considered immutable for background
 * synchronization. Historical repair must be an explicit operator/admin action, never a
 * side effect of an unrelated heartbeat, installation refresh or retry worker.
 *
 * Queue identity is the deterministic cloudPath. Legacy builds could persist an unreliable
 * or null parentTripId, so parentTripId must not be used to decide whether a historical
 * TRACK_CHUNK already exists.
 */
object TrackChunkQueueReconciler {
    private const val TAG = "CloudSyncIntegrity"
    private const val MAX_RECENT_CLOSED_TRIPS = 10
    private const val RECONCILE_CLOSED_WITHIN_MS = 15 * 60_000L

    data class Result(
        val scannedTrips: Int,
        val expectedChunks: Int,
        val requeuedChunks: Int,
        val missingChunks: Int,
        val changedChunks: Int
    )

    private data class QueueRow(
        val id: Long,
        val cloudPath: String,
        val status: String,
        val payloadJson: String
    )

    private data class PayloadSignature(
        val pointCount: Int?,
        val startTime: Long?,
        val endTime: Long?
    )

    suspend fun reconcileRecentClosedTrips(): Result {
        val context = AsdGraph.appContext
        val workspace = UrbanRuntime.workspace(context)
        val identity = UrbanRuntime.identity(context)
        val chunkSize = UrbanRuntime.configuration(context).trackChunkSize.coerceAtLeast(10)
        val trackDao = AsdGraph.db.trackDao()
        val now = System.currentTimeMillis()
        val oldestEligibleClose = now - RECONCILE_CLOSED_WITHIN_MS

        val trips = AsdGraph.db.tripDao()
            .getAllOnce()
            .asSequence()
            .filter { trip ->
                val endTime = trip.endTime
                endTime != null && endTime in oldestEligibleClose..now
            }
            .sortedBy { it.endTime }
            .toList()
            .takeLast(MAX_RECENT_CLOSED_TRIPS)

        if (trips.isEmpty()) {
            Log.i(
                TAG,
                "SYNC_TRACK_RECONCILIATION_IDLE eligibleClosedTrips=0 historicalRegenerationBlocked=true"
            )
        }

        var expectedChunks = 0
        var requeued = 0
        var missing = 0
        var changed = 0

        for (trip in trips) {
            val endTime = trip.endTime ?: continue
            val pointCount = trackDao.countThroughOnce(trip.tripId, endTime)
            if (pointCount <= 0) continue

            val allRoomCount = trackDao.countOnce(trip.tripId)
            val postCloseRows = (allRoomCount - pointCount).coerceAtLeast(0)
            if (postCloseRows > 0) {
                Log.w(
                    TAG,
                    "SYNC_TRACK_POST_CLOSE_ROWS_IGNORED trip=${trip.tripId} endTime=$endTime " +
                        "finalPoints=$pointCount ignoredRows=$postCloseRows"
                )
            }

            val cloudTripId = "${identity.installationId}_${trip.tripId}"
            val trackChunkPrefix = "${UrbanCloudPaths.tripPath(workspace, cloudTripId)}/track_chunks/"
            val latestRows = latestRowsByCloudPathPrefix(trackChunkPrefix)
            val chunkCount = (pointCount + chunkSize - 1) / chunkSize
            expectedChunks += chunkCount

            for (index in 0 until chunkCount) {
                val offset = index * chunkSize
                val chunk = trackDao.getPageThroughOnce(
                    tripId = trip.tripId,
                    toMs = endTime,
                    limit = chunkSize,
                    offset = offset
                )
                if (chunk.isEmpty()) continue

                val chunkId = "${cloudTripId}_chunk_$index"
                val cloudPath = UrbanCloudPaths.trackChunkPath(workspace, cloudTripId, chunkId)
                val current = latestRows[cloudPath]
                val queuedSignature = current?.let { payloadSignature(it.payloadJson) }
                val expectedSignature = PayloadSignature(
                    pointCount = chunk.size,
                    startTime = chunk.first().timeMs,
                    endTime = chunk.last().timeMs
                )

                val reason = when {
                    current == null -> "MISSING_QUEUE_ROW"
                    queuedSignature != expectedSignature -> "PAYLOAD_BOUNDARY_CHANGED"
                    else -> null
                }

                if (reason != null) {
                    val dto = buildDto(trip.tripId, cloudTripId, chunkId, index, chunk)
                    AsdGraph.repo.enqueueSync(
                        type = "TRACK_CHUNK",
                        operation = "UPSERT",
                        localId = trip.tripId,
                        payload = dto,
                        cloudPath = cloudPath,
                        parentTripId = trip.tripId
                    )
                    requeued++
                    if (reason == "MISSING_QUEUE_ROW") missing++ else changed++
                    Log.w(
                        TAG,
                        "SYNC_TRACK_CHUNK_RECONCILED trip=${trip.tripId} index=$index reason=$reason " +
                            "expectedSignature=$expectedSignature queuedSignature=$queuedSignature " +
                            "previousStatus=${current?.status ?: "NONE"} path=$cloudPath"
                    )
                }
            }
        }

        return Result(
            scannedTrips = trips.size,
            expectedChunks = expectedChunks,
            requeuedChunks = requeued,
            missingChunks = missing,
            changedChunks = changed
        )
    }

    private fun buildDto(
        tripId: Long,
        cloudTripId: String,
        chunkId: String,
        index: Int,
        chunk: List<TrackPoint>
    ): AsdTrackChunkCloudDto {
        val now = System.currentTimeMillis()
        return AsdTrackChunkCloudDto(
            chunkId = chunkId,
            cloudTripId = cloudTripId,
            localTripId = tripId,
            chunkIndex = index,
            pointCount = chunk.size,
            cleanPointCount = chunk.count { TrackPointQuality.isCleanRoutePoint(it) },
            stalePointCount = chunk.count { it.sampleStatus == "STALE" },
            noFixPointCount = chunk.count { it.sampleStatus == "NO_FIX" },
            syntheticPointCount = chunk.count { it.isSynthetic },
            points = chunk.map { AsdCloudMapper.toTrackPointDto(it) },
            startTime = chunk.first().timeMs,
            endTime = chunk.last().timeMs,
            createdAt = now,
            updatedAt = now
        )
    }

    private fun payloadSignature(payloadJson: String): PayloadSignature? = try {
        val json = JsonParser.parseString(payloadJson).asJsonObject
        PayloadSignature(
            pointCount = json.get("pointCount")?.asInt,
            startTime = json.get("startTime")?.asLong,
            endTime = json.get("endTime")?.asLong
        )
    } catch (_: Exception) {
        null
    }

    private fun latestRowsByCloudPathPrefix(prefix: String): Map<String, QueueRow> {
        val sql = """
            SELECT id, cloudPath, status, payloadJson
            FROM sync_queue
            WHERE entityType = 'TRACK_CHUNK'
              AND cloudPath IS NOT NULL
              AND cloudPath LIKE ?
            ORDER BY id ASC
        """.trimIndent()

        val cursor = AsdGraph.db.openHelper.readableDatabase.query(sql, arrayOf("$prefix%"))
        val rows = cursor.use {
            val idIndex = it.getColumnIndexOrThrow("id")
            val pathIndex = it.getColumnIndexOrThrow("cloudPath")
            val statusIndex = it.getColumnIndexOrThrow("status")
            val payloadIndex = it.getColumnIndexOrThrow("payloadJson")
            buildList {
                while (it.moveToNext()) {
                    add(
                        QueueRow(
                            id = it.getLong(idIndex),
                            cloudPath = it.getString(pathIndex),
                            status = it.getString(statusIndex),
                            payloadJson = it.getString(payloadIndex)
                        )
                    )
                }
            }
        }

        return rows.groupBy { it.cloudPath }
            .mapValues { (_, group) -> group.maxBy { it.id } }
    }
}
