package com.oropeza.urbanapp.asd.domain.trip

import com.oropeza.urbanapp.asd.AsdGraph
import com.oropeza.urbanapp.asd.location.LocationFix
import com.oropeza.urbanapp.core.events.UrbanEventFactory
import com.oropeza.urbanapp.core.events.UrbanEventTypes
import com.oropeza.urbanapp.core.runtime.UrbanRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AddStopUseCase {
    suspend operator fun invoke(
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
        stopFix: LocationFix,
        startFix: LocationFix = stopFix
    ): TripOperationResult = withContext(Dispatchers.IO) {
        try {
            AsdGraph.repo.addStopDetailed(
                tripId = tripId,
                stopType = stopType,
                stopTimeMs = stopTimeMs,
                startTimeMs = startTimeMs,
                stopName = stopName,
                notes = notes,
                menUp = menUp,
                womenUp = womenUp,
                menDown = menDown,
                womenDown = womenDown,
                hasLuggage = hasLuggage,
                delayCodes = delayCodes,
                otherDelayDesc = otherDelayDesc,
                eventTimestampMs = stopTimeMs,
                stopLat = stopFix.lat,
                stopLon = stopFix.lon,
                stopAltM = stopFix.altM,
                stopAccM = stopFix.accM,
                stopProvider = stopFix.provider,
                stopFixTime = stopFix.fixTime,
                locationStatus = stopFix.status,
                startLat = startFix.lat,
                startLon = startFix.lon,
                startAltM = startFix.altM,
                startAccM = startFix.accM,
                startProvider = startFix.provider,
                startFixTime = startFix.fixTime
            )
            UrbanRuntime.publishEvent(
                UrbanEventFactory.asd(
                    UrbanEventTypes.ASD_EVENT_CREATED,
                    mapOf("tripId" to tripId, "type" to stopType)
                )
            )
            TripOperationResult.Success
        } catch (t: Throwable) {
            TripOperationResult.Failed(t)
        }
    }
}
