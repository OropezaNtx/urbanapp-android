from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ORG_ID = "afora"
PROJECT_ID = "urban_operations"


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    if new in text:
        print(f"Ya aplicado: {label}")
        return
    if old not in text:
        raise RuntimeError(f"No se encontró el bloque esperado: {label} ({path})")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")
    print(f"Aplicado: {label}")


settings = ROOT / "app/src/main/java/com/oropeza/urbanapp/core/platform/UrbanPlatformSettings.kt"
workspace = ROOT / "app/src/main/java/com/oropeza/urbanapp/core/platform/UrbanWorkspaceRepository.kt"
cloud = ROOT / "app/src/main/java/com/oropeza/urbanapp/asd/sync/cloud/firestore/FirestoreCloudSyncTarget.kt"
dao = ROOT / "app/src/main/java/com/oropeza/urbanapp/asd/data/local/dao.kt"
repo = ROOT / "app/src/main/java/com/oropeza/urbanapp/asd/data/repository/repository.kt"
detail = ROOT / "app/src/main/java/com/oropeza/urbanapp/asd/ui/viewmodel/AsdTripDetailScreen.kt"
trip_list = ROOT / "app/src/main/java/com/oropeza/urbanapp/asd/ui/viewmodel/AsdTripListScreen.kt"

replace_once(
    settings,
    'object UrbanPlatformSettings {\n',
    '''object UrbanPlatformSettings {

    const val DEFAULT_ORGANIZATION_ID = "afora"
    const val DEFAULT_PROJECT_ID = "urban_operations"
''',
    "identificadores operativos compartidos",
)
replace_once(
    settings,
    '''        return prefs.getString(KEY_WORKSPACE_ID, null) 
            ?: prefs.getString(KEY_ORG_ID, "") 
            ?: ""
''',
    '''        return prefs.getString(KEY_WORKSPACE_ID, null)
            ?: prefs.getString(KEY_ORG_ID, null)
            ?: DEFAULT_ORGANIZATION_ID
''',
    "organizationId por defecto",
)
replace_once(
    settings,
    '''        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PROJECT_ID, "") ?: ""
''',
    '''        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PROJECT_ID, DEFAULT_PROJECT_ID) ?: DEFAULT_PROJECT_ID
''',
    "projectId por defecto",
)
replace_once(
    workspace,
    '''        val workspaceId = UrbanPlatformSettings.getWorkspaceId(context).ifBlank { "demo_workspace" }
        val projId = UrbanPlatformSettings.getProjectId(context).ifBlank { "demo_project" }
''',
    '''        val workspaceId = UrbanPlatformSettings.getWorkspaceId(context)
            .ifBlank { UrbanPlatformSettings.DEFAULT_ORGANIZATION_ID }
        val projId = UrbanPlatformSettings.getProjectId(context)
            .ifBlank { UrbanPlatformSettings.DEFAULT_PROJECT_ID }
''',
    "eliminar rutas demo en Android",
)

replace_once(
    cloud,
    'import com.google.firebase.firestore.FirebaseFirestore\n',
    '''import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
''',
    "import FirebaseAuth",
)
replace_once(
    cloud,
    '''    private val db = FirebaseFirestore.getInstance()
    private val gson = Gson()
''',
    '''    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val gson = Gson()

    private suspend fun ensureAuthenticated() {
        if (auth.currentUser == null) {
            auth.signInAnonymously().await()
        }
    }
''',
    "autenticación anónima Android",
)
replace_once(
    cloud,
    '''        return try {
            val payloadMap: Map<String, Any?> = gson.fromJson(item.payloadJson, mapType)
''',
    '''        return try {
            ensureAuthenticated()
            val payloadMap: Map<String, Any?> = gson.fromJson(item.payloadJson, mapType)
''',
    "autenticar antes de escribir",
)
replace_once(
    cloud,
    '''        return try {
            db.document(path)
                .delete()
''',
    '''        return try {
            ensureAuthenticated()
            db.document(path)
                .delete()
''',
    "autenticar antes de eliminar",
)

# Reactivar fallos de permisos una vez corregidas las reglas.
replace_once(
    dao,
    '''    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'FAILED'")
    fun failedCountFlow(): Flow<Int>
''',
    '''    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'FAILED'")
    fun failedCountFlow(): Flow<Int>

    @Query("""
        UPDATE sync_queue
        SET status = 'PENDING', nextAttemptAt = 0, updatedAt = :now
        WHERE status = 'FAILED' OR status = 'DEAD_LETTER'
    """)
    suspend fun reactivateFailed(now: Long = System.currentTimeMillis()): Int
''',
    "reactivación de elementos fallidos",
)
replace_once(
    repo,
    '''    fun syncQueueFailedCountFlow() = syncQueueDao.failedCountFlow()
''',
    '''    fun syncQueueFailedCountFlow() = syncQueueDao.failedCountFlow()
    suspend fun reactivateFailedSyncItems(): Int {
        val reactivated = syncQueueDao.reactivateFailed()
        if (reactivated > 0) {
            com.oropeza.urbanapp.core.platform.sync.UrbanCloudSyncScheduler.syncNow(AsdGraph.appContext)
        }
        return reactivated
    }
''',
    "API para reintentar errores",
)

# Etiqueta correcta para recorridos históricos finalizados.
text = detail.read_text(encoding="utf-8")
text = text.replace(
    'TrackingStatusCard(lastPoint != null && lastAgeMs < 12000L, lastAgeMs, lastPoint, pointCount, runtimeGps, tickMs)',
    'TrackingStatusCard(lastPoint != null && lastAgeMs < 12000L, lastAgeMs, lastPoint, pointCount, runtimeGps, tickMs, isEnded)',
)
text = text.replace(
    'private fun TrackingStatusCard(',
    'private fun TrackingStatusCard(',
)
text = text.replace(
    'runtimeGps: TrackingService.Companion.RuntimeGpsState, nowMs: Long)',
    'runtimeGps: TrackingService.Companion.RuntimeGpsState, nowMs: Long, isEnded: Boolean)',
)
text = text.replace(
    '"RASTREO PENDIENTE"',
    'if (isEnded) "RASTREO FINALIZADO" else "RASTREO PENDIENTE"',
)
detail.write_text(text, encoding="utf-8")
print("Aplicado: etiqueta de rastreo finalizado")

# Sustituye fallbacks demo en la web, sin asumir una ruta concreta.
web_hits = []
for path in ROOT.rglob("*"):
    if not path.is_file() or any(part in {".git", "build", "node_modules"} for part in path.parts):
        continue
    if path.suffix.lower() not in {".js", ".jsx", ".ts", ".tsx", ".env", ".example"} and not path.name.startswith(".env"):
        continue
    try:
        source = path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        continue
    updated = source.replace("demo_workspace", ORG_ID).replace("demo_project", PROJECT_ID)
    if updated != source:
        path.write_text(updated, encoding="utf-8")
        web_hits.append(str(path.relative_to(ROOT)))

print(f"Configuración web actualizada en {len(web_hits)} archivo(s).")
for hit in web_hits:
    print(f"- {hit}")

print("Sprint 1.4 aplicado. Falta desplegar las reglas con Firebase CLI.")
