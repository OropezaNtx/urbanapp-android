package com.oropeza.urbanapp.asd.sync

import com.oropeza.urbanapp.asd.AsdGraph

/**
 * Small read-only health view over the durable sync queue.
 *
 * UI counters historically treated only PENDING/FAILED as outstanding. Operationally,
 * however, IN_PROGRESS and DEAD_LETTER also mean the cloud backup is not settled.
 * This helper makes that distinction explicit without changing the queue schema.
 */
object SyncQueueHealth {
    data class Snapshot(
        val pending: Int,
        val failed: Int,
        val inProgress: Int,
        val deadLetter: Int
    ) {
        val retryable: Int get() = pending + failed + inProgress
        val unresolved: Int get() = retryable + deadLetter
        val isSettled: Boolean get() = unresolved == 0
    }

    fun snapshot(): Snapshot {
        val sql = """
            SELECT
                SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END) AS pending,
                SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) AS failed,
                SUM(CASE WHEN status = 'IN_PROGRESS' THEN 1 ELSE 0 END) AS inProgress,
                SUM(CASE WHEN status = 'DEAD_LETTER' THEN 1 ELSE 0 END) AS deadLetter
            FROM sync_queue
        """.trimIndent()

        val cursor = AsdGraph.db.openHelper.readableDatabase.query(sql)
        return cursor.use {
            if (!it.moveToFirst()) return@use Snapshot(0, 0, 0, 0)
            Snapshot(
                pending = it.getInt(it.getColumnIndexOrThrow("pending")),
                failed = it.getInt(it.getColumnIndexOrThrow("failed")),
                inProgress = it.getInt(it.getColumnIndexOrThrow("inProgress")),
                deadLetter = it.getInt(it.getColumnIndexOrThrow("deadLetter"))
            )
        }
    }
}
