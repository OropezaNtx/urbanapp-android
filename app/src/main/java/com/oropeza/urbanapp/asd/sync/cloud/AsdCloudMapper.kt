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
            cloudTripId = identity.installationId + "_" + trip.tripId,
            localTripId = trip.tripId,
            workspaceId = workspace.organization.workspaceId,
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
            aforador = trip.aforador,
            supervisor = trip.supervisor,
            deviceInstallationId = identity.installationId,
            appVersionName = identity.appVersionName,
            appVersionCode = identity.appVersionCode,
            startTime = trip.startTime,
            endTime = trip.endTime,
            status = if (trip.endTime == null) "ACTIVE" else "CLOSED",
            createdAt = trip.startTime,
            updatedAt = System.currentTimeMillis(),

            company = trip.company,
            seatCapacity = trip.seatCapacity,
            baseStart = trip.baseStart,
            baseEnd = trip.baseEnd,
            esFs = trip.esFs,
            deviceNumber = trip.deviceNumber,
            observerSex = trip.observerSex,
            notes = trip.notes
        )
    }

    fun toCloudDto(
        context: Context,
        event: StopEvent,
        cloudTripId: String,
        onboardMen: Int,
        onboardWomen: Int
    ): AsdEventCloudDto {
        val identity = UrbanRuntime.identity(context)

        val hasStop = event.stopLat != 0.0 && event.stopLon != 0.0
        val hasStart = event.startLat != 0.0 && event.startLon != 0.0

        val primaryLat = when {
            hasStop -> event.stopLat
            hasStart -> event.startLat
            else -> 0.0
        }

        val primaryLon = when {
            hasStop -> event.stopLon
            hasStart -> event.startLon
            else -> 0.0
        }

        val primaryAlt = when {
            hasStop -> event.stopAltM
            hasStart -> event.startAltM
            else -> 0.0
        }

        val primaryAcc = when {
            hasStop && event.stopAccM > 0.0 -> event.stopAccM
            hasStart && event.startAccM > 0.0 -> event.startAccM
            else -> maxOf(event.stopAccM, event.startAccM)
        }

        return AsdEventCloudDto(
            cloudEventId = identity.installationId + "_" + event.eventId,
            cloudTripId = cloudTripId,
            localEventId = event.eventId,
            localTripId = event.tripId,
            waypointStartId = event.waypointStartId.toLong(),
            waypointStopId = event.waypointStopId.toLong(),
            eventType = event.stopType,
            timestamp = event.timestamp,
            startTime = event.startTime,
            stopTime = event.stopTime,

            lat = primaryLat,
            lon = primaryLon,
            alt = primaryAlt,
            accuracy = primaryAcc,

            stopLat = event.stopLat,
            stopLon = event.stopLon,
            stopAltM = event.stopAltM,
            stopAccM = event.stopAccM,
            stopProvider = event.stopProvider,
            stopFixTime = event.stopFixTime,

            startLat = event.startLat,
            startLon = event.startLon,
            startAltM = event.startAltM,
            startAccM = event.startAccM,
            startProvider = event.startProvider,
            startFixTime = event.startFixTime,

            locationStatus = event.locationStatus,

            menUp = event.paxMenUp,
            womenUp = event.paxWomenUp,
            menDown = event.paxMenDown,
            womenDown = event.paxWomenDown,
            onboardMen = onboardMen,
            onboardWomen = onboardWomen,

            delayCodes = event.delayCodes,
            stopName = event.stopName,
            notes = event.notes,
            otherDelayDesc = event.otherDelayDesc,
            hasLuggage = event.hasLuggage,

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