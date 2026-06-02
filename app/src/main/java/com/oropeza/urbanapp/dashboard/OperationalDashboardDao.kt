package com.oropeza.urbanapp.dashboard

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OperationalDashboardDao {
    @Query("SELECT COUNT(*) FROM FovSession")
    fun fovSessionsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM FovSession WHERE endedAt IS NULL")
    fun fovOpenSessionsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM FovObservation")
    fun fovObservationsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM FovObservation WHERE lat != 0.0 AND lon != 0.0 AND locationStatus != 'NO_FIX'")
    fun fovValidGpsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM Trip")
    fun asdTripsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM Trip WHERE endTime IS NULL")
    fun asdOpenTripsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM StopEvent")
    fun asdEventsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM TrackPoint")
    fun asdTrackPointsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM CcSession")
    fun ccSessionsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM CcSession WHERE endedAt IS NULL")
    fun ccOpenSessionsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM CcEvent")
    fun ccEventsCount(): Flow<Int>
}
