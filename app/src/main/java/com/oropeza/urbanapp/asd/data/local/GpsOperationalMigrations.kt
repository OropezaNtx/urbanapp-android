package com.oropeza.urbanapp.asd.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object GpsOperationalMigrations {

    val MIGRATION_21_22 = object : Migration(21, 22) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Existing test devices may already have several AD/FINAL events for the same trip.
            // Keep the first one as the official closure and preserve the rest as historical duplicates.
            db.execSQL("""
                UPDATE StopEvent
                SET stopName = 'AD/FINAL_DUPLICATE',
                    notes = COALESCE(notes, '') || ' | DUPLICATE_FINAL_GUARD'
                WHERE stopName = 'AD/FINAL'
                  AND eventId NOT IN (
                    SELECT firstEventId FROM (
                        SELECT MIN(eventId) AS firstEventId
                        FROM StopEvent
                        WHERE stopName = 'AD/FINAL'
                        GROUP BY tripId
                    )
                  )
            """.trimIndent())

            // Hard DB-level idempotency guard for trip closure.
            // A trip can only have one official AD/FINAL event.
            db.execSQL("""
                CREATE UNIQUE INDEX IF NOT EXISTS index_StopEvent_one_final_flag_per_trip
                ON StopEvent(tripId)
                WHERE stopName = 'AD/FINAL'
            """.trimIndent())
        }
    }
}
