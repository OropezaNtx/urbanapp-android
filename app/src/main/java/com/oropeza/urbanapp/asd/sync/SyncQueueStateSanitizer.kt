package com.oropeza.urbanapp.asd.sync

import android.util.Log
import com.oropeza.urbanapp.asd.AsdGraph

/**
 * Repairs only internally contradictory queue metadata.
 *
 * A SYNCED row is already confirmed by the cloud target, so retaining a historical
 * lastError (for example "Job was cancelled") makes diagnostics lie even though the
 * durable state says the upload succeeded. Real FAILED/DEAD_LETTER evidence is never
 * touched here.
 */
object SyncQueueStateSanitizer {
    private const val TAG = "CloudSyncIntegrity"

    fun sanitize(): Int {
        val db = AsdGraph.db.openHelper.writableDatabase
        val statement = db.compileStatement(
            """
                UPDATE sync_queue
                SET lastError = NULL,
                    nextAttemptAt = 0
                WHERE status = 'SYNCED'
                  AND (lastError IS NOT NULL OR nextAttemptAt != 0)
            """.trimIndent()
        )
        val changed = statement.executeUpdateDelete()
        if (changed > 0) {
            Log.i(TAG, "SYNC_QUEUE_STATE_SANITIZED rows=$changed rule=SYNCED_HAS_NO_ERROR")
        }
        return changed
    }
}
