package com.oropeza.urbanapp.asd.data.local

import android.content.Context
import androidx.room.Room

object DbProvider {

    @Volatile private var INSTANCE: AppDatabase? = null

    fun getInstance(context: Context): AppDatabase {
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: buildDb(context.applicationContext).also { INSTANCE = it }
        }
    }

    private fun buildDb(appCtx: Context): AppDatabase {
        return Room.databaseBuilder(
            appCtx,
            AppDatabase::class.java,
            "asd_db"
        )
            .addMigrations(
                Migrations.MIGRATION_4_5,
                Migrations.MIGRATION_5_6,
                Migrations.MIGRATION_6_7,
                Migrations.MIGRATION_7_8,
                Migrations.MIGRATION_8_9,
                Migrations.MIGRATION_9_10,
                Migrations.MIGRATION_10_11,
                Migrations.MIGRATION_11_12,
                Migrations.MIGRATION_12_13,
                Migrations.MIGRATION_13_14,
                Migrations.MIGRATION_14_15,
                Migrations.MIGRATION_15_16,
                Migrations.MIGRATION_16_17,
                Migrations.MIGRATION_17_18,
                Migrations.MIGRATION_18_19,
                Migrations.MIGRATION_19_20,
                Migrations.MIGRATION_20_21,
                GpsOperationalMigrations.MIGRATION_21_22,
                SyncQueueIntegrityMigrations.MIGRATION_22_23
            )
            .build()
    }
}
