package com.oropeza.urbanapp.asd.data.repository

import android.util.Log
import com.oropeza.urbanapp.asd.AsdGraph
import com.oropeza.urbanapp.asd.backup.AsdOnlineBackup
import com.oropeza.urbanapp.asd.data.local.*
import com.oropeza.urbanapp.asd.location.engine.TrackPointQuality
import com.oropeza.urbanapp.asd.sync.cloud.*
import com.oropeza.urbanapp.core.identity.UrbanIdentityProvider
import com.oropeza.urbanapp.core.platform.UrbanCloudPaths
import com.oropeza.urbanapp.core.platform.UrbanPlatformSettings
import com.oropeza.urbanapp.core.events.UrbanEventFactory
import com.oropeza.urbanapp.core.events.UrbanEventTypes
import com.oropeza.urbanapp.core.runtime.UrbanRuntime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.max
import com.oropeza.urbanapp.asd.live.LiveEventBus
import com.oropeza.urbanapp.asd.live.LiveSignal

class AsdRepository(private val db: AppDatabase) {

    private val tripDao = db.tripDao()
    private val stopDao = db.stopDao()
    private val delayDao = db.delayDao()
    private val trackDao = db.trackDao()

    private val ccSessionDao = db.ccSessionDao()
    private val ccEventDao = db.ccEventDao()

    private val asdRouteCatalogDao = db.asdRouteCatalogDao()
    private val asdFieldPersonCatalogDao = db.asdFieldPersonCatalogDao()
    private val asdVehicleTypeCatalogDao = db.asdVehicleTypeCatalogDao()
    private val asdCatalogSyncStateDao = db.asdCatalogSyncStateDao()
    private val syncQueueDao = db.asdSyncQueueDao()

    private val gson = com.google.gson.Gson()

    val tripsFlow: Flow<List<Trip>> = tripDao.getAll()
    fun tripFlow(id: Long): Flow<Trip?> = tripDao.getById(id)
    fun stopsFlow(tripId: Long): Flow<List<StopEvent>> = stopDao.getByTrip(tripId)
    fun delaysFlow(tripId: Long): Flow<List<DelayEvent>> = delayDao.getByTrip(tripId)

    fun trackLastPointFlow(tripId: Long) = trackDao.getLatestFlow(tripId)
    fun trackCountFlow(tripId: Long) = trackDao.countFlow(tripId)

    suspend fun getTrackPointsBetweenOnce(tripId: Long, fromMs: Long, toMs: Long) = trackDao.getBetweenOnce(tripId, fromMs, toMs)

    fun trackPointsFlow(tripId: Long): Flow<List<TrackPoint>> = trackDao.getByTrip(tripId)
    suspend fun trackPointsOnce(tripId: Long): List<TrackPoint> = trackDao.getByTripOnce(tripId)
    suspend fun insertTrackPoint(p: TrackPoint): Long = trackDao.insert(p)

    private fun cleanText(value: String?): String? = value?.trim()?.uppercase()?.ifBlank { null }
    private fun normalizeSex(value: String?): String? = value?.trim()?.uppercase()?.take(1)

    suspend fun updateTripHeader(
        tripId: Long,
        routeName: String,
        company: String?,
        vehicleEco: String?,
        direction: String,
        routeNumber: Int?,
        esFs: String?,
        baseStart: String?,
        baseEnd: String?,
        plateNumber: String?,
        vehicleType: String?,
        seatCapacity: Int?,
        notes: String?,
        aforador: String? = null,
        supervisor: String? = null,
        deviceNumber: String? = null,
        observerSex: String? = null
    ): Boolean {
        val current = tripDao.getByIdOnce(tripId) ?: return false
        val updated = current.copy(
            routeName = routeName.trim().uppercase().ifBlank { current.routeName },
            company = cleanText(company),
            vehicleEco = cleanText(vehicleEco),
            direction = direction.trim().uppercase().ifBlank { current.direction },
            routeNumber = routeNumber,
            esFs = cleanText(esFs),
            baseStart = cleanText(baseStart),
            baseEnd = cleanText(baseEnd),
            plateNumber = cleanText(plateNumber),
            vehicleType = vehicleType,
            seatCapacity = seatCapacity,
            notes = cleanText(notes),
            aforador = cleanText(aforador),
            supervisor = cleanText(supervisor),
            deviceNumber = deviceNumber,
            observerSex = normalizeSex(observerSex)
        )
        val ok = tripDao.update(updated) > 0
        if (ok) enqueueSync("TRIP", "UPDATE", tripId, updated)
        return ok
    }

    suspend fun completePendingGpsEvents(tripId: Long, point: TrackPoint): Int {
        if (!point.isBackfillEligible) return 0
        if (point.lat == 0.0 || point.lon == 0.0) return 0
        if (point.accM <= 0.0 || point.accM > 60.0) return 0
        val pending = stopDao.getPendingGpsEvents(tripId, limit = 50)
        var updated = 0
        pending.forEach { event ->
            val diffMs = kotlin.math.abs(point.timeMs - event.timestamp)
            if (diffMs <= 5 * 60_000L) {
                val updatedEvent = event.copy(
                    stopLat = point.lat,
                    stopLon = point.lon,
                    stopAltM = point.altM,
                    stopAccM = point.accM,
                    stopProvider = point.provider,
                    stopFixTime = point.timeMs,
                    locationStatus = "GPS_BACKFILLED"
                )
                val ok = stopDao.update(updatedEvent) > 0
                if (ok) {
                    updated++
                    enqueueSync("EVENT", "UPDATE", event.eventId, updatedEvent)
                }
            }
        }
        return updated
    }

    suspend fun createTripWithStartFix(
        planningRouteId: String,
        stopLat: Double,
        stopLon: Double,
        stopAltM: Double = 0.0,
        stopAccM: Double,
        stopProvider: String,
        stopFixTime: Long,
        locationStatus: String,
        routeName: String,
        company: String?,
        vehicleEco: String?,
        direction: String,
        notes: String?,
        routeNumber: Int? = null,
        esFs: String? = null,
        baseStart: String? = null,
        baseEnd: String? = null,
        plateNumber: String? = null,
        vehicleType: String? = null,
        seatCapacity: Int? = null,
        aforador: String? = null,
        supervisor: String? = null,
        deviceNumber: String? = null,
        observerSex: String? = null,
        continueWaypoints: Boolean = false,
        initialMenPassengers: Int = 0,
        initialWomenPassengers: Int = 0
    ): Long {
        val start = System.currentTimeMillis()
        val initialWp = if (continueWaypoints) (tripDao.getLastTripNextWaypoint() ?: 1) else 1

        val trip = Trip(
            planningRouteId = planningRouteId.trim().uppercase(),
            routeName = routeName.trim().uppercase(),
            company = cleanText(company),
            vehicleEco = cleanText(vehicleEco),
            direction = direction.trim().uppercase(),
            startTime = start,
            notes = cleanText(notes),
            nextWaypointId = initialWp,
            routeNumber = routeNumber,
            esFs = cleanText(esFs),
            baseStart = cleanText(baseStart),
            baseEnd = cleanText(baseEnd),
            plateNumber = cleanText(plateNumber),
            vehicleType = vehicleType,
            seatCapacity = seatCapacity,
            aforador = cleanText(aforador),
            supervisor = cleanText(supervisor),
            deviceNumber = deviceNumber,
            observerSex = normalizeSex(observerSex)
        )
        val tripId = tripDao.insert(trip)
        val finalTrip = trip.copy(tripId = tripId)

        try {
            val context = AsdGraph.appContext
            val cloudTrip = AsdCloudMapper.toCloudDto(context, finalTrip)
            enqueueSync("TRIP", "UPSERT", tripId, cloudTrip)
            UrbanRuntime.publishEvent(UrbanEventFactory.asd(UrbanEventTypes.ASD_TRIP_ENQUEUED_FOR_SYNC, mapOf("tripId" to tripId)))
        } catch (e: Exception) {
            Log.w("AsdRepository", "Failed to enqueue trip for sync", e)
        }

        val normSex = normalizeSex(observerSex)
        val observerMen = if (normSex == "H") 1 else 0
        val observerWomen = if (normSex == "M") 1 else 0
        val mUp = initialMenPassengers.coerceAtLeast(0) + observerMen
        val wUp = initialWomenPassengers.coerceAtLeast(0) + observerWomen

        addStopDetailed(
            tripId = tripId,
            stopType = "BANDERA",
            stopTimeMs = start,
            startTimeMs = start,
            stopName = "AD/INICIO",
            notes = "PASAJEROS INICIALES + OBSERVADOR",
            menUp = mUp, womenUp = wUp, menDown = 0, womenDown = 0,
            hasLuggage = false,
            delayCodes = "AD/INICIO",
            otherDelayDesc = null,
            eventTimestampMs = start,
            stopLat = stopLat,
            stopLon = stopLon,
            stopAltM = stopAltM,
            stopAccM = stopAccM,
            stopProvider = stopProvider,
            stopFixTime = stopFixTime,
            locationStatus = locationStatus,
            startLat = stopLat,
            startLon = stopLon,
            startAltM = stopAltM,
            startAccM = stopAccM,
            startProvider = stopProvider,
            startFixTime = stopFixTime
        )
        return tripId
    }

    suspend fun endTripWithFix(tripId: Long, stopLat: Double, stopLon: Double, stopAltM: Double = 0.0, stopAccM: Double, stopProvider: String, stopFixTime: Long, locationStatus: String): Boolean {
        val trip = tripDao.getByIdOnce(tripId) ?: return false
        val activeDelay = delayDao.getActiveDelay(tripId)
        if (activeDelay != null) return false
        val activeBandera = stopDao.getActiveBandera(tripId)
        if (activeBandera != null) return false

        val endMs = System.currentTimeMillis()
        val (mOnBoard, wOnBoard) = computeDetailedOnBoard(tripId)

        addStopDetailed(
            tripId = tripId,
            stopType = "BANDERA",
            stopTimeMs = endMs,
            startTimeMs = endMs,
            stopName = "AD/FINAL",
            notes = "FINALIZANDO CON PASAJEROS RESTANTES",
            menUp = 0, womenUp = 0, menDown = mOnBoard, womenDown = wOnBoard,
            hasLuggage = false,
            delayCodes = "AD/FINAL",
            otherDelayDesc = null,
            eventTimestampMs = endMs,
            stopLat = stopLat,
            stopLon = stopLon,
            stopAltM = stopAltM,
            stopAccM = stopAccM,
            stopProvider = stopProvider,
            stopFixTime = stopFixTime,
            locationStatus = locationStatus,
            startLat = stopLat,
            startLon = stopLon,
            startAltM = stopAltM,
            startAccM = stopAccM,
            startProvider = stopProvider,
            startFixTime = stopFixTime
        )
        val finalTrip = trip.copy(endTime = endMs)
        val ok = tripDao.update(finalTrip) > 0

        if (ok) {
            try {
                val context = AsdGraph.appContext
                val cloudTrip = AsdCloudMapper.toCloudDto(context, finalTrip)
                enqueueSync("TRIP", "UPSERT", tripId, cloudTrip)
                enqueueTrackChunks(tripId)
                UrbanRuntime.publishEvent(UrbanEventFactory.asd(UrbanEventTypes.ASD_TRIP_SYNC_READY, mapOf("tripId" to tripId)))
            } catch (e: Exception) {
                android.util.Log.w("AsdRepository", "Failed to enqueue trip closure for sync", e)
            }
        }

        return ok
    }

    private suspend fun computeDetailedOnBoard(tripId: Long): Pair<Int, Int> {
        val stops = stopDao.getByTripOnce(tripId)
        var mOnboard = 0
        var wOnboard = 0
        for (s in stops) {
            mOnboard += (s.paxMenUp - s.paxMenDown)
            wOnboard += (s.paxWomenUp - s.paxWomenDown)
        }
        return max(0, mOnboard) to max(0, wOnboard)
    }

    private suspend fun computeOnBoard(tripId: Long): Int {
        val stops = stopDao.getByTripOnce(tripId)
        var onboard = 0
        for (s in stops) {
            val up = s.paxMenUp + s.paxWomenUp
            val down = s.paxMenDown + s.paxWomenDown
            onboard += (up - down)
            if (onboard < 0) onboard = 0
        }
        return onboard
    }

    private fun normalizeDelayCodes(delayCodes: String?, up: Int, down: Int): String? {
        val codes = delayCodes?.split("/", ",", "|", ";")?.map { it.trim().uppercase() }?.filter { it.isNotBlank() }?.toMutableList() ?: mutableListOf()
        if ((up > 0 || down > 0) && !codes.contains("AD")) codes.add(0, "AD")
        return codes.distinct().joinToString("/").ifBlank { null }
    }

    private fun isTripBoundaryFlag(stopType: String, stopName: String?, delayCodes: String?): Boolean {
        if (!stopType.equals("BANDERA", ignoreCase = true)) return false
        val n = stopName?.uppercase().orEmpty()
        val c = delayCodes?.uppercase().orEmpty()
        return n.contains("AD/INICIO") || n.contains("AD/FINAL") || c.contains("AD/INICIO") || c.contains("AD/FINAL")
    }

    suspend fun addStopDetailed(
        tripId: Long,
        stopType: String,
        stopTimeMs: Long,
        startTimeMs: Long,
        stopName: String?,
        notes: String?,
        menUp: Int,
        womenUp: Int,
        menDown: Int,
        womenDown: Int,
        hasLuggage: Boolean,
        delayCodes: String?,
        otherDelayDesc: String?,
        eventTimestampMs: Long,
        stopLat: Double,
        stopLon: Double,
        stopAltM: Double = 0.0,
        stopAccM: Double,
        stopProvider: String,
        stopFixTime: Long,
        locationStatus: String,
        startLat: Double = 0.0,
        startLon: Double = 0.0,
        startAltM: Double = 0.0,
        startAccM: Double = 0.0,
        startProvider: String = "",
        startFixTime: Long = 0L
    ) {
        val pair = tripDao.reserveWaypointPair(tripId)

        val event = StopEvent(
            tripId = tripId,
            timestamp = eventTimestampMs,
            stopType = stopType.trim().uppercase(),
            count = menUp + womenUp,
            stopName = cleanText(stopName),
            notes = cleanText(notes),
            waypointStopId = pair.outId,
            waypointStartId = pair.inId,
            stopTime = stopTimeMs,
            stopLat = stopLat,
            stopLon = stopLon,
            stopAltM = stopAltM,
            stopAccM = stopAccM,
            stopProvider = stopProvider,
            stopFixTime = stopFixTime,
            startTime = startTimeMs,
            startLat = startLat,
            startLon = startLon,
            startAltM = startAltM,
            startAccM = startAccM,
            startProvider = startProvider,
            startFixTime = startFixTime,
            locationStatus = locationStatus,
            delayCodes = normalizeDelayCodes(delayCodes, menUp + womenUp, menDown + womenDown),
            otherDelayDesc = cleanText(otherDelayDesc),
            hasLuggage = hasLuggage,
            paxMenUp = menUp,
            paxWomenUp = womenUp,
            paxMenDown = menDown,
            paxWomenDown = womenDown
        )
        val insertedId = stopDao.insert(event)
        val finalEvent = event.copy(eventId = insertedId)

        try {
            val context = AsdGraph.appContext
            val identity = UrbanRuntime.identity(context)
            val workspace = UrbanRuntime.workspace(context)
            val cloudTripId = "${identity.installationId}_$tripId"
            val (mOnBoard, wOnBoard) = computeDetailedOnBoard(tripId)

            val cloudEvent = AsdCloudMapper.toCloudDto(context, finalEvent, cloudTripId, mOnBoard, wOnBoard)
            enqueueSync(
                type = "EVENT",
                operation = "UPSERT",
                localId = insertedId,
                payload = cloudEvent,
                cloudPath = UrbanCloudPaths.tripEventPath(workspace, cloudTripId, cloudEvent.cloudEventId)
            )
            UrbanRuntime.publishEvent(UrbanEventFactory.asd(UrbanEventTypes.ASD_EVENT_ENQUEUED_FOR_SYNC, mapOf("tripId" to tripId, "eventId" to insertedId)))
        } catch (e: Exception) {
            android.util.Log.w("AsdRepository", "Failed to enqueue event for sync", e)
        }

        AsdOnlineBackup.backupStopEvent(finalEvent)
        LiveEventBus.tryEmit(
            LiveSignal(
                tripId = tripId,
                reason = LiveSignal.EVENT,
                eventType = finalEvent.stopType,
                eventId = finalEvent.eventId,
                waypointId = finalEvent.waypointStopId,
                timestampMs = finalEvent.timestamp
            )
        )
    }

    private fun normalizeCoord(lat: Double, lon: Double): Pair<Double, Double> {
        val latOk = lat.isFinite() && lat in -90.0..90.0
        val lonOk = lon.isFinite() && lon in -180.0..180.0
        return if (latOk && lonOk) lat to lon else 0.0 to 0.0
    }

    suspend fun startDelay(tripId: Long, delayType: String, notes: String?, startLat: Double, startLon: Double, startAltM: Double = 0.0, startAccM: Double = 0.0, startProvider: String = "", startFixTime: Long = 0L, locationStatus: String = "NO_FIX"): Boolean {
        val active = stopDao.getActiveBandera(tripId)
        if (active != null) return false
        val (lat, lon) = normalizeCoord(startLat, startLon)
        val pair = tripDao.reserveWaypointPair(tripId)
        val now = System.currentTimeMillis()
        val event = StopEvent(
            tripId = tripId,
            timestamp = now,
            stopType = "BANDERA",
            count = 0,
            stopName = null,
            notes = cleanText(notes),
            waypointStopId = pair.outId,
            waypointStartId = pair.inId,
            stopTime = now,
            stopLat = lat,
            stopLon = lon,
            stopAltM = startAltM,
            stopAccM = startAccM,
            stopProvider = startProvider,
            stopFixTime = if (startFixTime > 0L) startFixTime else now,
            startTime = 0L,
            startLat = 0.0,
            startLon = 0.0,
            startAltM = 0.0,
            startAccM = 0.0,
            startProvider = "",
            startFixTime = 0L,
            locationStatus = locationStatus,
            delayCodes = cleanText(delayType),
            otherDelayDesc = null,
            hasLuggage = false,
            paxMenUp = 0, paxWomenUp = 0, paxMenDown = 0, paxWomenDown = 0
        )
        val insertedId = stopDao.insert(event)
        val finalEvent = event.copy(eventId = insertedId)

        try {
            val context = AsdGraph.appContext
            val identity = UrbanRuntime.identity(context)
            val workspace = UrbanRuntime.workspace(context)
            val cloudTripId = "${identity.installationId}_$tripId"
            val (mOnBoard, wOnBoard) = computeDetailedOnBoard(tripId)

            val cloudEvent = AsdCloudMapper.toCloudDto(context, finalEvent, cloudTripId, mOnBoard, wOnBoard)
            enqueueSync(
                type = "EVENT",
                operation = "UPSERT",
                localId = insertedId,
                payload = cloudEvent,
                cloudPath = UrbanCloudPaths.tripEventPath(workspace, cloudTripId, cloudEvent.cloudEventId)
            )
        } catch (e: Exception) {
            Log.w("AsdRepository", "Failed to enqueue delay start for sync", e)
        }

        AsdOnlineBackup.backupStopEvent(finalEvent)
        LiveEventBus.tryEmit(
            LiveSignal(
                tripId = tripId,
                reason = LiveSignal.EVENT,
                eventType = "DELAY_START",
                eventId = finalEvent.eventId,
                waypointId = finalEvent.waypointStopId,
                timestampMs = finalEvent.timestamp
            )
        )
        return true
    }

    suspend fun stopDelay(tripId: Long, endLat: Double, endLon: Double, endAltM: Double = 0.0, endAccM: Double = 0.0, endProvider: String = "", endFixTime: Long = 0L, locationStatus: String = "NO_FIX"): Boolean {
        val active = stopDao.getActiveBandera(tripId) ?: return false
        val (lat, lon) = normalizeCoord(endLat, endLon)
        val now = System.currentTimeMillis()
        val updated = active.copy(
            startTime = now,
            startLat = lat,
            startLon = lon,
            startAltM = endAltM,
            startAccM = endAccM,
            startProvider = endProvider,
            startFixTime = if (endFixTime > 0L) endFixTime else now,
            locationStatus = locationStatus
        )
        val ok = stopDao.update(updated) > 0
        if (ok) {
            try {
                val context = AsdGraph.appContext
                val identity = UrbanRuntime.identity(context)
                val workspace = UrbanRuntime.workspace(context)
                val cloudTripId = "${identity.installationId}_$tripId"
                val (mOnBoard, wOnBoard) = computeDetailedOnBoard(tripId)

                val cloudEvent = AsdCloudMapper.toCloudDto(context, updated, cloudTripId, mOnBoard, wOnBoard)
                enqueueSync(
                    type = "EVENT",
                    operation = "UPSERT",
                    localId = updated.eventId,
                    payload = cloudEvent,
                    cloudPath = UrbanCloudPaths.tripEventPath(workspace, cloudTripId, cloudEvent.cloudEventId)
                )
            } catch (e: Exception) {
                Log.w("AsdRepository", "Failed to enqueue delay end for sync", e)
            }
            LiveEventBus.tryEmit(
                LiveSignal(
                    tripId = tripId,
                    reason = LiveSignal.EVENT,
                    eventType = "DELAY_END",
                    eventId = updated.eventId,
                    waypointId = updated.waypointStartId,
                    timestampMs = now
                )
            )
        }
        AsdOnlineBackup.backupStopEvent(updated)
        return ok
    }

    val ccSessionsFlow: Flow<List<CcSession>> = ccSessionDao.getAll()
    fun ccSessionFlow(id: Long): Flow<CcSession?> = ccSessionDao.getById(id)
    fun ccEventsFlow(sessionId: Long): Flow<List<CcEvent>> = ccEventDao.getBySession(sessionId)
    suspend fun getLatestCcSession(): CcSession? = ccSessionDao.getLatestOnce()
    suspend fun createCcSession(s: CcSession): Long = ccSessionDao.insert(s)
    suspend fun nextCcSeq(sessionId: Long): Int = (ccEventDao.getMaxSeq(sessionId) ?: 0) + 1
    suspend fun addCcEvent(e: CcEvent): Long = ccEventDao.insert(e)
    suspend fun getTripOnce(id: Long) = tripDao.getByIdOnce(id)
    suspend fun getStopsOnce(tripId: Long) = stopDao.getByTripOnce(tripId)
    suspend fun getDelaysOnce(tripId: Long) = delayDao.getByTripOnce(tripId)
    suspend fun getTrackPointsOnce(tripId: Long) = trackDao.getByTripOnce(tripId)
    suspend fun getActiveTripOnce() = tripDao.getActiveTripOnce()
    suspend fun endCcSession(sessionId: Long) { ccSessionDao.endSession(sessionId, System.currentTimeMillis()) }

    fun activeAsdRoutesFlow() = asdRouteCatalogDao.getActiveRoutes()
    suspend fun getAsdRouteByCatalogIdAndDirection(catalogId: String, direction: String) = asdRouteCatalogDao.getByCatalogIdAndDirection(catalogId.trim().uppercase(), direction.trim().uppercase())
    fun activeAsdPeopleFlow() = asdFieldPersonCatalogDao.getActivePeople()
    fun activeAsdPeopleByRoleFlow(role: String) = asdFieldPersonCatalogDao.getActiveByRole(role.trim().uppercase())

    fun activeAsdVehicleTypesFlow() = asdVehicleTypeCatalogDao.getActiveVehicleTypesFlow()

    suspend fun getLastTripNextWaypoint() = tripDao.getLastTripNextWaypoint()

    suspend fun replaceAsdRouteCatalog(items: List<AsdRouteCatalogItem>) {
        asdRouteCatalogDao.clear()
        asdRouteCatalogDao.upsertAll(items)
    }

    suspend fun replaceAsdPeopleCatalog(items: List<AsdFieldPersonCatalogItem>) {
        asdFieldPersonCatalogDao.clear()
        asdFieldPersonCatalogDao.upsertAll(items)
    }

    suspend fun replaceAsdVehicleTypeCatalog(items: List<AsdVehicleTypeCatalogItem>) {
        asdVehicleTypeCatalogDao.replaceAll(items)
    }

    fun catalogSyncStateFlow() = asdCatalogSyncStateDao.getStateFlow()
    suspend fun getCatalogSyncStateOnce() = asdCatalogSyncStateDao.getStateOnce()
    suspend fun updateCatalogSyncState(state: AsdCatalogSyncState) = asdCatalogSyncStateDao.upsert(state)

    internal suspend fun enqueueSync(
        type: String,
        operation: String,
        localId: Long,
        payload: Any,
        cloudPath: String? = null,
        priority: Int = 1
    ) {
        try {
            val context = AsdGraph.appContext
            val workspace = UrbanRuntime.workspace(context)
            val identity = UrbanRuntime.identity(context)

            val effectivePath = cloudPath ?: when(type) {
                "TRIP" -> UrbanCloudPaths.tripPath(workspace, "${identity.installationId}_$localId")
                "EVENT" -> UrbanCloudPaths.tripEventPath(workspace, "${identity.installationId}_0", "${identity.installationId}_$localId")
                else -> null
            }

            syncQueueDao.insert(
                AsdSyncQueueItem(
                    entityType = type,
                    operation = operation,
                    entityLocalId = localId,
                    payloadJson = gson.toJson(payload),
                    cloudPath = effectivePath,
                    priority = priority,
                    status = "PENDING"
                )
            )
            com.oropeza.urbanapp.core.platform.sync.UrbanCloudSyncScheduler.syncNow(context)
        } catch (e: Exception) {
            android.util.Log.e("AsdRepository", "Sync enqueue failed for $type", e)
        }
    }

    fun syncQueuePendingCountFlow() = syncQueueDao.pendingCountFlow()
    fun syncQueueFailedCountFlow() = syncQueueDao.failedCountFlow()
    fun lastSyncTimeFlow() = syncQueueDao.lastSyncTimeFlow()
    suspend fun getPendingSyncItems(limit: Int) = syncQueueDao.getPending(limit)
    suspend fun markSyncItemSynced(id: Long) = syncQueueDao.markSynced(id)
    suspend fun updateSyncItem(item: AsdSyncQueueItem) = syncQueueDao.update(item)
    suspend fun getLastFailedSyncItem() = syncQueueDao.getLastFailedItem()

    suspend fun triggerManualNoopSync(): Int {
        val engine = com.oropeza.urbanapp.asd.sync.cloud.CloudSyncEngine(
            this,
            com.oropeza.urbanapp.asd.sync.cloud.NoopCloudSyncTarget()
        )
        return engine.processNextBatch()
    }

    suspend fun triggerManualCloudSync(): Int {
        val engine = com.oropeza.urbanapp.asd.sync.cloud.CloudSyncEngine(
            this,
            AsdGraph.getCloudSyncTarget()
        )
        return engine.processNextBatch()
    }

    fun getTripSyncStatusFlow(tripId: Long): Flow<AsdTripSyncStatus> {
        return syncQueueDao.getTripSyncItemStatusesFlow(tripId).map { statuses ->
            if (statuses.isEmpty()) return@map AsdTripSyncStatus.NOT_QUEUED

            val distinct = statuses.distinct()

            return@map when {
                distinct.all { it == "SYNCED" } -> AsdTripSyncStatus.SYNCED
                distinct.any { it == "IN_PROGRESS" } -> AsdTripSyncStatus.IN_PROGRESS
                distinct.any { it == "FAILED" } -> {
                    if (distinct.any { it == "SYNCED" }) AsdTripSyncStatus.PARTIAL else AsdTripSyncStatus.FAILED
                }
                distinct.any { it == "SYNCED" } && distinct.any { it == "PENDING" } -> AsdTripSyncStatus.PARTIAL
                else -> AsdTripSyncStatus.PENDING
            }
        }
    }

    private suspend fun enqueueTrackChunks(tripId: Long) {
        val context = AsdGraph.appContext
        val workspace = UrbanRuntime.workspace(context)
        val identity = UrbanRuntime.identity(context)
        val cloudTripId = "${identity.installationId}_$tripId"

        val points = trackDao.getByTripOnce(tripId)

        UrbanRuntime.publishEvent(
            UrbanEventFactory.asd(
                "ASD_TRACK_CHUNK_DEBUG",
                mapOf(
                    "tripId" to tripId,
                    "cloudTripId" to cloudTripId,
                    "pointCount" to points.size,
                    "cleanPointCount" to points.count { TrackPointQuality.isCleanRoutePoint(it) }
                )
            )
        )

        if (points.isEmpty()) {
            UrbanRuntime.publishEvent(
                UrbanEventFactory.asd(
                    "ASD_TRACK_CHUNK_SKIPPED_EMPTY",
                    mapOf(
                        "tripId" to tripId,
                        "cloudTripId" to cloudTripId,
                        "reason" to "NO_TRACK_POINTS_IN_ROOM"
                    )
                )
            )
            return
        }

        val chunkSize = UrbanRuntime.configuration(context).trackChunkSize.coerceAtLeast(10)
        val chunks = points.sortedBy { it.timeMs }.chunked(chunkSize)

        chunks.forEachIndexed { index, chunk ->
            val chunkId = "${cloudTripId}_chunk_$index"
            val dto = AsdTrackChunkCloudDto(
                chunkId = chunkId,
                cloudTripId = cloudTripId,
                localTripId = tripId,
                chunkIndex = index,
                pointCount = chunk.size,
                cleanPointCount = chunk.count { TrackPointQuality.isCleanRoutePoint(it) },
                stalePointCount = chunk.count { it.sampleStatus == "STALE" },
                noFixPointCount = chunk.count { it.sampleStatus == "NO_FIX" },
                syntheticPointCount = chunk.count { it.isSynthetic },
                points = chunk.map { AsdCloudMapper.toTrackPointDto(it) },
                startTime = chunk.first().timeMs,
                endTime = chunk.last().timeMs,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            enqueueSync(
                type = "TRACK_CHUNK",
                operation = "UPSERT",
                localId = tripId,
                payload = dto,
                cloudPath = UrbanCloudPaths.trackChunkPath(workspace, cloudTripId, chunkId)
            )
            UrbanRuntime.publishEvent(
                UrbanEventFactory.asd(
                    UrbanEventTypes.ASD_TRACK_CHUNK_ENQUEUED,
                    mapOf(
                        "tripId" to tripId,
                        "index" to index,
                        "pointCount" to chunk.size,
                        "cleanPointCount" to chunk.count { TrackPointQuality.isCleanRoutePoint(it) },
                        "cloudPath" to UrbanCloudPaths.trackChunkPath(workspace, cloudTripId, chunkId)
                    )
                )
            )
        }
    }
}



