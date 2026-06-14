package com.oropeza.urbanapp.asd.data.local
import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class WaypointPair(val inId: Int, val outId: Int)

@Dao
interface TripDao {
    @Insert suspend fun insert(trip: Trip): Long
    @Update suspend fun update(trip: Trip): Int

    @Query("SELECT * FROM Trip ORDER BY startTime DESC")
    fun getAll(): Flow<List<Trip>>

    @Query("SELECT * FROM Trip WHERE tripId = :id LIMIT 1")
    fun getById(id: Long): Flow<Trip?>

    @Query("SELECT * FROM Trip WHERE tripId = :id LIMIT 1")
    suspend fun getByIdOnce(id: Long): Trip?

    @Query("SELECT nextWaypointId FROM Trip WHERE tripId = :tripId")
    suspend fun getNextWaypoint(tripId: Long): Int

    @Query("UPDATE Trip SET nextWaypointId = :value WHERE tripId = :tripId")
    suspend fun updateNextWaypoint(tripId: Long, value: Int): Int

    @Transaction
    suspend fun reserveWaypointPair(tripId: Long): WaypointPair {
        val base = getNextWaypoint(tripId)
        updateNextWaypoint(tripId, base + 2)
        return WaypointPair(inId = base, outId = base + 1)
    }

    @Transaction
    suspend fun reserveSingleWaypoint(tripId: Long): WaypointPair {
        val base = getNextWaypoint(tripId)
        updateNextWaypoint(tripId, base + 1)
        return WaypointPair(inId = base, outId = base)
    }
}

@Dao
interface StopDao {
    @Insert suspend fun insert(event: StopEvent): Long
    @Update suspend fun update(event: StopEvent): Int

    @Query("SELECT * FROM StopEvent WHERE tripId = :tripId ORDER BY timestamp ASC")
    fun getByTrip(tripId: Long): Flow<List<StopEvent>>

    @Query("SELECT * FROM StopEvent WHERE tripId = :tripId ORDER BY timestamp ASC")
    suspend fun getByTripOnce(tripId: Long): List<StopEvent>

    @Query("""
        SELECT * FROM StopEvent
        WHERE tripId = :tripId 
          AND stopType = 'BANDERA'
          AND startTime = 0
        ORDER BY timestamp DESC
        LIMIT 1
    """)
    suspend fun getActiveBandera(tripId: Long): StopEvent?

    @Query("""
        SELECT * FROM StopEvent
        WHERE tripId = :tripId
          AND (
            locationStatus = 'GPS_PENDING'
            OR stopLat = 0.0
            OR stopLon = 0.0
          )
        ORDER BY timestamp ASC
        LIMIT :limit
    """)
    suspend fun getPendingGpsEvents(tripId: Long, limit: Int = 10): List<StopEvent>

    @Query("""
        UPDATE StopEvent
        SET stopLat = :lat,
            stopLon = :lon,
            stopAccM = :accM,
            stopProvider = :provider,
            stopFixTime = :fixTime,
            startLat = CASE WHEN startLat = 0.0 THEN :lat ELSE startLat END,
            startLon = CASE WHEN startLon = 0.0 THEN :lon ELSE startLon END,
            startAccM = CASE WHEN startAccM = 0.0 THEN :accM ELSE startAccM END,
            startProvider = CASE WHEN startProvider = '' THEN :provider ELSE startProvider END,
            startFixTime = CASE WHEN startFixTime = 0 THEN :fixTime ELSE startFixTime END,
            locationStatus = :status
        WHERE eventId = :eventId
    """)
    suspend fun updateEventGpsFix(
        eventId: Long,
        lat: Double,
        lon: Double,
        accM: Double,
        provider: String,
        fixTime: Long,
        status: String
    ): Int
}

@Dao
interface DelayDao {
    @Insert suspend fun insert(e: DelayEvent): Long
    @Update suspend fun update(e: DelayEvent): Int

    @Query("SELECT * FROM DelayEvent WHERE tripId = :tripId ORDER BY timestampStart ASC")
    fun getByTrip(tripId: Long): Flow<List<DelayEvent>>

    @Query("SELECT * FROM DelayEvent WHERE tripId = :tripId ORDER BY timestampStart ASC")
    suspend fun getByTripOnce(tripId: Long): List<DelayEvent>

    @Query("""
        SELECT * FROM DelayEvent 
        WHERE tripId = :tripId AND timestampEnd IS NULL 
        ORDER BY timestampStart DESC 
        LIMIT 1
    """)
    suspend fun getActiveDelay(tripId: Long): DelayEvent?
}

@Dao
interface CcSessionDao {
    @Insert suspend fun insert(s: CcSession): Long

    @Query("SELECT * FROM CcSession ORDER BY createdAt DESC")
    fun getAll(): Flow<List<CcSession>>

    @Query("SELECT * FROM CcSession ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestOnce(): CcSession?

    @Query("SELECT * FROM CcSession WHERE sessionId = :id LIMIT 1")
    fun getById(id: Long): Flow<CcSession?>

    @Query("SELECT * FROM CcSession WHERE sessionId = :id LIMIT 1")
    suspend fun getByIdOnce(id: Long): CcSession?

    @Query("UPDATE CcSession SET endedAt = :endedAt WHERE sessionId = :sessionId")
    suspend fun endSession(sessionId: Long, endedAt: Long): Int
}

@Dao
interface CcEventDao {
    @Insert suspend fun insert(e: CcEvent): Long

    @Query("SELECT * FROM CcEvent WHERE sessionId = :sessionId ORDER BY timeMs ASC")
    fun getBySession(sessionId: Long): Flow<List<CcEvent>>

    @Query("SELECT MAX(seqInSession) FROM CcEvent WHERE sessionId = :sessionId")
    suspend fun getMaxSeq(sessionId: Long): Int?
}

@Dao
interface FovPoiCatalogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: FovPoiCatalogItem): Long

    @Query("SELECT * FROM FovPoiCatalogItem WHERE poiKey = :poiKey AND active = 1 ORDER BY observableId ASC")
    fun getByPoi(poiKey: String): Flow<List<FovPoiCatalogItem>>

    @Query("SELECT MAX(observableId) FROM FovPoiCatalogItem WHERE poiKey = :poiKey")
    suspend fun getMaxObservableId(poiKey: String): Int?

    @Query("SELECT * FROM FovPoiCatalogItem WHERE poiKey = :poiKey AND observableId = :observableId LIMIT 1")
    suspend fun getByObservableOnce(poiKey: String, observableId: Int): FovPoiCatalogItem?

    @Query("SELECT * FROM FovPoiCatalogItem WHERE poiKey = :poiKey AND routeUid = :routeUid LIMIT 1")
    suspend fun getByRouteOnce(poiKey: String, routeUid: String): FovPoiCatalogItem?

    @Query("""
    SELECT
      c.poiKey AS poiKey,
      c.observableId AS observableId,
      c.routeUid AS routeUid,
      m.ruta AS ruta,
      m.numeroRutaEmpresa AS numeroRutaEmpresa,
      m.derroteroLetrero AS derroteroLetrero
    FROM FovPoiCatalogItem c
    JOIN FovRouteMaster m ON m.routeUid = c.routeUid
    WHERE c.poiKey = :poiKey
      AND c.active = 1
      AND (
          LOWER(m.ruta) LIKE '%' || LOWER(:q) || '%' OR
          LOWER(m.numeroRutaEmpresa) LIKE '%' || LOWER(:q) || '%' OR
          LOWER(m.derroteroLetrero) LIKE '%' || LOWER(:q) || '%'
      )
    ORDER BY c.observableId ASC
    LIMIT 200
    """)
    suspend fun searchPoiCatalogOnce(poiKey: String, q: String): List<FovPoiCatalogRow>
}

data class FovPoiCatalogRow(
    val poiKey: String,
    val observableId: Int,
    val routeUid: String,
    val ruta: String,
    val numeroRutaEmpresa: String,
    val derroteroLetrero: String
)

@Dao
interface FovObservationDao {
    @Insert suspend fun insert(o: FovObservation): Long

    @Query("SELECT * FROM FovObservation WHERE sessionId = :sessionId ORDER BY timeMs ASC")
    fun getBySession(sessionId: Long): Flow<List<FovObservation>>

    @Query("SELECT * FROM FovObservation WHERE sessionId = :sessionId ORDER BY timeMs ASC")
    suspend fun getBySessionOnce(sessionId: Long): List<FovObservation>

    @Query("SELECT MAX(seqInSession) FROM FovObservation WHERE sessionId = :sessionId")
    suspend fun getMaxSeq(sessionId: Long): Int?

    @Query("SELECT * FROM FovObservation WHERE folio = :folio LIMIT 1")
    suspend fun getByFolioOnce(folio: String): FovObservation?
}

@Dao
interface FovRouteMasterDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: FovRouteMaster): Long

    @Query("SELECT * FROM FovRouteMaster WHERE routeUid = :uid LIMIT 1")
    suspend fun getByUidOnce(uid: String): FovRouteMaster?

    @Query("""
        SELECT * FROM FovRouteMaster
        WHERE ruta LIKE '%' || :q || '%'
           OR numeroRutaEmpresa LIKE '%' || :q || '%'
           OR derroteroLetrero LIKE '%' || :q || '%'
        ORDER BY ruta ASC
        LIMIT 200
    """)
    suspend fun searchOnce(q: String): List<FovRouteMaster>
}

data class FovSessionRow(
    @Embedded val s: FovSession,
    val catalogCount: Int
)

@Dao
interface FovSessionDao {
    @Insert suspend fun insert(s: FovSession): Long

    @Query("SELECT * FROM FovSession ORDER BY createdAt DESC")
    fun getAll(): Flow<List<FovSession>>

    @Query("""
        SELECT 
          s.*,
          COALESCE(c.cnt, 0) AS catalogCount
        FROM FovSession s
        LEFT JOIN (
          SELECT poiKey, COUNT(*) AS cnt
          FROM FovPoiCatalogItem
          WHERE active = 1
          GROUP BY poiKey
        ) c
        ON c.poiKey = s.poiKey
        ORDER BY s.createdAt DESC
    """)
    fun getAllWithCatalogCount(): Flow<List<FovSessionRow>>

    @Query("SELECT * FROM FovSession WHERE sessionId = :id LIMIT 1")
    fun getById(id: Long): Flow<FovSession?>

    @Query("SELECT * FROM FovSession WHERE sessionId = :id LIMIT 1")
    suspend fun getByIdOnce(id: Long): FovSession?

    @Query("UPDATE FovSession SET endedAt = :endedAt WHERE sessionId = :sessionId")
    suspend fun endSession(sessionId: Long, endedAt: Long): Int
}
