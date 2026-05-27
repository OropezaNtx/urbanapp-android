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

    // ✅ NUEVO: último punto como Flow (para UI)
    @Query("SELECT * FROM TrackPoint WHERE tripId = :tripId ORDER BY timeMs DESC LIMIT 1")
    fun getLatestFlow(tripId: Long): Flow<TrackPoint?>

    // ✅ NUEVO: conteo como Flow (para UI)
    @Query("SELECT COUNT(*) FROM TrackPoint WHERE tripId = :tripId")
    fun countFlow(tripId: Long): Flow<Int>

    // ✅ NUEVO: puntos por rango (para cálculos: distancia, etc.)
    @Query("""
        SELECT * FROM TrackPoint
        WHERE tripId = :tripId AND timeMs BETWEEN :fromMs AND :toMs
        ORDER BY timeMs ASC
    """)
    suspend fun getBetweenOnce(tripId: Long, fromMs: Long, toMs: Long): List<TrackPoint>

    @Query("DELETE FROM TrackPoint WHERE tripId = :tripId")
    suspend fun deleteByTrip(tripId: Long)
}
