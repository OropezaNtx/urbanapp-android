package com.oropeza.urbanapp.asd.sync.cloud

import android.content.Context
import com.oropeza.urbanapp.asd.data.local.StopEvent
import com.oropeza.urbanapp.asd.data.local.TrackPoint
import com.oropeza.urbanapp.asd.data.local.Trip
import com.oropeza.urbanapp.core.runtime.UrbanRuntime

object AsdCloudMapper {

    fun toCloudDto(context: Context, trip: Trip): AsdTripCloudDto {
        val workspace = UrbanRuntime.workspace(context)
        val identity = UrbanRuntime.identity(context)
        
        return AsdTripCloudDto(
            cloudTripId = "${identity.installationId}_${trip.tripId}",
            localTripId = trip.tripId,
            organizationId = workspace.organization.organizationId,
            projectId = workspace.project.projectId,
            workspaceEnvironment = workspace.environment,
            routeId = trip.planningRouteId,
            routeName = trip.routeName,
            direction = trip.direction,
            tripNumber = trip.routeNumber,
            vehicleType = trip.vehicleType,
            vehicleEco = trip.vehicleEco,
            plateNumber = trip.plateNumber,
            observerName = trip.aforador,
            supervisorName = trip.supervisor,
            deviceInstallationId = identity.installationId,
            appVersionName = identity.appVersionName,
            appVersionCode = identity.appVersionCode,
            startTime = trip.startTime,
            endTime = trip.endTime,
            status = if (trip.endTime == null) "ACTIVE" else "CLOSED",
            createdAt = trip.startTime,
            updatedAt = System.currentTimeMillis()
        )
    }

    fun toCloudDto(context: Context, event: StopEvent, cloudTripId: String, onboardMen: Int, onboardWomen: Int): AsdEventCloudDto {
        val identity = UrbanRuntime.identity(context)
        
        return AsdEventCloudDto(
            cloudEventId = "${identity.installationId}_${event.eventId}",
            cloudTripId = cloudTripId,
            localEventId = event.eventId,
            localTripId = event.tripId,
            waypointStartId = event.waypointStartId.toLong(),
            waypointStopId = event.waypointStopId.toLong(),
            eventType = event.stopType,
            timestamp = event.timestamp,
            startTime = event.startTime,
            stopTime = event.stopTime,
            lat = event.stopLat,
            lon = event.stopLon,
            alt = event.stopAltM,
            accuracy = event.stopAccM,
            menUp = event.paxMenUp,
            womenUp = event.paxWomenUp,
            menDown = event.paxMenDown,
            womenDown = event.paxWomenDown,
            onboardMen = onboardMen,
            onboardWomen = onboardWomen,
            delayCodes = event.delayCodes,
            notes = event.notes,
            createdAt = event.timestamp,
            updatedAt = System.currentTimeMillis()
        )
    }

    fun toTrackPointDto(p: TrackPoint) = AsdTrackPointDto(
        lat = p.lat,
        lon = p.lon,
        alt = p.altM,
        accuracy = p.accM,
        time = p.timeMs,
        provider = p.provider
    )
}
