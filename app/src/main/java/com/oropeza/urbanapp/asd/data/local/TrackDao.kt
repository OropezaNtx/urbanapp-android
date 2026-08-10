package com.oropeza.urbanapp.asd.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(p: TrackPoint): Long

    @Query("SELECT * FROM TrackPoint WHERE tripId = :tripId ORDER BY timeMs ASC")
    fun getByTrip(tripId: Long): Flow<List<TrackPoint>>

    @Query("SELECT * FROM TrackPoint WHERE tripId = :tripId ORDER BY timeMs ASC")
    suspend fun getByTripOnce(tripId: Long): List<TrackPoint>

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
