package com.oropeza.urbanapp.asd.sync

import android.util.Log
import com.google.gson.JsonParser
import com.oropeza.urbanapp.asd.AsdGraph

/**
 * Repairs internally contradictory or redundant sync queue metadata without touching
 * source-of-truth trip/track rows in Room.
 *
 * TRACK_CHUNK identity is its deterministic cloudPath. Duplicate queued copies are safe to
 * suppress only when they represent the same finalized payload signature. A newer payload
 * with a different point/boundary signature is intentionally preserved so the short
 * post-close reconciliation window can still publish the legitimate final geometry.
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
        val now = System.currentTimeMillis()
        val suppressStatement = db.compileStatement(
            """
                UPDATE sync_queue
                SET status = 'DEAD_LETTER',
                    lastError = ?,
                    nextAttemptAt = 0,
                    updatedAt = ?
                WHERE id = ?
                  AND status IN ('PENDING', 'FAILED')
            """.trimIndent()
        )

        rows.groupBy { it.cloudPath }.forEach { (cloudPath, group) ->
            val queued = group.filter { it.status == "PENDING" || it.status == "FAILED" }
            if (queued.isEmpty()) return@forEach

            val newestQueued = queued.maxBy { it.id }

            // Older unsent rows for the same deterministic document are superseded by the
            // newest queued payload. This prevents backlog amplification while preserving
            // one legitimate write if the final payload changed during trip close.
            queued.filter { it.id != newestQueued.id }.forEach { row ->
                if (suppressRow(
                        statement = suppressStatement,
                        rowId = row.id,
                        now = now,
                        reason = "Suppressed duplicate TRACK_CHUNK: newer queue row exists"
                    )
                ) {
                    changed++
                    Log.w(
                        TAG,
                        "SYNC_QUEUE_DUPLICATE_TRACK_SUPPRESSED queueId=${row.id} " +
                            "rule=KEEP_NEWEST_UNSENT path=$cloudPath"
                    )
                }
            }

            val newestSignature = payloadSignature(newestQueued.payloadJson)
            val alreadyConfirmedSamePayload = group
                .asSequence()
                .filter { it.status == "SYNCED" }
                .mapNotNull { payloadSignature(it.payloadJson) }
                .any { it == newestSignature }

            if (newestSignature != null && alreadyConfirmedSamePayload) {
                if (suppressRow(
                        statement = suppressStatement,
                        rowId = newestQueued.id,
                        now = now,
                        reason = "Suppressed duplicate TRACK_CHUNK: same payload already SYNCED"
                    )
                ) {
                    changed++
                    Log.w(
                        TAG,
                        "SYNC_QUEUE_DUPLICATE_TRACK_SUPPRESSED queueId=${newestQueued.id} " +
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

    private fun suppressRow(
        statement: androidx.sqlite.db.SupportSQLiteStatement,
        rowId: Long,
        now: Long,
        reason: String
    ): Boolean {
        statement.clearBindings()
        statement.bindString(1, reason)
        statement.bindLong(2, now)
        statement.bindLong(3, rowId)
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
