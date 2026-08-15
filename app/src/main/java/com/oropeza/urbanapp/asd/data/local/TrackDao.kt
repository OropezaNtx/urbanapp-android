package com.oropeza.urbanapp.asd.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {

    /**
     * Hard persistence boundary: a finalized trip must never accept new GPS rows.
     *
     * TrackingService already checks Trip.endTime before sampling, but that check and
     * the actual insert are separate operations. Keeping the invariant here protects
     * Room from late coroutines, delayed service callbacks or future callers that may
     * attempt to persist after the trip has been closed.
     *
     * -1 mirrors Room's IGNORE sentinel so existing callers already treat the write as
     * rejected instead of counting it as a persisted sample.
     */
    @Transaction
    suspend fun insert(p: TrackPoint): Long {
        if (isTripOpenForTracking(p.tripId) <= 0) return -1L
        return insertRaw(p)
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRaw(p: TrackPoint): Long

    @Query("SELECT COUNT(*) FROM Trip WHERE tripId = :tripId AND endTime IS NULL")
    suspend fun isTripOpenForTracking(tripId: Long): Int

    @Query("SELECT * FROM TrackPoint WHERE tripId = :tripId ORDER BY timeMs ASC")
    fun getByTrip(tripId: Long): Flow<List<TrackPoint>>

    @Query("SELECT * FROM TrackPoint WHERE tripId = :tripId ORDER BY timeMs ASC")
    suspend fun getByTripOnce(tripId: Long): List<TrackPoint>

    @Query("SELECT * FROM TrackPoint WHERE tripId = :tripId AND timeMs <= :toMs ORDER BY timeMs ASC")
    suspend fun getThroughOnce(tripId: Long, toMs: Long): List<TrackPoint>

    @Query("SELECT * FROM TrackPoint WHERE tripId = :tripId AND timeMs <= :toMs ORDER BY timeMs ASC LIMIT :limit OFFSET :offset")
    suspend fun getPageThroughOnce(tripId: Long, toMs: Long, limit: Int, offset: Int): List<TrackPoint>

    @Query("SELECT COUNT(*) FROM TrackPoint WHERE tripId = :tripId")
    suspend fun countOnce(tripId: Long): Int

    @Query("SELECT COUNT(*) FROM TrackPoint WHERE tripId = :tripId AND timeMs <= :toMs")
    suspend fun countThroughOnce(tripId: Long, toMs: Long): Int

    @Query("SELECT * FROM TrackPoint WHERE tripId = :tripId ORDER BY timeMs DESC LIMIT 1")
    suspend fun getLatest(tripId: Long): TrackPoint?

    // The event-capture UI must not consume a point already classified by the GPS
    // engine as stale, synthetic, low-confidence or geometrically suspect. Raw and
    // rejected points remain fully persisted and available through getByTrip* for
    // audit/completeness/export diagnostics.
    @Query("""
        SELECT * FROM TrackPoint
        WHERE tripId = :tripId
          AND sampleStatus = 'LIVE'
          AND qualityStatus IN ('GOOD_ACCURACY', 'USABLE_ACCURACY')
          AND geometryStatus = 'GEOMETRY_OK'
          AND isStale = 0
          AND isSynthetic = 0
          AND lat BETWEEN -90.0 AND 90.0
          AND lon BETWEEN -180.0 AND 180.0
          AND NOT (lat = 0.0 AND lon = 0.0)
        ORDER BY timeMs DESC
        LIMIT 1
    """)
    fun getLatestFlow(tripId: Long): Flow<TrackPoint?>

    @Query("SELECT COUNT(*) FROM TrackPoint WHERE tripId = :tripId")
    fun countFlow(tripId: Long): Flow<Int>

    @Query("""
        SELECT * FROM TrackPoint
        WHERE tripId = :tripId AND timeMs BETWEEN :fromMs AND :toMs
        ORDER BY timeMs ASC
    """)
    suspend fun getBetweenOnce(tripId: Long, fromMs: Long, toMs: Long): List<TrackPoint>

    @Query("DELETE FROM TrackPoint WHERE tripId = :tripId")
    suspend fun deleteByTrip(tripId: Long): Int
}
