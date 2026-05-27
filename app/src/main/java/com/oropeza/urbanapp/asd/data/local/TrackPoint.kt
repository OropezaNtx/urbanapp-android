package com.oropeza.urbanapp.asd.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    indices = [
        Index("tripId"),
        Index("timeMs")
    ]
)
data class TrackPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val timeMs: Long,          // ✅ este es el que usará el DAO
    val lat: Double,
    val lon: Double,
    val accM: Double,
    val provider: String
)
