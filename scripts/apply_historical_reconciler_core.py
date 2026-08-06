from pathlib import Path

root = Path(__file__).resolve().parents[1]
dao = root / "app/src/main/java/com/oropeza/urbanapp/asd/data/local/dao.kt"
repo = root / "app/src/main/java/com/oropeza/urbanapp/asd/data/repository/repository.kt"
lst = root / "app/src/main/java/com/oropeza/urbanapp/asd/ui/viewmodel/AsdTripListScreen.kt"

def rep(path, old, new, name):
    s = path.read_text(encoding="utf-8")
    if old not in s:
        raise RuntimeError(f"No se encontró {name}")
    path.write_text(s.replace(old, new, 1), encoding="utf-8")

rep(dao,
'''    @Query("SELECT * FROM Trip ORDER BY startTime DESC")
    fun getAll(): Flow<List<Trip>>
''',
'''    @Query("SELECT * FROM Trip ORDER BY startTime DESC")
    fun getAll(): Flow<List<Trip>>

    @Query("SELECT * FROM Trip ORDER BY startTime ASC")
    suspend fun getAllOnce(): List<Trip>
''', "TripDao.getAllOnce")

rep(dao,
'''interface AsdSyncQueueDao {
    @Insert suspend fun insert(item: AsdSyncQueueItem): Long
''',
'''interface AsdSyncQueueDao {
    @Insert suspend fun insert(item: AsdSyncQueueItem): Long

    @Query("""
        SELECT * FROM sync_queue
        WHERE entityType = :entityType
          AND entityLocalId = :entityLocalId
          AND parentTripId = :parentTripId
          AND cloudPath = :cloudPath
        ORDER BY id DESC LIMIT 1
    """)
    suspend fun findLogicalItem(entityType: String, entityLocalId: Long, parentTripId: Long, cloudPath: String): AsdSyncQueueItem?

    @Query("SELECT COUNT(*) FROM sync_queue WHERE parentTripId = :tripId AND entityType = :entityType")
    suspend fun countTypeForTrip(tripId: Long, entityType: String): Int
''', "consultas idempotentes")

marker = "    fun getTripSyncStatusFlow(tripId: Long): Flow<AsdTripSyncStatus> {"
block = '''    data class HistoricalReconciliationReport(
        val scannedTrips: Int,
        val queuedTrips: Int,
        val queuedEvents: Int,
        val queuedTrackSets: Int,
        val skippedExisting: Int,
        val errors: List<String>
    )

    suspend fun reconcileHistoricalTrips(): HistoricalReconciliationReport {
        val context = AsdGraph.appContext
        val workspace = UrbanRuntime.workspace(context)
        val identity = UrbanRuntime.identity(context)
        val trips = tripDao.getAllOnce()
        var queuedTrips = 0
        var queuedEvents = 0
        var queuedTrackSets = 0
        var skipped = 0
        val errors = mutableListOf<String>()

        trips.forEach { trip ->
            try {
                val cloudTripId = "${identity.installationId}_${trip.tripId}"
                val tripPath = UrbanCloudPaths.tripPath(workspace, cloudTripId)
                if (syncQueueDao.findLogicalItem("TRIP", trip.tripId, trip.tripId, tripPath) == null) {
                    enqueueSync("TRIP", "UPSERT", trip.tripId, AsdCloudMapper.toCloudDto(context, trip), tripPath, parentTripId = trip.tripId)
                    queuedTrips++
                } else skipped++

                var men = 0
                var women = 0
                stopDao.getByTripOnce(trip.tripId).forEach { event ->
                    men = (men + event.paxMenUp - event.paxMenDown).coerceAtLeast(0)
                    women = (women + event.paxWomenUp - event.paxWomenDown).coerceAtLeast(0)
                    val dto = AsdCloudMapper.toCloudDto(context, event, cloudTripId, men, women)
                    val path = UrbanCloudPaths.tripEventPath(workspace, cloudTripId, dto.cloudEventId)
                    if (syncQueueDao.findLogicalItem("EVENT", event.eventId, trip.tripId, path) == null) {
                        enqueueSync("EVENT", "UPSERT", event.eventId, dto, path, parentTripId = trip.tripId)
                        queuedEvents++
                    } else skipped++
                }

                if (trip.endTime != null && syncQueueDao.countTypeForTrip(trip.tripId, "TRACK_CHUNK") == 0) {
                    enqueueTrackChunks(trip.tripId)
                    queuedTrackSets++
                }
            } catch (t: Throwable) {
                errors += "Trip ${trip.tripId}: ${t.message ?: t::class.java.simpleName}"
            }
        }
        com.oropeza.urbanapp.asd.sync.AsdCloudSyncWorker.enqueue(context)
        return HistoricalReconciliationReport(trips.size, queuedTrips, queuedEvents, queuedTrackSets, skipped, errors)
    }

'''
s = repo.read_text(encoding="utf-8")
if marker not in s:
    raise RuntimeError("No se encontró getTripSyncStatusFlow")
repo.write_text(s.replace(marker, block + marker, 1), encoding="utf-8")

rep(lst,
'''    init {
        checkForRecovery()
    }
''',
'''    init {
        checkForRecovery()
        viewModelScope.launch { AsdGraph.repo.reconcileHistoricalTrips() }
    }
''', "reconciliación automática")

print("Núcleo del reconciliador histórico aplicado.")
