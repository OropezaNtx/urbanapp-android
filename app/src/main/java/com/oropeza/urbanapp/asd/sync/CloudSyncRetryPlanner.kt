package com.oropeza.urbanapp.asd.sync

import com.oropeza.urbanapp.asd.AsdGraph

/**
 * Calcula cuándo conviene volver a despertar el drenado cloud usando el
 * nextAttemptAt persistido en sync_queue como única fuente de verdad del backoff.
 */
object CloudSyncRetryPlanner {
    private const val FALLBACK_DELAY_MS = 10 * 60_000L

    fun nextDelayMs(now: Long = System.currentTimeMillis()): Long {
        val sql = """
            SELECT MIN(nextAttemptAt)
            FROM sync_queue
            WHERE status = 'PENDING' OR status = 'FAILED'
        """.trimIndent()

        val cursor = AsdGraph.db.openHelper.readableDatabase.query(sql)
        val nextAt = cursor.use {
            if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null
        }

        return when {
            nextAt == null -> FALLBACK_DELAY_MS
            nextAt <= now -> 1_000L
            else -> (nextAt - now).coerceAtLeast(1_000L)
        }
    }
}
