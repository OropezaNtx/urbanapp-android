package com.oropeza.urbanapp.asd.sync

import android.util.Log
import com.google.gson.JsonParser
import com.oropeza.urbanapp.asd.AsdGraph
import com.oropeza.urbanapp.asd.data.local.*
import com.oropeza.urbanapp.asd.data.repository.AsdCloudMapper
import com.oropeza.urbanapp.asd.location.engine.TrackPointQuality
import com.oropeza.urbanapp.asd.sync.cloud.*
import com.oropeza.urbanapp.core.platform.UrbanCloudPaths
import com.oropeza.urbanapp.core.runtime.UrbanRuntime

/**
 * Reconciles the final Room track of recently closed trips with the durable sync queue.
 *
 * The regular close flow already enqueues track chunks. This pass is intentionally
 * additive and idempotent: it only creates a fresh logical queue row when a chunk is
 * missing locally or when the final Room snapshot contains a different point count
 * than the latest queued payload for that same cloud path.
 *
 * This protects the close boundary where TrackingService may persist its last sample
 * at nearly the same time the trip is being closed. Room remains the source of truth.
 */
object TrackChunkQueueReconciler {
    private const val TAG = "CloudSyncIntegrity"
    private const val MAX_RECENT_CLOSED_TRIPS = 10

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

    suspend fun reconcileRecentClosedTrips(): Result {
        val context = AsdGraph.appContext
        val workspace = UrbanRuntime.workspace(context)
        val identity = UrbanRuntime.identity(context)
        val chunkSize = UrbanRuntime.configuration(context).trackChunkSize.coerceAtLeast(10)

        val trips = AsdGraph.db.tripDao()
            .getAllOnce()
            .asSequence()
            .filter { it.endTime != null }
            .toList()
            .takeLast(MAX_RECENT_CLOSED_TRIPS)

        var expectedChunks = 0
        var requeued = 0
        var missing = 0
        var changed = 0

        for (trip in trips) {
            val points = AsdGraph.repo.getTrackPointsOnce(trip.tripId).sortedBy { it.timeMs }
            if (points.isEmpty()) continue

            val cloudTripId = "${identity.installationId}_${trip.tripId}"
            val latestRows = latestRowsByPath(trip.tripId)
            val chunks = points.chunked(chunkSize)
            expectedChunks += chunks.size

            chunks.forEachIndexed { index, chunk ->
                val chunkId = "${cloudTripId}_chunk_$index"
                val cloudPath = UrbanCloudPaths.trackChunkPath(workspace, cloudTripId, chunkId)
                val current = latestRows[cloudPath]
                val queuedPointCount = current?.let { payloadPointCount(it.payloadJson) }

                val reason = when {
                    current == null -> "MISSING_QUEUE_ROW"
                    queuedPointCount != chunk.size -> "POINT_COUNT_CHANGED"
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
                            "roomPoints=${chunk.size} queuedPoints=${queuedPointCount ?: -1} previousStatus=${current?.status ?: "NONE"} path=$cloudPath"
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

    private fun payloadPointCount(payloadJson: String): Int? = try {
        JsonParser.parseString(payloadJson).asJsonObject.get("pointCount")?.asInt
    } catch (_: Exception) {
        null
    }

    private fun latestRowsByPath(tripId: Long): Map<String, QueueRow> {
        val sql = """
            SELECT id, cloudPath, status, payloadJson
            FROM sync_queue
            WHERE parentTripId = ?
              AND entityType = 'TRACK_CHUNK'
              AND cloudPath IS NOT NULL
            ORDER BY id ASC
        """.trimIndent()

        val cursor = AsdGraph.db.openHelper.readableDatabase.query(sql, arrayOf(tripId.toString()))
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
