package com.oropeza.urbanapp.asd.data.local

import android.content.Context
import androidx.room.Room

object DbProvider {

    @Volatile private var INSTANCE: AppDatabase? = null

    // ✅ Cambia a false cuando ya quieras respetar migraciones
    private const val DEV_MODE = true

    fun getInstance(context: Context): AppDatabase {
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: buildDb(context.applicationContext).also { INSTANCE = it }
        }
    }

    private fun buildDb(appCtx: Context): AppDatabase {
        val builder = Room.databaseBuilder(
            appCtx,
            AppDatabase::class.java,
            "asd_db"
        )

        return if (DEV_MODE) {
            builder
                .fallbackToDestructiveMigration()
                .build()
        } else {
            builder
                .addMigrations(
                    Migrations.MIGRATION_4_5,
                    Migrations.MIGRATION_5_6,
                    Migrations.MIGRATION_6_7,
                    Migrations.MIGRATION_7_8,
                    Migrations.MIGRATION_8_9,
                    Migrations.MIGRATION_9_10,
                    Migrations.MIGRATION_10_11
                )
                .build()
        }
    }
}
