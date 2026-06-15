package com.oropeza.urbanapp.asd.data.repository

import com.oropeza.urbanapp.asd.backup.AsdOnlineBackup
import com.oropeza.urbanapp.asd.data.local.*
import kotlinx.coroutines.flow.Flow
import kotlin.math.max

class AsdRepository(private val db: AppDatabase) {

    private val tripDao = db.tripDao()
    private val stopDao = db.stopDao()
    private val delayDao = db.delayDao()
    private val trackDao = db.trackDao()

    private val ccSessionDao = db.ccSessionDao()
    private val ccEventDao = db.ccEventDao()

    private val asdRouteCatalogDao = db.asdRouteCatalogDao()
    private val asdFieldPersonCatalogDao = db.asdFieldPersonCatalogDao()

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
            vehicleType = cleanText(vehicleType),
            seatCapacity = seatCapacity,
            notes = cleanText(notes),
            aforador = cleanText(aforador),
            supervisor = cleanText(supervisor),
            deviceNumber = cleanText(deviceNumber),
            observerSex = normalizeSex(observerSex)
        )
        return tripDao.update(updated) > 0
    }

    suspend fun completePendingGpsEvents(tripId: Long, point: TrackPoint): Int {
        if (point.lat == 0.0 || point.lon == 0.0) return 0
        if (point.accM <= 0.0 || point.accM > 60.0) return 0
        val pending = stopDao.getPendingGpsEvents(tripId, limit = 10)
        var updated = 0
        pending.forEach { event ->
            updated += stopDao.updateEventGpsFix(
                eventId = event.eventId,
                lat = point.lat,
                lon = point.lon,
                accM = point.accM,
                provider = point.provider,
                fixTime = point.timeMs,
                status = "GPS_BACKFILLED"
            )
        }
        return updated
    }

    suspend fun createTripWithStartFix(
        planningRouteId: String,
        stopLat: Double,
        stopLon: Double,
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
        observerSex: String? = null
    ): Long {
        val start = System.currentTimeMillis()
        val tripId = tripDao.insert(
            Trip(
                planningRouteId = planningRouteId.trim().uppercase(),
                routeName = routeName.trim().uppercase(),
                company = cleanText(company),
                vehicleEco = cleanText(vehicleEco),
                direction = direction.trim().uppercase(),
                startTime = start,
                notes = cleanText(notes),
                nextWaypointId = 1,
                routeNumber = routeNumber,
                esFs = cleanText(esFs),
                baseStart = cleanText(baseStart),
                baseEnd = cleanText(baseEnd),
                plateNumber = cleanText(plateNumber),
                vehicleType = cleanText(vehicleType),
                seatCapacity = seatCapacity,
                aforador = cleanText(aforador),
                supervisor = cleanText(supervisor),
                deviceNumber = cleanText(deviceNumber),
                observerSex = normalizeSex(observerSex)
            )
        )

        val normSex = normalizeSex(observerSex)
        val mUp = if (normSex == "H") 1 else 0
        val wUp = if (normSex == "M") 1 else 0

        addStopDetailed(
            tripId = tripId,
            stopType = "BANDERA",
            stopTimeMs = start,
            startTimeMs = start,
            stopName = "AD/INICIO",
            notes = "OBSERVADOR A BORDO",
            menUp = mUp, womenUp = wUp, menDown = 0, womenDown = 0,
            hasLuggage = false,
            delayCodes = "AD/INICIO",
            otherDelayDesc = null,
            eventTimestampMs = start,
            stopLat = stopLat,
            stopLon = stopLon,
            stopAccM = stopAccM,
            stopProvider = stopProvider,
            stopFixTime = stopFixTime,
            locationStatus = locationStatus,
            startLat = stopLat,
            startLon = stopLon,
            startAccM = stopAccM,
            startProvider = stopProvider,
            startFixTime = stopFixTime
        )
        return tripId
    }

    suspend fun endTripWithFix(tripId: Long, stopLat: Double, stopLon: Double, stopAccM: Double, stopProvider: String, stopFixTime: Long, locationStatus: String): Boolean {
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
            stopAccM = stopAccM,
            stopProvider = stopProvider,
            stopFixTime = stopFixTime,
            locationStatus = locationStatus,
            startLat = stopLat,
            startLon = stopLon,
            startAccM = stopAccM,
            startProvider = stopProvider,
            startFixTime = stopFixTime
        )
        tripDao.update(trip.copy(endTime = endMs))
        return true
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
        val combined = listOfNotNull(stopName, delayCodes).joinToString("/").uppercase()
        return combined.contains("AD/INICIO") || combined.contains("AD/FINAL")
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
        eventTimestampMs: Long? = null,
        stopLat: Double = 0.0,
        stopLon: Double = 0.0,
        stopAccM: Double = 0.0,
        stopProvider: String = "",
        stopFixTime: Long = 0L,
        locationStatus: String = "NO_FIX",
        startLat: Double = 0.0,
        startLon: Double = 0.0,
        startAccM: Double = 0.0,
        startProvider: String = "",
        startFixTime: Long = 0L
    ) {
        val now = eventTimestampMs ?: System.currentTimeMillis()
        val cleanMenUp = max(0, menUp)
        val cleanWomenUp = max(0, womenUp)
        val cleanMenDown = max(0, menDown)
        val cleanWomenDown = max(0, womenDown)
        val up = cleanMenUp + cleanWomenUp
        val down = cleanMenDown + cleanWomenDown
        val normalizedDelayCodes = normalizeDelayCodes(delayCodes, up, down)

        val isAdFinal = normalizedDelayCodes?.contains("AD/FINAL") == true

        if (!isAdFinal) {
            val trip = tripDao.getByIdOnce(tripId)
            val normSex = normalizeSex(trip?.observerSex)
            val (mOnBoard, wOnBoard) = computeDetailedOnBoard(tripId)

            if (normSex == "H") {
                if (cleanMenDown > (mOnBoard + cleanMenUp - 1).coerceAtLeast(0)) {
                    throw IllegalStateException("No puedes bajar al observador durante el recorrido.")
                }
            } else if (normSex == "M") {
                if (cleanWomenDown > (wOnBoard + cleanWomenUp - 1).coerceAtLeast(0)) {
                    throw IllegalStateException("No puedes bajar a la observadora durante el recorrido.")
                }
            }
        }

        if (stopType.equals("DESCENSO", ignoreCase = true)) {
            val onboard = computeOnBoard(tripId)
            if (down > onboard) throw IllegalStateException("No puedes bajar $down si solo van $onboard a bordo.")
        }
        val pair = if (isTripBoundaryFlag(stopType, stopName, normalizedDelayCodes)) {
            tripDao.reserveSingleWaypoint(tripId)
        } else {
            tripDao.reserveWaypointPair(tripId)
        }
        val count = when (stopType.uppercase()) {
            "ASCENSO" -> up
            "DESCENSO" -> down
            "ASD" -> up + down
            else -> 0
        }
        val event = StopEvent(
            tripId = tripId,
            timestamp = now,
            stopType = stopType.uppercase(),
            count = count.coerceAtLeast(0),
            stopName = cleanText(stopName),
            notes = cleanText(notes),
            waypointStopId = pair.inId,
            waypointStartId = pair.outId,
            stopTime = stopTimeMs,
            startTime = startTimeMs,
            stopLat = stopLat,
            stopLon = stopLon,
            startLat = startLat,
            startLon = startLon,
            stopAccM = stopAccM,
            stopProvider = stopProvider,
            stopFixTime = stopFixTime,
            startAccM = startAccM,
            startProvider = startProvider,
            startFixTime = startFixTime,
            locationStatus = locationStatus,
            paxMenUp = cleanMenUp,
            paxWomenUp = cleanWomenUp,
            paxMenDown = cleanMenDown,
            paxWomenDown = cleanWomenDown,
            hasLuggage = hasLuggage,
            delayCodes = normalizedDelayCodes,
            otherDelayDesc = cleanText(otherDelayDesc)
        )
        val insertedId = stopDao.insert(event)
        AsdOnlineBackup.backupStopEvent(event.copy(eventId = insertedId))
    }

    private fun normalizeCoord(lat: Double, lon: Double): Pair<Double, Double> {
        val latOk = lat.isFinite() && lat in -90.0..90.0
        val lonOk = lon.isFinite() && lon in -180.0..180.0
        return if (latOk && lonOk) lat to lon else 0.0 to 0.0
    }

    suspend fun startDelay(tripId: Long, delayType: String, notes: String?, startLat: Double, startLon: Double, startAccM: Double = 0.0, startProvider: String = "", startFixTime: Long = 0L, locationStatus: String = "NO_FIX"): Boolean {
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
            waypointStopId = pair.inId,
            waypointStartId = pair.outId,
            stopTime = now,
            stopLat = lat,
            stopLon = lon,
            stopAccM = startAccM,
            stopProvider = startProvider,
            stopFixTime = if (startFixTime > 0L) startFixTime else now,
            startTime = 0L,
            startLat = 0.0,
            startLon = 0.0,
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
        AsdOnlineBackup.backupStopEvent(event.copy(eventId = insertedId))
        return true
    }

    suspend fun stopDelay(tripId: Long, endLat: Double, endLon: Double, endAccM: Double = 0.0, endProvider: String = "", endFixTime: Long = 0L, locationStatus: String = "NO_FIX"): Boolean {
        val active = stopDao.getActiveBandera(tripId) ?: return false
        val (lat, lon) = normalizeCoord(endLat, endLon)
        val now = System.currentTimeMillis()
        val updated = active.copy(
            startTime = now,
            startLat = lat,
            startLon = lon,
            startAccM = endAccM,
            startProvider = endProvider,
            startFixTime = if (endFixTime > 0L) endFixTime else now,
            locationStatus = locationStatus
        )
        stopDao.update(updated)
        AsdOnlineBackup.backupStopEvent(updated)
        return true
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
    suspend fun endCcSession(sessionId: Long) { ccSessionDao.endSession(sessionId, System.currentTimeMillis()) }

    fun activeAsdRoutesFlow() = asdRouteCatalogDao.getActiveRoutes()
    suspend fun getAsdRouteByCatalogIdAndDirection(catalogId: String, direction: String) = asdRouteCatalogDao.getByCatalogIdAndDirection(catalogId.trim().uppercase(), direction.trim().uppercase())
    fun activeAsdPeopleFlow() = asdFieldPersonCatalogDao.getActivePeople()
    fun activeAsdPeopleByRoleFlow(role: String) = asdFieldPersonCatalogDao.getActiveByRole(role.trim().uppercase())

    suspend fun replaceAsdRouteCatalog(items: List<AsdRouteCatalogItem>) {
        asdRouteCatalogDao.clear()
        asdRouteCatalogDao.upsertAll(items)
    }

    suspend fun replaceAsdPeopleCatalog(items: List<AsdFieldPersonCatalogItem>) {
        asdFieldPersonCatalogDao.clear()
        asdFieldPersonCatalogDao.upsertAll(items)
    }
}
