package com.oropeza.urbanapp.asd.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    indices = [
        Index("tripId"),
        Index("timeMs"),
        Index("sampleStatus"),
        Index("qualityStatus"),
        Index("geometryStatus")
    ]
)
data class TrackPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val timeMs: Long,

    /** Main coordinate used by current map/export consumers. Usually filtered when safe. */
    val lat: Double,
    val lon: Double,
    val altM: Double = 0.0,
    val accM: Double,
    val provider: String,

    /** Raw coordinate exactly as delivered by the Android/Fused provider. */
    val rawLat: Double = lat,
    val rawLon: Double = lon,
    val rawAltM: Double = altM,

    /** Filtered coordinate produced by the engine; mirrors lat/lon when no filter is applied. */
    val filteredLat: Double = lat,
    val filteredLon: Double = lon,

    /** Provider timing/audit metadata. */
    val sourceFixTimeMs: Long = timeMs,
    val receivedAtMs: Long = timeMs,
    val savedAtMs: Long = timeMs,

    /** Professional GPS sample state. */
    val sampleStatus: String = "LIVE",          // LIVE | STALE | NO_FIX
    val qualityStatus: String = "UNKNOWN",      // GOOD_ACCURACY | USABLE_ACCURACY | LOW_ACCURACY | VERY_LOW_ACCURACY | NO_FIX
    val geometryStatus: String = "GEOMETRY_OK", // GEOMETRY_OK | SUSPECT_SPEED | SUSPECT_JUMP | NO_FIX
    val filterStatus: String = "raw",           // raw | kalman
    val engineMode: String = "NA",              // ACQUIRE | TRACK | STILL | IDLE
    val armStatus: String = "QUICK",            // QUICK | ARMED

    /** Explicit flags for downstream export/dashboard/playback decisions. */
    val isStale: Boolean = false,
    val isBackfillEligible: Boolean = false,
    val isSynthetic: Boolean = false
)
