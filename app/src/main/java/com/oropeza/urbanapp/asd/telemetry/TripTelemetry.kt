package com.oropeza.urbanapp.asd.telemetry

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.oropeza.urbanapp.asd.data.local.Trip

/**
 * Resumen persistente y acumulativo de telemetría de un recorrido.
 *
 * Android/Room es la fuente de verdad. La nube recibe una proyección de esta fila.
 * No contiene muestras GPS; esas siguen perteneciendo a TrackPoint.
 */
@Entity(
    tableName = "trip_telemetry",
    foreignKeys = [
        ForeignKey(
            entity = Trip::class,
            parentColumns = ["tripId"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tripId"), Index("updatedAt")]
)
data class TripTelemetry(
    @PrimaryKey val tripId: Long,
    val schemaVersion: Int = 1,
    val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
    val updatedAt: Long = System.currentTimeMillis(),

    // Battery
    val batterySampleCount: Long = 0L,
    val batterySumPct: Long = 0L,
    val batteryStartPct: Int? = null,
    val batteryEndPct: Int? = null,
    val batteryMinPct: Int? = null,
    val batteryMaxPct: Int? = null,
    val chargingSampleCount: Long = 0L,

    // Tracking heartbeat
    val heartbeatSampleCount: Long = 0L,
    val heartbeatAgeSumMs: Long = 0L,
    val heartbeatMaxAgeMs: Long = 0L,
    val heartbeatStaleSamples: Long = 0L,
    val lastHeartbeatAt: Long? = null,

    // Network
    val networkSampleCount: Long = 0L,
    val connectedSampleCount: Long = 0L,
    val offlineDurationMs: Long = 0L,
    val networkTransitionCount: Int = 0,
    val reconnectionCount: Int = 0,
    val lastNetworkConnected: Boolean? = null,
    val lastNetworkType: String? = null,
    val lastSampleAt: Long? = null,

    // Recovery / watchdog
    val recoveryCount: Int = 0,
    val assistedRecoveryCount: Int = 0,
    val fgsBlockedCount: Int = 0,
    val watchdogHealthyCount: Int = 0,
    val lastRecoveryAt: Long? = null,
    val lastRecoveryGapMs: Long = 0L,

    // Cloud sync
    val syncRunCount: Int = 0,
    val syncRetryCount: Int = 0,
    val syncFailedItemCount: Int = 0,
    val syncConfirmedItemCount: Int = 0,
    val syncPayloadBytes: Long = 0L,
    val firstUploadStartedAt: Long? = null,
    val lastUploadFinishedAt: Long? = null,
    val lastCloudConfirmedAt: Long? = null,
    val totalCloudRoundTripMs: Long = 0L,
    val maxCloudRoundTripMs: Long = 0L,
    val lastSyncResult: String? = null
) {
    val batteryAveragePct: Double?
        get() = if (batterySampleCount > 0) batterySumPct.toDouble() / batterySampleCount else null

    val heartbeatAverageAgeMs: Double?
        get() = if (heartbeatSampleCount > 0) heartbeatAgeSumMs.toDouble() / heartbeatSampleCount else null

    val networkCoveragePct: Double?
        get() = if (networkSampleCount > 0) connectedSampleCount.toDouble() * 100.0 / networkSampleCount else null

    val averageCloudRoundTripMs: Double?
        get() = if (syncConfirmedItemCount > 0) totalCloudRoundTripMs.toDouble() / syncConfirmedItemCount else null
}
