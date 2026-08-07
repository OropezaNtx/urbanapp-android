package com.oropeza.urbanapp.asd.sync

import android.util.Log
import com.google.gson.JsonParser
import com.oropeza.urbanapp.asd.AsdGraph
import com.oropeza.urbanapp.core.runtime.UrbanRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Auditor read-only del pipeline TrackPoint(Room) -> TRACK_CHUNK(sync_queue) -> cloud.
 *
 * No altera TrackPoint, chunks, cola ni Firestore. Lee el estado ya persistido y
 * produce una fotografía de integridad para detectar pérdidas o duplicados entre
 * fronteras del pipeline.
 */
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

    private data class QueueRow(
        val id: Long,
        val cloudPath: String?,
        val payloadJson: String,
        val status: String
    )

    suspend fun snapshot(tripId: Long): Snapshot = withContext(Dispatchers.IO) {
        val context = AsdGraph.appContext
        val points = AsdGraph.db.trackDao().getByTripOnce(tripId)
        val chunkSize = UrbanRuntime.configuration(context).trackChunkSize.coerceAtLeast(10)
        val expectedChunks = if (points.isEmpty()) 0 else (points.size + chunkSize - 1) / chunkSize

        val rows = readTrackChunkQueueRows(tripId)
        val groups = rows.groupBy { it.cloudPath ?: "__queue_id_${it.id}" }
        val logicalRows = groups.values.map { group -> group.maxBy { it.id } }

        var invalidPayloads = 0
        fun pointCount(row: QueueRow): Int {
            return try {
                val json = JsonParser.parseString(row.payloadJson).asJsonObject
                json.get("pointCount")?.asInt ?: run {
                    invalidPayloads++
                    0
                }
            } catch (_: Exception) {
                invalidPayloads++
                0
            }
        }

        val queuedPoints = logicalRows.sumOf(::pointCount)

        var confirmedPoints = 0
        var syncedLogicalChunks = 0
        groups.values.forEach { group ->
            val synced = group.filter { it.status == "SYNCED" }.maxByOrNull { it.id }
            if (synced != null) {
                syncedLogicalChunks++
                confirmedPoints += pointCount(synced)
            }
        }

        val duplicateRows = (rows.size - groups.size).coerceAtLeast(0)
        val pending = logicalRows.count { it.status == "PENDING" }
        val inProgress = logicalRows.count { it.status == "IN_PROGRESS" }
        val failed = logicalRows.count { it.status == "FAILED" }
        val dead = logicalRows.count { it.status == "DEAD_LETTER" }

        val state = when {
            points.isEmpty() -> "ROOM_EMPTY"
            invalidPayloads > 0 -> "INVALID_CHUNK_PAYLOAD"
            groups.size != expectedChunks || queuedPoints != points.size -> "ROOM_QUEUE_MISMATCH"
            duplicateRows > 0 -> "QUEUE_DUPLICATES"
            failed > 0 || dead > 0 -> "SYNC_ERROR"
            syncedLogicalChunks == expectedChunks && confirmedPoints == points.size -> "COMPLETE"
            else -> "SYNC_PENDING"
        }

        Snapshot(
            tripId = tripId,
            roomPointCount = points.size,
            configuredChunkSize = chunkSize,
            expectedChunkCount = expectedChunks,
            queueRowCount = rows.size,
            logicalChunkCount = groups.size,
            duplicateQueueRowCount = duplicateRows,
            queuedPointCount = queuedPoints,
            syncedLogicalChunkCount = syncedLogicalChunks,
            confirmedPointCount = confirmedPoints,
            pendingCount = pending,
            inProgressCount = inProgress,
            failedCount = failed,
            deadLetterCount = dead,
            invalidPayloadCount = invalidPayloads,
            state = state
        )
    }

    suspend fun logSnapshot(tripId: Long, stage: String): Snapshot {
        val s = snapshot(tripId)
        Log.i(
            TAG,
            "PIPELINE_INTEGRITY stage=$stage trip=${s.tripId} state=${s.state} " +
                "roomPoints=${s.roomPointCount} chunkSize=${s.configuredChunkSize} " +
                "expectedChunks=${s.expectedChunkCount} queueRows=${s.queueRowCount} " +
                "logicalChunks=${s.logicalChunkCount} duplicateRows=${s.duplicateQueueRowCount} " +
                "queuedPoints=${s.queuedPointCount} syncedChunks=${s.syncedLogicalChunkCount} " +
                "confirmedPoints=${s.confirmedPointCount} roomToQueueDelta=${s.roomToQueueDelta} " +
                "queueToCloudDelta=${s.queueToCloudDelta} pending=${s.pendingCount} " +
                "inProgress=${s.inProgressCount} failed=${s.failedCount} dead=${s.deadLetterCount} " +
                "invalidPayloads=${s.invalidPayloadCount}"
        )
        return s
    }

    private fun readTrackChunkQueueRows(tripId: Long): List<QueueRow> {
        val sql = """
            SELECT id, cloudPath, payloadJson, status
            FROM sync_queue
            WHERE parentTripId = ? AND entityType = 'TRACK_CHUNK'
            ORDER BY id ASC
        """.trimIndent()

        val cursor = AsdGraph.db.openHelper.readableDatabase.query(sql, arrayOf(tripId.toString()))
        return cursor.use {
            val idIndex = it.getColumnIndexOrThrow("id")
            val pathIndex = it.getColumnIndexOrThrow("cloudPath")
            val payloadIndex = it.getColumnIndexOrThrow("payloadJson")
            val statusIndex = it.getColumnIndexOrThrow("status")
            buildList {
                while (it.moveToNext()) {
                    add(
                        QueueRow(
                            id = it.getLong(idIndex),
                            cloudPath = if (it.isNull(pathIndex)) null else it.getString(pathIndex),
                            payloadJson = it.getString(payloadIndex),
                            status = it.getString(statusIndex)
                        )
                    )
                }
            }
        }
    }
}
