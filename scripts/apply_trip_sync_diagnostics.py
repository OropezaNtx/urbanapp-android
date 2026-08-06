from pathlib import Path

root = Path(__file__).resolve().parents[1]
dao = root / "app/src/main/java/com/oropeza/urbanapp/asd/data/local/dao.kt"
repo = root / "app/src/main/java/com/oropeza/urbanapp/asd/data/repository/repository.kt"
detail = root / "app/src/main/java/com/oropeza/urbanapp/asd/ui/viewmodel/AsdTripDetailScreen.kt"

def rep(path, old, new, name):
    s = path.read_text(encoding="utf-8")
    if old not in s:
        raise RuntimeError(f"No se encontró {name}")
    path.write_text(s.replace(old, new, 1), encoding="utf-8")

s = dao.read_text(encoding="utf-8")
marker = "@Dao\ninterface AsdSyncQueueDao"
model = '''data class AsdTripSyncDiagnostics(
    val totalCount: Int,
    val pendingCount: Int,
    val inProgressCount: Int,
    val syncedCount: Int,
    val failedCount: Int,
    val deadLetterCount: Int,
    val lastError: String?,
    val lastFailedPath: String?,
    val lastUpdatedAt: Long?
)

'''
if marker not in s:
    raise RuntimeError("No se encontró AsdSyncQueueDao")
dao.write_text(s.replace(marker, model + marker, 1), encoding="utf-8")

rep(dao,
'''    fun getTripSyncItemStatusesFlow(tripId: Long): Flow<List<String>>
}''',
'''    fun getTripSyncItemStatusesFlow(tripId: Long): Flow<List<String>>

    @Query("""
        SELECT COUNT(*) totalCount,
        SUM(CASE WHEN status='PENDING' THEN 1 ELSE 0 END) pendingCount,
        SUM(CASE WHEN status='IN_PROGRESS' THEN 1 ELSE 0 END) inProgressCount,
        SUM(CASE WHEN status='SYNCED' THEN 1 ELSE 0 END) syncedCount,
        SUM(CASE WHEN status='FAILED' THEN 1 ELSE 0 END) failedCount,
        SUM(CASE WHEN status='DEAD_LETTER' THEN 1 ELSE 0 END) deadLetterCount,
        (SELECT lastError FROM sync_queue q2 WHERE (q2.parentTripId=:tripId OR (q2.entityType='TRIP' AND q2.entityLocalId=:tripId)) AND q2.lastError IS NOT NULL ORDER BY q2.updatedAt DESC LIMIT 1) lastError,
        (SELECT cloudPath FROM sync_queue q3 WHERE (q3.parentTripId=:tripId OR (q3.entityType='TRIP' AND q3.entityLocalId=:tripId)) AND q3.lastError IS NOT NULL ORDER BY q3.updatedAt DESC LIMIT 1) lastFailedPath,
        MAX(updatedAt) lastUpdatedAt
        FROM sync_queue
        WHERE parentTripId=:tripId OR (entityType='TRIP' AND entityLocalId=:tripId)
    """)
    fun getTripDiagnosticsFlow(tripId: Long): Flow<AsdTripSyncDiagnostics>
}''', "consulta diagnóstico")

marker2 = "    fun getTripSyncStatusFlow(tripId: Long): Flow<AsdTripSyncStatus> {"
s = repo.read_text(encoding="utf-8")
if marker2 not in s:
    raise RuntimeError("No se encontró getTripSyncStatusFlow")
repo.write_text(s.replace(marker2, "    fun getTripSyncDiagnosticsFlow(tripId: Long) = syncQueueDao.getTripDiagnosticsFlow(tripId)\n\n" + marker2, 1), encoding="utf-8")

rep(detail,
'''import com.oropeza.urbanapp.asd.data.local.AsdTripSyncStatus
''',
'''import com.oropeza.urbanapp.asd.data.local.AsdTripSyncStatus
import com.oropeza.urbanapp.asd.data.local.AsdTripSyncDiagnostics
''', "import diagnóstico")
rep(detail,
'''    fun tripSyncStatusFlow(tripId: Long) = AsdGraph.repo.getTripSyncStatusFlow(tripId)
''',
'''    fun tripSyncStatusFlow(tripId: Long) = AsdGraph.repo.getTripSyncStatusFlow(tripId)
    fun tripSyncDiagnosticsFlow(tripId: Long) = AsdGraph.repo.getTripSyncDiagnosticsFlow(tripId)
''', "VM diagnóstico")
rep(detail,
'''    val syncStatus by vm.tripSyncStatusFlow(tripId).collectAsState(initial = AsdTripSyncStatus.NOT_QUEUED)
''',
'''    val syncStatus by vm.tripSyncStatusFlow(tripId).collectAsState(initial = AsdTripSyncStatus.NOT_QUEUED)
    val syncDiagnostics by vm.tripSyncDiagnosticsFlow(tripId).collectAsState(initial = AsdTripSyncDiagnostics(0,0,0,0,0,0,null,null,null))
''', "collect diagnóstico")
rep(detail,
'''    AsdTripDetailContent(tripId, trip, stops, lastPoint, pointCount, trackPoints, syncStatus, pendingSyncCount,''',
'''    AsdTripDetailContent(tripId, trip, stops, lastPoint, pointCount, trackPoints, syncStatus, syncDiagnostics, pendingSyncCount,''', "pasar diagnóstico")
rep(detail,
'''syncStatus: AsdTripSyncStatus, pendingSyncCount: Int,''',
'''syncStatus: AsdTripSyncStatus, syncDiagnostics: AsdTripSyncDiagnostics, pendingSyncCount: Int,''', "firma diagnóstico")
rep(detail,
'''            item { CloudSyncStatusCard(syncStatus, pendingSyncCount, lastSyncTimeMs, { scope.launch { val res = UrbanRuntime.syncNow(context); if (res.isSuccess) snackbarHostState.showSnackbar("Respaldo finalizado ✅") } }) }
''',
'''            item { CloudSyncStatusCard(syncStatus, pendingSyncCount, lastSyncTimeMs, { scope.launch { val res = UrbanRuntime.syncNow(context); if (res.isSuccess) snackbarHostState.showSnackbar("Respaldo finalizado ✅") } }) }
            if (!syncDiagnostics.lastError.isNullOrBlank()) item { AforaInlineAlert("SYNC: ${syncDiagnostics.lastError}${syncDiagnostics.lastFailedPath?.let { " | $it" }.orEmpty()}", colors.Danger) }
''', "alerta diagnóstico")

print("Diagnóstico por recorrido aplicado.")
