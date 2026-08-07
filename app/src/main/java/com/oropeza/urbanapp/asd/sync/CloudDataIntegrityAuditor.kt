package com.oropeza.urbanapp.asd.sync

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.oropeza.urbanapp.asd.AsdGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Sprint 2.3.1 — Auditor read-only Android <-> Firestore.
 *
 * No corrige, reencola ni reescribe datos. Solo audita recorridos cerrados cuya
 * cola lógica ya quedó completamente SYNCED para evitar falsos positivos.
 */
object CloudDataIntegrityAuditor {
    private const val TAG = "DataIntegrity"
    private const val RECENT_WINDOW_MS = 6 * 60 * 60 * 1000L
    private const val MAX_TRIPS_PER_AUDIT = 5

    data class AuditResult(
        val tripId: Long,
        val cloudTripPath: String,
        val roomPointCount: Int,
        val expectedEventCount: Int,
        val expectedChunkCount: Int,
        val cloudTripExists: Boolean,
        val cloudEventCount: Int,
        val cloudChunkCount: Int,
        val cloudPointCount: Int,
        val state: String
    )

    private data class QueueRow(
        val id: Long,
        val entityType: String,
        val cloudPath: String?,
        val status: String
    )

    suspend fun auditRelevantTrips(stage: String, runId: String) = withContext(Dispatchers.IO) {
        val tripIds = recentCandidateTripIds()
        Log.i(TAG, "DATA_AUDIT_STARTED stage=$stage runId=$runId candidates=${tripIds.size}")

        if (tripIds.isEmpty()) {
            Log.i(TAG, "DATA_AUDIT_SUCCESS stage=$stage runId=$runId audited=0 reason=NO_ELIGIBLE_TRIPS")
            return@withContext
        }

        ensureAuthenticated()
        var success = 0
        var mismatch = 0
        var skipped = 0

        tripIds.forEach { tripId ->
            val trip = AsdGraph.db.tripDao().getByIdOnce(tripId)
            if (trip == null || trip.endTime == null) {
                skipped++
                Log.i(TAG, "DATA_AUDIT_TRIP trip=$tripId state=SKIPPED reason=TRIP_OPEN_OR_MISSING")
                return@forEach
            }

            val logicalRows = latestLogicalRows(tripId)
            if (logicalRows.isEmpty() || logicalRows.any { it.status != "SYNCED" }) {
                skipped++
                Log.i(TAG, "DATA_AUDIT_TRIP trip=$tripId state=SKIPPED reason=QUEUE_NOT_SETTLED")
                return@forEach
            }

            val tripRow = logicalRows
                .filter { it.entityType == "TRIP" && !it.cloudPath.isNullOrBlank() }
                .maxByOrNull { it.id }
            val tripPath = tripRow?.cloudPath
            if (tripPath.isNullOrBlank()) {
                skipped++
                Log.w(TAG, "DATA_AUDIT_TRIP trip=$tripId state=SKIPPED reason=NO_CLOUD_TRIP_PATH")
                return@forEach
            }

            try {
                val result = auditTrip(tripId, tripPath, logicalRows)
                logResult(result, stage, runId)
                if (result.state == "COMPLETE") success++ else mismatch++
            } catch (e: Exception) {
                mismatch++
                Log.e(
                    TAG,
                    "DATA_AUDIT_MISMATCH stage=$stage runId=$runId trip=$tripId reason=AUDIT_EXCEPTION error=${e.javaClass.simpleName}:${e.message}",
                    e
                )
            }
        }

        val finalState = if (mismatch == 0) "DATA_AUDIT_SUCCESS" else "DATA_AUDIT_MISMATCH"
        val message = "$finalState stage=$stage runId=$runId audited=${success + mismatch} complete=$success mismatches=$mismatch skipped=$skipped"
        if (mismatch == 0) Log.i(TAG, message) else Log.w(TAG, message)
    }

    private suspend fun auditTrip(
        tripId: Long,
        tripPath: String,
        logicalRows: List<QueueRow>
    ): AuditResult {
        val db = FirebaseFirestore.getInstance()
        val roomPointCount = AsdGraph.db.trackDao().getByTripOnce(tripId).size
        val expectedEvents = logicalRows.count { it.entityType == "EVENT" }
        val expectedChunks = logicalRows.count { it.entityType == "TRACK_CHUNK" }

        val tripSnapshot = db.document(tripPath).get().await()
        val eventSnapshot = db.collection("$tripPath/events").get().await()
        val chunkSnapshot = db.collection("$tripPath/track_chunks").get().await()
        val cloudPointCount = chunkSnapshot.documents.sumOf { doc ->
            (doc.get("pointCount") as? Number)?.toInt() ?: 0
        }

        val state = if (
            tripSnapshot.exists() &&
            eventSnapshot.size() == expectedEvents &&
            chunkSnapshot.size() == expectedChunks &&
            cloudPointCount == roomPointCount
        ) "COMPLETE" else "MISMATCH"

        return AuditResult(
            tripId = tripId,
            cloudTripPath = tripPath,
            roomPointCount = roomPointCount,
            expectedEventCount = expectedEvents,
            expectedChunkCount = expectedChunks,
            cloudTripExists = tripSnapshot.exists(),
            cloudEventCount = eventSnapshot.size(),
            cloudChunkCount = chunkSnapshot.size(),
            cloudPointCount = cloudPointCount,
            state = state
        )
    }

    private fun logResult(result: AuditResult, stage: String, runId: String) {
        Log.i(
            TAG,
            "DATA_AUDIT_TRIP stage=$stage runId=$runId trip=${result.tripId} state=${result.state} cloudTripExists=${result.cloudTripExists} path=${result.cloudTripPath}"
        )
        Log.i(
            TAG,
            "DATA_AUDIT_EVENT stage=$stage runId=$runId trip=${result.tripId} expected=${result.expectedEventCount} cloud=${result.cloudEventCount} delta=${result.cloudEventCount - result.expectedEventCount}"
        )
        Log.i(
            TAG,
            "DATA_AUDIT_TRACK stage=$stage runId=$runId trip=${result.tripId} roomPoints=${result.roomPointCount} expectedChunks=${result.expectedChunkCount} cloudChunks=${result.cloudChunkCount} cloudPoints=${result.cloudPointCount} pointDelta=${result.cloudPointCount - result.roomPointCount}"
        )

        if (result.state == "COMPLETE") {
            Log.i(TAG, "DATA_AUDIT_SUCCESS stage=$stage runId=$runId trip=${result.tripId}")
        } else {
            Log.w(
                TAG,
                "DATA_AUDIT_MISMATCH stage=$stage runId=$runId trip=${result.tripId} " +
                    "tripExists=${result.cloudTripExists} eventsExpected=${result.expectedEventCount} eventsCloud=${result.cloudEventCount} " +
                    "chunksExpected=${result.expectedChunkCount} chunksCloud=${result.cloudChunkCount} " +
                    "roomPoints=${result.roomPointCount} cloudPoints=${result.cloudPointCount}"
            )
        }
    }

    private suspend fun ensureAuthenticated() {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) auth.signInAnonymously().await()
    }

    /**
     * Última fila por cloudPath = estado lógico actual. Esto evita que reintentos
     * históricos/duplicados antiguos produzcan falsos positivos.
     */
    private fun latestLogicalRows(tripId: Long): List<QueueRow> {
        val sql = """
            SELECT id, entityType, cloudPath, status
            FROM sync_queue
            WHERE parentTripId = ?
              AND entityType IN ('TRIP','EVENT','TRACK_CHUNK')
              AND cloudPath IS NOT NULL
            ORDER BY id ASC
        """.trimIndent()
        val cursor = AsdGraph.db.openHelper.readableDatabase.query(sql, arrayOf(tripId.toString()))
        val rows = cursor.use {
            val idIndex = it.getColumnIndexOrThrow("id")
            val typeIndex = it.getColumnIndexOrThrow("entityType")
            val pathIndex = it.getColumnIndexOrThrow("cloudPath")
            val statusIndex = it.getColumnIndexOrThrow("status")
            buildList {
                while (it.moveToNext()) {
                    add(
                        QueueRow(
                            id = it.getLong(idIndex),
                            entityType = it.getString(typeIndex),
                            cloudPath = if (it.isNull(pathIndex)) null else it.getString(pathIndex),
                            status = it.getString(statusIndex)
                        )
                    )
                }
            }
        }
        return rows
            .groupBy { it.cloudPath }
            .values
            .mapNotNull { group -> group.maxByOrNull { it.id } }
    }

    private fun recentCandidateTripIds(): List<Long> {
        val cutoff = System.currentTimeMillis() - RECENT_WINDOW_MS
        val sql = """
            SELECT parentTripId, MAX(updatedAt) AS lastUpdate
            FROM sync_queue
            WHERE parentTripId IS NOT NULL
              AND entityType IN ('TRIP','EVENT','TRACK_CHUNK')
              AND updatedAt >= ?
            GROUP BY parentTripId
            ORDER BY lastUpdate DESC
            LIMIT $MAX_TRIPS_PER_AUDIT
        """.trimIndent()
        val cursor = AsdGraph.db.openHelper.readableDatabase.query(sql, arrayOf(cutoff.toString()))
        return cursor.use {
            val tripIndex = it.getColumnIndexOrThrow("parentTripId")
            buildList { while (it.moveToNext()) add(it.getLong(tripIndex)) }
        }
    }
}
