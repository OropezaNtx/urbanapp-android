package com.oropeza.urbanapp.asd.telemetry

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object TelemetryMigrations {
    val MIGRATION_23_24 = object : Migration(23, 24) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `trip_telemetry` (
                    `tripId` INTEGER NOT NULL,
                    `schemaVersion` INTEGER NOT NULL,
                    `startedAt` INTEGER NOT NULL,
                    `finishedAt` INTEGER,
                    `updatedAt` INTEGER NOT NULL,
                    `batterySampleCount` INTEGER NOT NULL,
                    `batterySumPct` INTEGER NOT NULL,
                    `batteryStartPct` INTEGER,
                    `batteryEndPct` INTEGER,
                    `batteryMinPct` INTEGER,
                    `batteryMaxPct` INTEGER,
                    `chargingSampleCount` INTEGER NOT NULL,
                    `heartbeatSampleCount` INTEGER NOT NULL,
                    `heartbeatAgeSumMs` INTEGER NOT NULL,
                    `heartbeatMaxAgeMs` INTEGER NOT NULL,
                    `heartbeatStaleSamples` INTEGER NOT NULL,
                    `lastHeartbeatAt` INTEGER,
                    `networkSampleCount` INTEGER NOT NULL,
                    `connectedSampleCount` INTEGER NOT NULL,
                    `offlineDurationMs` INTEGER NOT NULL,
                    `networkTransitionCount` INTEGER NOT NULL,
                    `reconnectionCount` INTEGER NOT NULL,
                    `lastNetworkConnected` INTEGER,
                    `lastNetworkType` TEXT,
                    `lastSampleAt` INTEGER,
                    `recoveryCount` INTEGER NOT NULL,
                    `assistedRecoveryCount` INTEGER NOT NULL,
                    `fgsBlockedCount` INTEGER NOT NULL,
                    `watchdogHealthyCount` INTEGER NOT NULL,
                    `lastRecoveryAt` INTEGER,
                    `lastRecoveryGapMs` INTEGER NOT NULL,
                    `syncRunCount` INTEGER NOT NULL,
                    `syncRetryCount` INTEGER NOT NULL,
                    `syncFailedItemCount` INTEGER NOT NULL,
                    `syncConfirmedItemCount` INTEGER NOT NULL,
                    `syncPayloadBytes` INTEGER NOT NULL,
                    `firstUploadStartedAt` INTEGER,
                    `lastUploadFinishedAt` INTEGER,
                    `lastCloudConfirmedAt` INTEGER,
                    `lastSyncRunStartedAt` INTEGER,
                    `lastSyncRunFinishedAt` INTEGER,
                    `lastSyncRunDurationMs` INTEGER,
                    `totalSyncRunDurationMs` INTEGER NOT NULL,
                    `maxSyncRunDurationMs` INTEGER NOT NULL,
                    `completedSyncRunCount` INTEGER NOT NULL,
                    `totalCloudRoundTripMs` INTEGER NOT NULL,
                    `maxCloudRoundTripMs` INTEGER NOT NULL,
                    `lastSyncResult` TEXT,
                    PRIMARY KEY(`tripId`),
                    FOREIGN KEY(`tripId`) REFERENCES `Trip`(`tripId`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_trip_telemetry_tripId` ON `trip_telemetry` (`tripId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_trip_telemetry_updatedAt` ON `trip_telemetry` (`updatedAt`)")
        }
    }
}
