package com.oropeza.urbanapp.asd.sync

import android.util.Log
import com.google.gson.JsonParser
import com.oropeza.urbanapp.asd.AsdGraph

/**
 * Repairs internally contradictory or redundant sync queue metadata without touching
 * source-of-truth trip/track rows in Room.
 *
 * sync_queue is derived transport state. Proven redundant TRACK_CHUNK rows can therefore
 * be removed safely: the canonical track remains in Room and the deterministic cloudPath
 * identifies the cloud document. We only remove PENDING/FAILED duplicates; IN_PROGRESS,
 * unique unsent payloads and legitimate post-close corrections are preserved.
 */
object SyncQueueStateSanitizer {
    private const val TAG = "CloudSyncIntegrity"

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

    fun sanitize(): Int {
        val db = AsdGraph.db.openHelper.writableDatabase
        var changed = 0

        val syncedStateRepair = db.compileStatement(
            """
                UPDATE sync_queue
                SET lastError = NULL,
                    nextAttemptAt = 0
                WHERE status = 'SYNCED'
                  AND (lastError IS NOT NULL OR nextAttemptAt != 0)
            """.trimIndent()
        ).executeUpdateDelete()
        changed += syncedStateRepair
        if (syncedStateRepair > 0) {
            Log.i(TAG, "SYNC_QUEUE_STATE_SANITIZED rows=$syncedStateRepair rule=SYNCED_HAS_NO_ERROR")
        }

        val rows = loadTrackRows()
        val deleteStatement = db.compileStatement(
            """
                DELETE FROM sync_queue
                WHERE id = ?
                  AND status IN ('PENDING', 'FAILED')
            """.trimIndent()
        )

        rows.groupBy { it.cloudPath }.forEach { (cloudPath, group) ->
            val queued = group.filter { it.status == "PENDING" || it.status == "FAILED" }
            if (queued.isEmpty()) return@forEach

            val newestQueued = queued.maxBy { it.id }

            // Older unsent rows for the same deterministic document are superseded by the
            // newest queued payload. Keeping just one prevents backlog amplification.
            queued.filter { it.id != newestQueued.id }.forEach { row ->
                if (deleteRow(deleteStatement, row.id)) {
                    changed++
                    Log.w(
                        TAG,
                        "SYNC_QUEUE_DUPLICATE_TRACK_REMOVED queueId=${row.id} " +
                            "rule=KEEP_NEWEST_UNSENT path=$cloudPath"
                    )
                }
            }

            // A matching signature means the exact finalized chunk boundary has already
            // been confirmed in cloud. Do not delete a newer payload whose signature is
            // different: that may be the legitimate final correction produced immediately
            // after trip close.
            val newestSignature = payloadSignature(newestQueued.payloadJson)
            val alreadyConfirmedSamePayload = group
                .asSequence()
                .filter { it.status == "SYNCED" }
                .mapNotNull { payloadSignature(it.payloadJson) }
                .any { it == newestSignature }

            if (newestSignature != null && alreadyConfirmedSamePayload) {
                if (deleteRow(deleteStatement, newestQueued.id)) {
                    changed++
                    Log.w(
                        TAG,
                        "SYNC_QUEUE_DUPLICATE_TRACK_REMOVED queueId=${newestQueued.id} " +
                            "rule=SAME_PAYLOAD_ALREADY_SYNCED signature=$newestSignature path=$cloudPath"
                    )
                }
            }
        }

        return changed
    }

    private fun loadTrackRows(): List<QueueRow> {
        val sql = """
            SELECT id, cloudPath, status, payloadJson
            FROM sync_queue
            WHERE entityType = 'TRACK_CHUNK'
              AND cloudPath IS NOT NULL
              AND status IN ('PENDING', 'FAILED', 'SYNCED')
            ORDER BY cloudPath ASC, id ASC
        """.trimIndent()

        val cursor = AsdGraph.db.openHelper.readableDatabase.query(sql)
        return cursor.use {
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
    }

    private fun deleteRow(
        statement: androidx.sqlite.db.SupportSQLiteStatement,
        rowId: Long
    ): Boolean {
        statement.clearBindings()
        statement.bindLong(1, rowId)
        return statement.executeUpdateDelete() > 0
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
}
