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

    val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Eliminar índice único viejo
            db.execSQL("DROP INDEX IF EXISTS index_AsdRouteCatalogItem_catalogId")
            
            // Agregar columna direction (DEFAULT 'IDA')
            db.execSQL("ALTER TABLE AsdRouteCatalogItem ADD COLUMN direction TEXT NOT NULL DEFAULT 'IDA'")
            
            // Crear nuevo índice único compuesto
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_AsdRouteCatalogItem_catalogId_direction ON AsdRouteCatalogItem(catalogId, direction)")
        }
    }

    val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS AsdCatalogSyncState (
                    id TEXT PRIMARY KEY NOT NULL,
                    version TEXT,
                    lastSyncAt INTEGER,
                    source TEXT,
                    routesCount INTEGER NOT NULL,
                    peopleCount INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    message TEXT
                )
            """.trimIndent())
        }
    }

    val MIGRATION_16_17 = object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // ✅ Agregar columnas de altitud con DEFAULT 0.0
            db.execSQL("ALTER TABLE TrackPoint ADD COLUMN altM REAL NOT NULL DEFAULT 0.0")
            db.execSQL("ALTER TABLE StopEvent ADD COLUMN stopAltM REAL NOT NULL DEFAULT 0.0")
            db.execSQL("ALTER TABLE StopEvent ADD COLUMN startAltM REAL NOT NULL DEFAULT 0.0")
        }
    }

    val MIGRATION_17_18 = object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1. Crear tabla de catálogo de tipos de unidad
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS AsdVehicleTypeCatalogItem (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    vehicleTypeId TEXT NOT NULL,
                    name TEXT NOT NULL,
                    displayName TEXT NOT NULL,
                    defaultSeatCapacity INTEGER NOT NULL,
                    capacityApplies INTEGER NOT NULL,
                    sortOrder INTEGER NOT NULL,
                    active INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_AsdVehicleTypeCatalogItem_vehicleTypeId ON AsdVehicleTypeCatalogItem(vehicleTypeId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_AsdVehicleTypeCatalogItem_name ON AsdVehicleTypeCatalogItem(name)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_AsdVehicleTypeCatalogItem_active ON AsdVehicleTypeCatalogItem(active)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_AsdVehicleTypeCatalogItem_sortOrder ON AsdVehicleTypeCatalogItem(sortOrder)")

            // 2. Agregar columna a estado de sincronización
            db.execSQL("ALTER TABLE AsdCatalogSyncState ADD COLUMN vehicleTypesCount INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATION_18_19 = object : Migration(18, 19) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS AsdSyncQueueItem (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    entityType TEXT NOT NULL,
                    entityLocalId INTEGER NOT NULL,
                    payloadJson TEXT NOT NULL,
                    status TEXT NOT NULL,
                    attempts INTEGER NOT NULL,
                    lastError TEXT,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_AsdSyncQueueItem_status ON AsdSyncQueueItem(status)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_AsdSyncQueueItem_entityType ON AsdSyncQueueItem(entityType)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_AsdSyncQueueItem_createdAt ON AsdSyncQueueItem(createdAt)")
        }
    }

    val MIGRATION_19_20 = object : Migration(19, 20) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1. Renombrar tabla
            db.execSQL("ALTER TABLE AsdSyncQueueItem RENAME TO sync_queue")
            
            // 2. Agregar nuevas columnas
            db.execSQL("ALTER TABLE sync_queue ADD COLUMN operation TEXT NOT NULL DEFAULT 'CREATE'")
            db.execSQL("ALTER TABLE sync_queue ADD COLUMN cloudPath TEXT")
            db.execSQL("ALTER TABLE sync_queue ADD COLUMN priority INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE sync_queue ADD COLUMN nextAttemptAt INTEGER NOT NULL DEFAULT 0")

            // 3. Recrear índices (los de la tabla vieja se mantienen pero el nombre cambia)
            db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_queue_status ON sync_queue(status)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_queue_nextAttemptAt ON sync_queue(nextAttemptAt)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_queue_entityType ON sync_queue(entityType)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_queue_priority ON sync_queue(priority)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_queue_createdAt ON sync_queue(createdAt)")
        }
    }

    val MIGRATION_20_21 = object : Migration(20, 21) {
        override fun migrate(db: SupportSQLiteDatabase) {
            addTrackPointColumn(db, "rawLat", "REAL NOT NULL DEFAULT 0.0")
            addTrackPointColumn(db, "rawLon", "REAL NOT NULL DEFAULT 0.0")
            addTrackPointColumn(db, "rawAltM", "REAL NOT NULL DEFAULT 0.0")
            addTrackPointColumn(db, "filteredLat", "REAL NOT NULL DEFAULT 0.0")
            addTrackPointColumn(db, "filteredLon", "REAL NOT NULL DEFAULT 0.0")
            addTrackPointColumn(db, "sourceFixTimeMs", "INTEGER NOT NULL DEFAULT 0")
            addTrackPointColumn(db, "receivedAtMs", "INTEGER NOT NULL DEFAULT 0")
            addTrackPointColumn(db, "savedAtMs", "INTEGER NOT NULL DEFAULT 0")
            addTrackPointColumn(db, "sampleStatus", "TEXT NOT NULL DEFAULT 'LIVE'")
            addTrackPointColumn(db, "qualityStatus", "TEXT NOT NULL DEFAULT 'UNKNOWN'")
            addTrackPointColumn(db, "geometryStatus", "TEXT NOT NULL DEFAULT 'GEOMETRY_OK'")
            addTrackPointColumn(db, "filterStatus", "TEXT NOT NULL DEFAULT 'raw'")
            addTrackPointColumn(db, "engineMode", "TEXT NOT NULL DEFAULT 'NA'")
            addTrackPointColumn(db, "armStatus", "TEXT NOT NULL DEFAULT 'QUICK'")
            addTrackPointColumn(db, "isStale", "INTEGER NOT NULL DEFAULT 0")
            addTrackPointColumn(db, "isBackfillEligible", "INTEGER NOT NULL DEFAULT 0")
            addTrackPointColumn(db, "isSynthetic", "INTEGER NOT NULL DEFAULT 0")

            db.execSQL("UPDATE TrackPoint SET rawLat = lat WHERE rawLat = 0.0")
            db.execSQL("UPDATE TrackPoint SET rawLon = lon WHERE rawLon = 0.0")
            db.execSQL("UPDATE TrackPoint SET rawAltM = altM WHERE rawAltM = 0.0")
            db.execSQL("UPDATE TrackPoint SET filteredLat = lat WHERE filteredLat = 0.0")
            db.execSQL("UPDATE TrackPoint SET filteredLon = lon WHERE filteredLon = 0.0")
            db.execSQL("UPDATE TrackPoint SET sourceFixTimeMs = timeMs WHERE sourceFixTimeMs = 0")
            db.execSQL("UPDATE TrackPoint SET receivedAtMs = timeMs WHERE receivedAtMs = 0")
            db.execSQL("UPDATE TrackPoint SET savedAtMs = timeMs WHERE savedAtMs = 0")
            db.execSQL("UPDATE TrackPoint SET sampleStatus = 'NO_FIX', qualityStatus = 'NO_FIX', geometryStatus = 'NO_FIX', isSynthetic = 1 WHERE lat = 0.0 AND lon = 0.0")
            db.execSQL("UPDATE TrackPoint SET sampleStatus = 'STALE', isStale = 1 WHERE provider LIKE '%+STALE+%'")
            db.execSQL("UPDATE TrackPoint SET sampleStatus = 'LIVE' WHERE provider LIKE '%+LIVE+%'")
            db.execSQL("UPDATE TrackPoint SET qualityStatus = 'GOOD_ACCURACY' WHERE provider LIKE '%+GOOD_ACCURACY+%'")
            db.execSQL("UPDATE TrackPoint SET qualityStatus = 'USABLE_ACCURACY' WHERE provider LIKE '%+USABLE_ACCURACY+%'")
            db.execSQL("UPDATE TrackPoint SET qualityStatus = 'LOW_ACCURACY' WHERE provider LIKE '%+LOW_ACCURACY+%'")
            db.execSQL("UPDATE TrackPoint SET qualityStatus = 'VERY_LOW_ACCURACY' WHERE provider LIKE '%+VERY_LOW_ACCURACY+%'")
            db.execSQL("UPDATE TrackPoint SET geometryStatus = 'SUSPECT_SPEED' WHERE provider LIKE '%+SUSPECT_SPEED%'")
            db.execSQL("UPDATE TrackPoint SET geometryStatus = 'SUSPECT_JUMP' WHERE provider LIKE '%+SUSPECT_JUMP%'")
            db.execSQL("UPDATE TrackPoint SET filterStatus = 'kalman' WHERE provider LIKE '%+kalman+%'")
            db.execSQL("UPDATE TrackPoint SET engineMode = 'ACQUIRE' WHERE provider LIKE '%+ACQUIRE+%'")
            db.execSQL("UPDATE TrackPoint SET engineMode = 'TRACK' WHERE provider LIKE '%+TRACK+%'")
            db.execSQL("UPDATE TrackPoint SET engineMode = 'STILL' WHERE provider LIKE '%+STILL+%'")
            db.execSQL("UPDATE TrackPoint SET armStatus = 'ARMED' WHERE provider LIKE '%+ARMED+%'")
            db.execSQL("UPDATE TrackPoint SET isBackfillEligible = 1 WHERE isStale = 0 AND accM > 0.0 AND accM <= 60.0 AND geometryStatus = 'GEOMETRY_OK'")

            db.execSQL("CREATE INDEX IF NOT EXISTS index_TrackPoint_sampleStatus ON TrackPoint(sampleStatus)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_TrackPoint_qualityStatus ON TrackPoint(qualityStatus)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_TrackPoint_geometryStatus ON TrackPoint(geometryStatus)")
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
        return tableColumnExists(db, "Trip", columnName)
    }

    private fun addTrackPointColumn(db: SupportSQLiteDatabase, columnName: String, columnSql: String) {
        if (!tableColumnExists(db, "TrackPoint", columnName)) {
            db.execSQL("ALTER TABLE TrackPoint ADD COLUMN $columnName $columnSql")
        }
    }

    private fun tableColumnExists(db: SupportSQLiteDatabase, tableName: String, columnName: String): Boolean {
        val cursor = db.query("PRAGMA table_info(`$tableName`)")
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
