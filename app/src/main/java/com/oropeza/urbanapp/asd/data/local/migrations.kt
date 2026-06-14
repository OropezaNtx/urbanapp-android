package com.oropeza.urbanapp.asd.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migrations {

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS TrackPoint (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    tripId INTEGER NOT NULL,
                    timeMs INTEGER NOT NULL,
                    lat REAL NOT NULL,
                    lon REAL NOT NULL,
                    accM REAL NOT NULL,
                    provider TEXT NOT NULL
                )
            """.trimIndent())

            db.execSQL("CREATE INDEX IF NOT EXISTS index_TrackPoint_tripId ON TrackPoint(tripId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_TrackPoint_timeMs ON TrackPoint(timeMs)")
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Si hiciste cambios en el esquema entre la versión 5 y 6,
            // añade aquí las sentencias SQL con ALTER TABLE.
        }
    }


    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {

            db.execSQL("""
            CREATE TABLE IF NOT EXISTS CcSession (
                sessionId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                planningId TEXT NOT NULL,
                locationName TEXT NOT NULL,
                aforador TEXT NOT NULL,
                dateDayMs INTEGER NOT NULL,
                base TEXT NOT NULL,
                terminalOrigin TEXT NOT NULL,
                terminalDestination TEXT NOT NULL,
                direction TEXT NOT NULL,
                companyName TEXT NOT NULL,
                derrotero TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
        """.trimIndent())

            db.execSQL("""
            CREATE TABLE IF NOT EXISTS CcEvent (
                eventId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                sessionId INTEGER NOT NULL,
                seqInSession INTEGER NOT NULL,
                eventType TEXT NOT NULL,
                timeMs INTEGER NOT NULL,
                timeIsManual INTEGER NOT NULL,
                plate TEXT,
                eco TEXT,
                vehicleType TEXT,
                pax INTEGER NOT NULL,
                luggageCount INTEGER,
                notes TEXT,
                lat REAL NOT NULL,
                lon REAL NOT NULL,
                accM REAL NOT NULL,
                provider TEXT NOT NULL,
                fixTime INTEGER NOT NULL,
                locationStatus TEXT NOT NULL,
                FOREIGN KEY(sessionId) REFERENCES CcSession(sessionId) ON DELETE CASCADE
            )
        """.trimIndent())

            db.execSQL("CREATE INDEX IF NOT EXISTS index_CcEvent_sessionId ON CcEvent(sessionId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_CcEvent_timeMs ON CcEvent(timeMs)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_CcEvent_seqInSession ON CcEvent(seqInSession)")
        }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE CcSession ADD COLUMN endedAt INTEGER")
        }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {

            db.execSQL("""
            CREATE TABLE IF NOT EXISTS FovSession (
                sessionId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                estacion TEXT NOT NULL,
                ubicacion TEXT NOT NULL,
                sentido TEXT NOT NULL,
                dateDayMs INTEGER NOT NULL,
                esFs TEXT,
                supervisor TEXT,
                aforador TEXT,
                poiKey TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                endedAt INTEGER
            )
        """.trimIndent())

            db.execSQL("""
            CREATE TABLE IF NOT EXISTS FovCatalogItem (
                poiKey TEXT NOT NULL,
                observableId INTEGER NOT NULL,
                ruta TEXT NOT NULL,
                numeroRutaEmpresa TEXT NOT NULL,
                derroteroLetrero TEXT NOT NULL,
                PRIMARY KEY(poiKey, observableId)
            )
        """.trimIndent())

            db.execSQL("""
            CREATE TABLE IF NOT EXISTS FovObservation (
                obsId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                sessionId INTEGER NOT NULL,
                folio TEXT NOT NULL,
                seqInSession INTEGER NOT NULL,
                timeMs INTEGER NOT NULL,
                poiKey TEXT NOT NULL,
                observableId INTEGER NOT NULL,
                ruta TEXT NOT NULL,
                numeroRutaEmpresa TEXT NOT NULL,
                derroteroLetrero TEXT NOT NULL,
                eco TEXT,
                placa TEXT,
                gradoOcupacion TEXT,
                tipoVehiculo TEXT,
                descTipoVehiculo TEXT,
                observaciones TEXT,
                FOREIGN KEY(sessionId) REFERENCES FovSession(sessionId) ON DELETE CASCADE
            )
        """.trimIndent())

            db.execSQL("CREATE INDEX IF NOT EXISTS index_FovObservation_sessionId ON FovObservation(sessionId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_FovObservation_timeMs ON FovObservation(timeMs)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_FovObservation_seqInSession ON FovObservation(seqInSession)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_FovObservation_folio ON FovObservation(folio)")

            db.execSQL("CREATE INDEX IF NOT EXISTS index_FovCatalogItem_poiKey ON FovCatalogItem(poiKey)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_FovCatalogItem_observableId ON FovCatalogItem(observableId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_FovCatalogItem_ruta ON FovCatalogItem(ruta)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_FovCatalogItem_numeroRutaEmpresa ON FovCatalogItem(numeroRutaEmpresa)")
        }
    }

    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {

            // Si por alguna razón no existe, créala (solo si no existiera)
            db.execSQL("""
           CREATE TABLE IF NOT EXISTS FovRouteMaster (
             routeUid TEXT NOT NULL,
             ruta TEXT NOT NULL,
             numeroRutaEmpresa TEXT NOT NULL,
             derroteroLetrero TEXT NOT NULL,
             createdAt INTEGER NOT NULL,
             createdBy TEXT,
             PRIMARY KEY(routeUid)
           )
         """.trimIndent())

            db.execSQL("CREATE INDEX IF NOT EXISTS index_FovRouteMaster_ruta ON FovRouteMaster(ruta)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_FovRouteMaster_numeroRutaEmpresa ON FovRouteMaster(numeroRutaEmpresa)")
        }
    }

    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            addTripOperationalMetadataColumns(db)
        }
    }

    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            addTripOperationalMetadataColumns(db)
        }
    }

    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!tripColumnExists(db, "observerSex")) {
                db.execSQL("ALTER TABLE Trip ADD COLUMN observerSex TEXT")
            }
        }
    }

    val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS AsdRouteCatalogItem (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    catalogId TEXT NOT NULL,
                    routeName TEXT NOT NULL,
                    company TEXT,
                    derrotero TEXT,
                    cromatica TEXT,
                    baseStart TEXT,
                    baseEnd TEXT,
                    observacion TEXT,
                    active INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_AsdRouteCatalogItem_catalogId ON AsdRouteCatalogItem(catalogId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_AsdRouteCatalogItem_routeName ON AsdRouteCatalogItem(routeName)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_AsdRouteCatalogItem_company ON AsdRouteCatalogItem(company)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_AsdRouteCatalogItem_active ON AsdRouteCatalogItem(active)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS AsdFieldPersonCatalogItem (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    personId TEXT NOT NULL,
                    name TEXT NOT NULL,
                    role TEXT NOT NULL,
                    defaultSex TEXT,
                    active INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_AsdFieldPersonCatalogItem_personId ON AsdFieldPersonCatalogItem(personId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_AsdFieldPersonCatalogItem_name ON AsdFieldPersonCatalogItem(name)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_AsdFieldPersonCatalogItem_role ON AsdFieldPersonCatalogItem(role)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_AsdFieldPersonCatalogItem_active ON AsdFieldPersonCatalogItem(active)")
        }
    }

    private fun addTripOperationalMetadataColumns(db: SupportSQLiteDatabase) {
        if (!tripColumnExists(db, "aforador")) {
            db.execSQL("ALTER TABLE Trip ADD COLUMN aforador TEXT")
        }
        if (!tripColumnExists(db, "supervisor")) {
            db.execSQL("ALTER TABLE Trip ADD COLUMN supervisor TEXT")
        }
        if (!tripColumnExists(db, "deviceNumber")) {
            db.execSQL("ALTER TABLE Trip ADD COLUMN deviceNumber TEXT")
        }
    }

    private fun tripColumnExists(db: SupportSQLiteDatabase, columnName: String): Boolean {
        val cursor = db.query("PRAGMA table_info(`Trip`)")
        return try {
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (nameIndex >= 0 && cursor.getString(nameIndex) == columnName) return true
            }
            false
        } finally {
            cursor.close()
        }
    }
}
