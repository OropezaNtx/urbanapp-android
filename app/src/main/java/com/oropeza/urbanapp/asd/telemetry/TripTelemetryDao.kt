package com.oropeza.urbanapp.asd.telemetry

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface TripTelemetryDao {
    @Upsert
    suspend fun upsert(item: TripTelemetry)

    @Query("SELECT * FROM trip_telemetry WHERE tripId = :tripId LIMIT 1")
    suspend fun getByTripId(tripId: Long): TripTelemetry?

    @Query("SELECT * FROM trip_telemetry ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 20): List<TripTelemetry>
}
