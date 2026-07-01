package com.oropeza.urbanapp.asd.sync

import android.content.Context
import android.os.BatteryManager
import android.provider.Settings
import com.oropeza.urbanapp.BuildConfig
import com.oropeza.urbanapp.asd.AsdGraph
import com.oropeza.urbanapp.asd.data.local.TrackPoint
import com.oropeza.urbanapp.asd.location.LatLng
import com.oropeza.urbanapp.asd.location.PolylineSmoother

object AsdSyncManager {

    private fun getDeviceId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_device"
    }

    private fun getBatteryLevel(context: Context): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    suspend fun enqueueDeviceStatus(context: Context, tripId: Long?, lastPoint: TrackPoint?) {
        val status = DeviceStatusDto(
            deviceId = getDeviceId(context),
            currentTripId = tripId,
            batteryLevel = getBatteryLevel(context),
            gpsStatus = lastPoint?.provider?.split("+")?.lastOrNull(),
            lastLat = lastPoint?.lat,
            lastLon = lastPoint?.lon,
            lastFixTime = lastPoint?.timeMs,
            appVersion = BuildConfig.VERSION_NAME
        )
        // TODO: Migrar DEVICE_STATUS a paths Enterprise con cloudPath obligatorio.
        // AsdGraph.repo.enqueueSync("DEVICE_STATUS", "CREATE", 0L, status)
        // AsdCloudSyncWorker.enqueue(context)
    }

    suspend fun enqueueTrackSummary(context: Context, tripId: Long) {
        val points = AsdGraph.repo.trackPointsOnce(tripId)
        if (points.isEmpty()) return

        val totalDist = distanceMeters(points)
        val last = points.last()

        val summary = TrackSummaryDto(
            tripId = tripId,
            pointCount = points.size,
            totalDistanceM = totalDist,
            lastLat = last.lat,
            lastLon = last.lon,
            lastTime = last.timeMs
        )
        // TODO: Migrar TRACK_SUMMARY a paths Enterprise con cloudPath obligatorio.
        // AsdGraph.repo.enqueueSync("TRACK_SUMMARY", "UPDATE", tripId, summary)
        // AsdCloudSyncWorker.enqueue(context)
    }

    private fun distanceMeters(points: List<TrackPoint>): Double {
        if (points.size < 2) return 0.0
        val raw = points.map { LatLng(it.lat, it.lon) }
        val smooth = PolylineSmoother.movingAverage(raw, window = 3)
        var total = 0.0
        for (i in 1 until smooth.size) {
            total += haversineMeters(smooth[i - 1].lat, smooth[i - 1].lon, smooth[i].lat, smooth[i].lon)
        }
        return total
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
                kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
                kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        return 2 * r * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    }
}
