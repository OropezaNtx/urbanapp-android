package com.oropeza.urbanapp.asd.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object SyncQueueIntegrityMigrations {
    val MIGRATION_22_23 = object : Migration(22, 23) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE sync_queue ADD COLUMN parentTripId INTEGER")
            db.execSQL("UPDATE sync_queue SET parentTripId = entityLocalId WHERE entityType = 'TRIP'")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_queue_parentTripId ON sync_queue(parentTripId)")
            // Una ejecución interrumpida no debe dejar registros bloqueados para siempre.
            db.execSQL("UPDATE sync_queue SET status = 'FAILED', nextAttemptAt = 0, lastError = COALESCE(lastError, 'Recovered after database upgrade') WHERE status = 'IN_PROGRESS'")
        }
    }
}
