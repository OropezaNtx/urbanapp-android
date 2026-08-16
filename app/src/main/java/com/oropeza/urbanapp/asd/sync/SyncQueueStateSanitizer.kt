package com.oropeza.urbanapp.asd.sync

import android.util.Log
import com.oropeza.urbanapp.asd.AsdGraph

/**
 * Repairs internally contradictory or redundant queue metadata without deleting
 * source-of-truth trip/track data from Room.
 *
 * A SYNCED row is already confirmed by the cloud target, so retaining a historical
 * lastError makes diagnostics lie. TRACK_CHUNK identity is its deterministic cloudPath;
 * duplicate queue rows for the same path must never cause repeated uploads while idle.
 */
object SyncQueueStateSanitizer {
    private const val TAG = "CloudSyncIntegrity"

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

        // If this exact Firestore document was already confirmed, any additional
        // PENDING/FAILED TRACK_CHUNK row is redundant and must not consume another write.
        val confirmedDuplicates = db.compileStatement(
            """
                UPDATE sync_queue
                SET status = 'DEAD_LETTER',
                    lastError = 'Suppressed duplicate TRACK_CHUNK: cloudPath already SYNCED',
                    nextAttemptAt = 0,
                    updatedAt = ${System.currentTimeMillis()}
                WHERE entityType = 'TRACK_CHUNK'
                  AND status IN ('PENDING', 'FAILED')
                  AND cloudPath IS NOT NULL
                  AND EXISTS (
                      SELECT 1
                      FROM sync_queue confirmed
                      WHERE confirmed.entityType = 'TRACK_CHUNK'
                        AND confirmed.cloudPath = sync_queue.cloudPath
                        AND confirmed.status = 'SYNCED'
                  )
            """.trimIndent()
        ).executeUpdateDelete()
        changed += confirmedDuplicates
        if (confirmedDuplicates > 0) {
            Log.w(TAG, "SYNC_QUEUE_DUPLICATE_TRACK_SUPPRESSED rows=$confirmedDuplicates rule=CLOUD_PATH_ALREADY_SYNCED")
        }

        // If several unsent copies of the same deterministic chunk accumulated, keep only
        // the newest one. This preserves one legitimate upload attempt while eliminating
        // backlog amplification from repeated reconciliation runs.
        val queuedDuplicates = db.compileStatement(
            """
                UPDATE sync_queue
                SET status = 'DEAD_LETTER',
                    lastError = 'Suppressed duplicate TRACK_CHUNK: newer queue row exists',
                    nextAttemptAt = 0,
                    updatedAt = ${System.currentTimeMillis()}
                WHERE entityType = 'TRACK_CHUNK'
                  AND status IN ('PENDING', 'FAILED')
                  AND cloudPath IS NOT NULL
                  AND EXISTS (
                      SELECT 1
                      FROM sync_queue newer
                      WHERE newer.entityType = 'TRACK_CHUNK'
                        AND newer.cloudPath = sync_queue.cloudPath
                        AND newer.status IN ('PENDING', 'FAILED')
                        AND newer.id > sync_queue.id
                  )
            """.trimIndent()
        ).executeUpdateDelete()
        changed += queuedDuplicates
        if (queuedDuplicates > 0) {
            Log.w(TAG, "SYNC_QUEUE_DUPLICATE_TRACK_SUPPRESSED rows=$queuedDuplicates rule=KEEP_NEWEST_UNSENT")
        }

        return changed
    }
}
