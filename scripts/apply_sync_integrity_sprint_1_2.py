from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def read(path):
    return (ROOT / path).read_text(encoding='utf-8-sig')

def write(path, text):
    (ROOT / path).write_text(text, encoding='utf-8')

def replace_once(text, old, new, label):
    if new in text:
        return text
    if old not in text:
        raise RuntimeError(f'No se encontró el bloque esperado: {label}')
    return text.replace(old, new, 1)

# 1) Entity + DB version
p = 'app/src/main/java/com/oropeza/urbanapp/asd/data/local/entities.kt'
t = read(p)
t = replace_once(t,
'''        Index(value = ["entityType"]),
        Index(value = ["priority"]),''',
'''        Index(value = ["entityType"]),
        Index(value = ["parentTripId"]),
        Index(value = ["priority"]),''', 'índice parentTripId')
t = replace_once(t,
'''    val entityLocalId: Long,
    val cloudPath: String? = null,''',
'''    val entityLocalId: Long,
    val parentTripId: Long? = null,
    val cloudPath: String? = null,''', 'campo parentTripId')
write(p, t)

p = 'app/src/main/java/com/oropeza/urbanapp/asd/data/local/AppDatabase.kt'
t = read(p).replace('version = 22,', 'version = 23,')
write(p, t)

# 2) Migration 22 -> 23
migration_path = ROOT / 'app/src/main/java/com/oropeza/urbanapp/asd/data/local/SyncQueueIntegrityMigrations.kt'
migration_path.write_text('''package com.oropeza.urbanapp.asd.data.local

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
''', encoding='utf-8')

p = 'app/src/main/java/com/oropeza/urbanapp/asd/data/local/DbProvider.kt'
t = read(p)
t = replace_once(t,
'''                Migrations.MIGRATION_20_21,
                GpsOperationalMigrations.MIGRATION_21_22''',
'''                Migrations.MIGRATION_20_21,
                GpsOperationalMigrations.MIGRATION_21_22,
                SyncQueueIntegrityMigrations.MIGRATION_22_23''', 'registro migración 22-23')
write(p, t)

# 3) DAO: aggregate by parent and recover stale IN_PROGRESS
p = 'app/src/main/java/com/oropeza/urbanapp/asd/data/local/dao.kt'
t = read(p)
t = replace_once(t,
'''    @Query("SELECT * FROM sync_queue WHERE (status = 'PENDING' OR status = 'FAILED') AND nextAttemptAt <= :now ORDER BY priority ASC, createdAt ASC LIMIT :limit")
    suspend fun getPending(limit: Int, now: Long = System.currentTimeMillis()): List<AsdSyncQueueItem>
''',
'''    @Query("SELECT * FROM sync_queue WHERE (status = 'PENDING' OR status = 'FAILED') AND nextAttemptAt <= :now ORDER BY priority ASC, createdAt ASC LIMIT :limit")
    suspend fun getPending(limit: Int, now: Long = System.currentTimeMillis()): List<AsdSyncQueueItem>

    @Query("""
        UPDATE sync_queue
        SET status = 'FAILED',
            nextAttemptAt = 0,
            lastError = CASE
                WHEN lastError IS NULL OR lastError = '' THEN 'Recovered stale IN_PROGRESS item'
                ELSE lastError
            END,
            updatedAt = :now
        WHERE status = 'IN_PROGRESS' AND updatedAt < :cutoff
    """)
    suspend fun recoverStaleInProgress(cutoff: Long, now: Long = System.currentTimeMillis()): Int
''', 'recuperación IN_PROGRESS')
t = replace_once(t,
'''        SELECT status FROM sync_queue 
        WHERE entityLocalId = :tripId 
          AND (entityType = 'TRIP' OR entityType = 'EVENT' OR entityType = 'TRACK_CHUNK')
''',
'''        SELECT status FROM sync_queue
        WHERE (
            parentTripId = :tripId
            OR (entityType = 'TRIP' AND entityLocalId = :tripId)
        )
          AND (entityType = 'TRIP' OR entityType = 'EVENT' OR entityType = 'TRACK_CHUNK')
''', 'estado agregado por parentTripId')
write(p, t)

# 4) Repository: correct backfill route, persist parent, expose recovery
p = 'app/src/main/java/com/oropeza/urbanapp/asd/data/repository/repository.kt'
t = read(p)
old = '''                    updated++
                    enqueueSync("EVENT", "UPDATE", event.eventId, updatedEvent)
'''
new = '''                    updated++
                    val context = AsdGraph.appContext
                    val identity = UrbanRuntime.identity(context)
                    val workspace = UrbanRuntime.workspace(context)
                    val cloudTripId = "${identity.installationId}_$tripId"
                    val cloudEvent = AsdCloudMapper.toCloudDto(context, updatedEvent, cloudTripId, 0, 0)
                    enqueueSync(
                        type = "EVENT",
                        operation = "UPDATE",
                        localId = event.eventId,
                        payload = cloudEvent,
                        cloudPath = UrbanCloudPaths.tripEventPath(workspace, cloudTripId, cloudEvent.cloudEventId),
                        parentTripId = tripId
                    )
'''
t = replace_once(t, old, new, 'ruta cloud backfill GPS')
t = replace_once(t,
'''        cloudPath: String? = null,
        priority: Int = 1
''',
'''        cloudPath: String? = null,
        priority: Int = 1,
        parentTripId: Long? = null
''', 'parámetro parentTripId')
t = replace_once(t,
'''            syncQueueDao.insert(
                AsdSyncQueueItem(
                    entityType = type,
                    operation = operation,
                    entityLocalId = localId,
                    payloadJson = gson.toJson(payload),
                    cloudPath = effectivePath,
''',
'''            val resolvedParentTripId = parentTripId ?: when (type) {
                "TRIP" -> localId
                "EVENT", "TRACK_CHUNK", "TRACK_SUMMARY" -> effectivePath
                    ?.let { path -> Regex("_[0-9]+(?=/|$)").find(path)?.value?.drop(1)?.toLongOrNull() }
                else -> null
            }

            syncQueueDao.insert(
                AsdSyncQueueItem(
                    entityType = type,
                    operation = operation,
                    entityLocalId = localId,
                    parentTripId = resolvedParentTripId,
                    payloadJson = gson.toJson(payload),
                    cloudPath = effectivePath,
''', 'persistencia parentTripId')
t = replace_once(t,
'''    suspend fun getPendingSyncItems(limit: Int) = syncQueueDao.getPending(limit)
''',
'''    suspend fun recoverStaleSyncItems(staleAfterMs: Long = 10 * 60 * 1000L): Int {
        val now = System.currentTimeMillis()
        return syncQueueDao.recoverStaleInProgress(cutoff = now - staleAfterMs, now = now)
    }
    suspend fun getPendingSyncItems(limit: Int) = syncQueueDao.getPending(limit)
''', 'wrapper recuperación stale')
write(p, t)

# 5) Engine recovers stale items before selecting queue
p = 'app/src/main/java/com/oropeza/urbanapp/asd/sync/cloud/CloudSyncEngine.kt'
t = read(p)
t = replace_once(t,
'''    suspend fun processNextBatch(): Int = withContext(Dispatchers.IO) {
        val pendingItems = repository.getPendingSyncItems(config.maxBatchSize)
''',
'''    suspend fun processNextBatch(): Int = withContext(Dispatchers.IO) {
        repository.recoverStaleSyncItems()
        val pendingItems = repository.getPendingSyncItems(config.maxBatchSize)
''', 'recuperar IN_PROGRESS antes de procesar')
write(p, t)

print('Sprint 1.2 aplicado correctamente.')
