package com.oropeza.urbanapp.asd.live

import android.content.Context
import android.os.BatteryManager
import android.provider.Settings
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.oropeza.urbanapp.asd.data.local.TrackPoint
import com.oropeza.urbanapp.asd.data.local.Trip
import com.oropeza.urbanapp.license.LicenseCache
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Publicador LIVE aislado.
 * - No toca Room.
 * - No cambia track_chunks.
 * - No crea historial.
 * - Actualiza un solo documento: live_devices/{installationId}
 */
class LiveDevicePublisher(
    context: Context,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val timeThresholdMs: Long = 5 * 60 * 1000L,
    private val distanceThresholdM: Double = 100.0
) {
    private val appContext = context.applicationContext
    private val installationId: String = resolveInstallationId(appContext)
    private val licenseCache = LicenseCache(appContext)

    private var lastPublishedAtMs: Long = 0L
    private var lastPublishedLat: Double? = null
    private var lastPublishedLon: Double? = null
    private var lastKnownPoint: TrackPoint? = null
    private var lastHeading: Double? = null
    private var lastSpeedMs: Double? = null
    private var lastGpsStatus: String = "UNKNOWN"
    private var syncVersion: Long = 0L

    fun rememberLocation(
        point: TrackPoint,
        heading: Double?,
        speedMs: Double?,
        gpsStatus: String
    ) {
        lastKnownPoint = point
        lastHeading = heading
        lastSpeedMs = speedMs
        lastGpsStatus = gpsStatus
    }

    fun publishTripStart(trip: Trip) {
        publish(
            trip = trip,
            reason = "TRIP_START",
            tripStatus = "ACTIVE",
            force = true
        )
    }

    fun publishTripEnd(trip: Trip) {
        publish(
            trip = trip,
            reason = "TRIP_END",
            tripStatus = "FINISHED",
            force = true
        )
    }

    fun publishEvent(trip: Trip, signal: LiveSignal) {
        publish(
            trip = trip,
            reason = signal.reason,
            tripStatus = if (trip.endTime == null) "ACTIVE" else "FINISHED",
            force = true,
            currentEvent = mapOf(
                "type" to signal.eventType,
                "eventId" to signal.eventId,
                "timestamp" to signal.timestampMs,
                "waypointId" to signal.waypointId
            )
        )
    }

    fun maybePublishLocation(trip: Trip, point: TrackPoint, heading: Double?, speedMs: Double?, gpsStatus: String) {
        rememberLocation(point, heading, speedMs, gpsStatus)

        val now = System.currentTimeMillis()
        val lastLat = lastPublishedLat
        val lastLon = lastPublishedLon

        val elapsedEnough = lastPublishedAtMs <= 0L || now - lastPublishedAtMs >= timeThresholdMs
        val distanceEnough = if (lastLat != null && lastLon != null) {
            haversineMeters(lastLat, lastLon, point.lat, point.lon) >= distanceThresholdM
        } else true

        if (elapsedEnough || distanceEnough) {
            publish(
                trip = trip,
                reason = if (distanceEnough) "DISTANCE" else "TIME",
                tripStatus = if (trip.endTime == null) "ACTIVE" else "FINISHED",
                force = true
            )
        }
    }

    fun publishGpsStatus(trip: Trip, gpsStatus: String) {
        lastGpsStatus = gpsStatus
        publish(
            trip = trip,
            reason = "GPS_STATUS",
            tripStatus = if (trip.endTime == null) "ACTIVE" else "FINISHED",
            force = true
        )
    }

    private fun publish(
        trip: Trip,
        reason: String,
        tripStatus: String,
        force: Boolean,
        currentEvent: Map<String, Any?>? = null
    ) {
        val p = lastKnownPoint
        if (!force) return

        syncVersion += 1L
        val nowMs = System.currentTimeMillis()
        val battery = readBattery(appContext)
        val licenseState = licenseCache.load()

        val data = hashMapOf<String, Any?>(
            "installationId" to installationId,
            "tripId" to trip.tripId,
            "planningRouteId" to trip.planningRouteId,

            "lat" to p?.lat,
            "lon" to p?.lon,
            "accuracy" to p?.accM,
            "heading" to lastHeading,
            "speed" to lastSpeedMs,

            "position" to mapOf(
                "lat" to p?.lat,
                "lon" to p?.lon,
                "accuracy" to p?.accM,
                "heading" to lastHeading,
                "speed" to lastSpeedMs,
                "fixTime" to p?.timeMs,
                "provider" to p?.provider
            ),

            "battery" to mapOf(
                "level" to battery.level,
                "charging" to battery.charging
            ),

            "gpsStatus" to lastGpsStatus,
            "currentWaypoint" to mapOf(
                "nextWaypointId" to trip.nextWaypointId
            ),
            "currentEvent" to currentEvent,

            "route" to mapOf(
                "routeId" to trip.planningRouteId,
                "name" to trip.routeName,
                "direction" to trip.direction,
                "routeNumber" to trip.routeNumber,
                "baseStart" to trip.baseStart,
                "baseEnd" to trip.baseEnd
            ),

            "observer" to mapOf(
                "name" to null
            ),

            "vehicle" to mapOf(
                "eco" to trip.vehicleEco,
                "plate" to trip.plateNumber,
                "type" to trip.vehicleType,
                "capacity" to trip.seatCapacity
            ),

            "device" to mapOf(
                "androidId" to installationId,
                "model" to android.os.Build.MODEL,
                "manufacturer" to android.os.Build.MANUFACTURER,
                "androidVersion" to android.os.Build.VERSION.RELEASE
            ),

            "license" to mapOf(
                "licenseId" to licenseState?.license?.licenseId,
                "customerId" to licenseState?.license?.customerId,
                "projectId" to licenseState?.license?.projectId,
                "licenseStatus" to licenseState?.license?.status,
                "installationStatus" to licenseState?.installationStatus,
                "plan" to licenseState?.license?.plan,
                "source" to licenseState?.license?.source,
                "lastCheckedAt" to licenseState?.license?.lastCheckedAt,
                "canUseApp" to licenseState?.canUseApp,
                "canUseOffline" to licenseState?.canUseOffline
            ),

            "tripStatus" to tripStatus,
            "lastUpdateClient" to nowMs,
            "lastUpdateServer" to FieldValue.serverTimestamp(),
            "syncReason" to reason,
            "syncVersion" to syncVersion
        )

        firestore.collection("live_devices")
            .document(installationId)
            .set(data, SetOptions.merge())
            .addOnSuccessListener {
                lastPublishedAtMs = nowMs
                p?.let {
                    lastPublishedLat = it.lat
                    lastPublishedLon = it.lon
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "No se pudo publicar live_devices/$installationId", e)
            }
    }

    private fun resolveInstallationId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() }
            ?: "unknown-device"
    }

    private data class BatterySnapshot(val level: Int?, val charging: Boolean)

    private fun readBattery(context: Context): BatterySnapshot {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            ?: return BatterySnapshot(null, false)

        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            .takeIf { it >= 0 }

        val status = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        return BatterySnapshot(level, charging)
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a =
            sin(dLat / 2) * sin(dLat / 2) +
                    cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                    sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    companion object {
        private const val TAG = "LiveDevicePublisher"
    }
}
