package com.oropeza.urbanapp.asd.sync

import android.util.Log
import com.google.gson.JsonParser
import com.oropeza.urbanapp.asd.AsdGraph
import com.oropeza.urbanapp.asd.data.local.*
import com.oropeza.urbanapp.asd.location.engine.TrackPointQuality
import com.oropeza.urbanapp.asd.sync.cloud.*
import com.oropeza.urbanapp.core.platform.UrbanCloudPaths
import com.oropeza.urbanapp.core.runtime.UrbanRuntime

/**
 * Reconciles the final Room track of recently closed trips with the durable sync queue.
 *
 * Closed-trip truth is frozen at Trip.endTime. Any row that somehow exists after that
 * boundary is preserved in Room for audit, but is never allowed to expand or mutate the
 * final cloud geometry. Processing is paged so long field sessions do not require a full
 * track plus all derived chunks to be materialized in memory at the same time.
 *
 * IMPORTANT: queue identity is the deterministic cloudPath, not parentTripId. Older builds
 * could enqueue TRACK_CHUNK rows without a reliable parentTripId; looking them up only by
 * parentTripId made already-synced historical chunks appear missing and caused needless
 * re-enqueue/re-upload cycles while the device was idle.
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
        val trackDao = AsdGraph.db.trackDao()

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

    /**
     * Finds the newest queue row for each deterministic cloudPath.
     *
     * We intentionally do NOT depend on parentTripId here. Legacy rows may have null or
     * incorrectly inferred ownership, but their cloudPath is still the canonical identity
     * of the Firestore document. Recognizing those rows makes reconciliation idempotent
     * across upgrades and prevents idle historical re-upload loops.
     */
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
