from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    if new in text:
        print(f"Ya aplicado: {label}")
        return
    if old not in text:
        raise RuntimeError(f"No se encontró el bloque esperado: {label} ({path})")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")
    print(f"Aplicado: {label}")


dao = ROOT / "app/src/main/java/com/oropeza/urbanapp/asd/data/local/dao.kt"
repo = ROOT / "app/src/main/java/com/oropeza/urbanapp/asd/data/repository/repository.kt"
worker = ROOT / "app/src/main/java/com/oropeza/urbanapp/asd/sync/AsdCloudSyncWorker.kt"

replace_once(
    dao,
    '''    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'PENDING' OR status = 'FAILED'")
    fun pendingCountFlow(): Flow<Int>
''',
    '''    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'PENDING' OR status = 'FAILED'")
    fun pendingCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'PENDING' OR status = 'FAILED' OR status = 'IN_PROGRESS'")
    suspend fun getOutstandingCount(): Int
''',
    "conteo suspendido de elementos pendientes"
)

replace_once(
    repo,
    '''    suspend fun getPendingSyncItems(limit: Int) = syncQueueDao.getPending(limit)
''',
    '''    suspend fun getPendingSyncItems(limit: Int) = syncQueueDao.getPending(limit)
    suspend fun getOutstandingSyncCount(): Int = syncQueueDao.getOutstandingCount()
''',
    "acceso al conteo pendiente"
)

replace_once(
    worker,
    '''            val remaining = AsdGraph.repo.getPendingSyncItems(1).isNotEmpty()
            when {
                remaining -> {
                    Log.w(TAG, "Quedan elementos pendientes; WorkManager continuará con backoff")
                    ListenableWorker.Result.retry()
                }

                else -> {
                    Log.i(TAG, "Sincronización en background terminada. Exitosos: $processed")
                    ListenableWorker.Result.success()
                }
            }
''',
    '''            val outstanding = AsdGraph.repo.getOutstandingSyncCount()
            when {
                outstanding > 0 -> {
                    Log.w(
                        TAG,
                        "Quedan $outstanding elementos por resolver; WorkManager continuará automáticamente con backoff"
                    )
                    ListenableWorker.Result.retry()
                }

                else -> {
                    Log.i(TAG, "Sincronización en background terminada. Exitosos: $processed")
                    ListenableWorker.Result.success()
                }
            }
''',
    "reintento automático hasta vaciar la cola"
)

print("Sincronización continua aplicada correctamente.")
