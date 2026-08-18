package com.oropeza.urbanapp.asd.sync

import android.util.Log
import com.google.gson.JsonParser
import com.oropeza.urbanapp.asd.AsdGraph
import com.oropeza.urbanapp.core.runtime.UrbanRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Read-only auditor: TrackPoint(Room) -> TRACK_CHUNK(sync_queue) -> cloud. */
object TrackingPipelineIntegrityAuditor {
    private const val TAG = "TrackingIntegrity"

    data class Snapshot(
        val tripId: Long,
        val roomPointCount: Int,
        val configuredChunkSize: Int,
        val expectedChunkCount: Int,
        val queueRowCount: Int,
        val logicalChunkCount: Int,
        val duplicateQueueRowCount: Int,
        val queuedPointCount: Int,
        val syncedLogicalChunkCount: Int,
        val confirmedPointCount: Int,
        val pendingCount: Int,
        val inProgressCount: Int,
        val failedCount: Int,
        val deadLetterCount: Int,
        val invalidPayloadCount: Int,
        val state: String
    ) {
        val roomToQueueDelta: Int get() = queuedPointCount - roomPointCount
        val queueToCloudDelta: Int get() = confirmedPointCount - queuedPointCount
    }

    private data class QueueRow(val id: Long, val cloudPath: String?, val payloadJson: String, val status: String)

    suspend fun snapshot(tripId: Long): Snapshot = withContext(Dispatchers.IO) {
        val context = AsdGraph.appContext
        val trip = AsdGraph.db.tripDao().getByIdOnce(tripId)
        val roomPointCount = if (trip?.endTime != null) {
            AsdGraph.db.trackDao().countThroughOnce(tripId, trip.endTime!!)
        } else {
            AsdGraph.db.trackDao().countOnce(tripId)
        }
        val chunkSize = UrbanRuntime.configuration(context).trackChunkSize.coerceAtLeast(10)
        val expectedChunks = if (roomPointCount == 0) 0 else (roomPointCount + chunkSize - 1) / chunkSize
        val rows = readTrackChunkQueueRows(tripId)
        val groups = rows.groupBy { it.cloudPath ?: "__queue_id_${it.id}" }
        val logicalRows = groups.values.map { it.maxBy { row -> row.id } }

        var invalidPayloads = 0
        fun pointCount(row: QueueRow): Int = try {
            JsonParser.parseString(row.payloadJson).asJsonObject.get("pointCount")?.asInt
                ?: run { invalidPayloads++; 0 }
        } catch (_: Exception) { invalidPayloads++; 0 }

        val queuedPoints = logicalRows.sumOf(::pointCount)
        var confirmedPoints = 0
        var syncedLogicalChunks = 0
        groups.values.forEach { group ->
            group.filter { it.status == "SYNCED" }.maxByOrNull { it.id }?.let {
                syncedLogicalChunks++
                confirmedPoints += pointCount(it)
            }
        }

        val duplicateRows = (rows.size - groups.size).coerceAtLeast(0)
        val pending = logicalRows.count { it.status == "PENDING" }
        val inProgress = logicalRows.count { it.status == "IN_PROGRESS" }
        val failed = logicalRows.count { it.status == "FAILED" }
        val dead = logicalRows.count { it.status == "DEAD_LETTER" }
        val state = when {
            trip == null -> "TRIP_MISSING"
            trip.endTime == null && roomPointCount == 0 -> "TRACKING_STARTING"
            trip.endTime == null -> "TRACKING_ACTIVE"
            roomPointCount == 0 -> "ROOM_EMPTY"
            invalidPayloads > 0 -> "INVALID_CHUNK_PAYLOAD"
            groups.size != expectedChunks || queuedPoints != roomPointCount -> "ROOM_QUEUE_MISMATCH"
            duplicateRows > 0 -> "QUEUE_DUPLICATES"
            failed > 0 || dead > 0 -> "SYNC_ERROR"
            syncedLogicalChunks == expectedChunks && confirmedPoints == roomPointCount -> "COMPLETE"
            else -> "SYNC_PENDING"
        }

        Snapshot(tripId, roomPointCount, chunkSize, expectedChunks, rows.size, groups.size,
            duplicateRows, queuedPoints, syncedLogicalChunks, confirmedPoints, pending,
            inProgress, failed, dead, invalidPayloads, state)
    }

    suspend fun logSnapshot(tripId: Long, stage: String): Snapshot {
        val s = snapshot(tripId)
        Log.i(TAG, "PIPELINE_INTEGRITY stage=$stage trip=${s.tripId} state=${s.state} " +
            "roomPoints=${s.roomPointCount} chunkSize=${s.configuredChunkSize} expectedChunks=${s.expectedChunkCount} " +
            "queueRows=${s.queueRowCount} logicalChunks=${s.logicalChunkCount} duplicateRows=${s.duplicateQueueRowCount} " +
            "queuedPoints=${s.queuedPointCount} syncedChunks=${s.syncedLogicalChunkCount} confirmedPoints=${s.confirmedPointCount} " +
            "roomToQueueDelta=${s.roomToQueueDelta} queueToCloudDelta=${s.queueToCloudDelta} pending=${s.pendingCount} " +
            "inProgress=${s.inProgressCount} failed=${s.failedCount} dead=${s.deadLetterCount} invalidPayloads=${s.invalidPayloadCount}")
        return s
    }

    suspend fun logRelevantTrips(stage: String) {
        relevantTripIds().forEach { logSnapshot(it, stage) }
    }

    private fun relevantTripIds(): List<Long> {
        val sql = """
            SELECT DISTINCT parentTripId
            FROM sync_queue
            WHERE parentTripId IS NOT NULL
              AND entityType IN ('TRIP','EVENT','TRACK_CHUNK')
              AND (status != 'SYNCED' OR updatedAt >= ?)
            ORDER BY parentTripId DESC
            LIMIT 20
        """.trimIndent()
        val cutoff = System.currentTimeMillis() - 60 * 60 * 1000L
        val cursor = AsdGraph.db.openHelper.readableDatabase.query(sql, arrayOf(cutoff.toString()))
        return cursor.use {
            val index = it.getColumnIndexOrThrow("parentTripId")
            buildList { while (it.moveToNext()) add(it.getLong(index)) }
        }
    }

    private fun readTrackChunkQueueRows(tripId: Long): List<QueueRow> {
        val sql = "SELECT id, cloudPath, payloadJson, status FROM sync_queue WHERE parentTripId = ? AND entityType = 'TRACK_CHUNK' ORDER BY id ASC"
        val cursor = AsdGraph.db.openHelper.readableDatabase.query(sql, arrayOf(tripId.toString()))
        return cursor.use {
            val idIndex = it.getColumnIndexOrThrow("id")
            val pathIndex = it.getColumnIndexOrThrow("cloudPath")
            val payloadIndex = it.getColumnIndexOrThrow("payloadJson")
            val statusIndex = it.getColumnIndexOrThrow("status")
            buildList {
                while (it.moveToNext()) add(QueueRow(it.getLong(idIndex), if (it.isNull(pathIndex)) null else it.getString(pathIndex), it.getString(payloadIndex), it.getString(statusIndex)))
            }
        }
    }
}
