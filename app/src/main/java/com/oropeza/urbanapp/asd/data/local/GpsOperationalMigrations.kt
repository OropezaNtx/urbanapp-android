package com.oropeza.urbanapp.asd.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object GpsOperationalMigrations {

    val MIGRATION_21_22 = object : Migration(21, 22) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Hard DB-level idempotency guard for trip closure.
            // A trip can only have one AD/FINAL event.
            db.execSQL("""
                CREATE UNIQUE INDEX IF NOT EXISTS index_StopEvent_one_final_flag_per_trip
                ON StopEvent(tripId)
                WHERE stopName = 'AD/FINAL'
            """.trimIndent())
        }
    }
}
