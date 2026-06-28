package com.oropeza.urbanapp.core.heartbeat

import android.content.Context
import android.os.BatteryManager
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.oropeza.urbanapp.BuildConfig
import com.oropeza.urbanapp.core.runtime.UrbanRuntime
import com.oropeza.urbanapp.license.LicenseCache
import kotlinx.coroutines.tasks.await

/**
 * Heartbeat estándar de Urban Platform Core.
 *
 * Publica un documento pequeño y sobrescribible por instalación:
 * installations/{installationId}
 *
 * No guarda historial y no sustituye los datos operativos de live_devices.
 */
class UrbanHeartbeatPublisher(
    context: Context,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val appContext = context.applicationContext

    suspend fun publish(activeTripId: Long? = null): Result<Unit> {
        return runCatching {
            val identity = UrbanRuntime.identity(appContext)
            val config = UrbanRuntime.configuration(appContext)
            val battery = readBattery(appContext)
            val now = System.currentTimeMillis()

            val payload = mapOf(
                "installationId" to identity.installationId,
                "lastSeen" to FieldValue.serverTimestamp(),
                "lastSeenClient" to now,
                "lastHeartbeatAt" to now,
                "installationVersion" to identity.appVersionName,
                "activeTripId" to activeTripId,
                "battery" to mapOf(
                    "level" to battery.level,
                    "charging" to battery.charging
                ),
                "device" to mapOf(
                    "manufacturer" to android.os.Build.MANUFACTURER,
                    "model" to android.os.Build.MODEL,
                    "androidVersion" to android.os.Build.VERSION.RELEASE,
                    "sdkInt" to android.os.Build.VERSION.SDK_INT,
                    "appVersionName" to BuildConfig.VERSION_NAME,
                    "appVersionCode" to BuildConfig.VERSION_CODE
                ),
                "config" to mapOf(
                    "source" to UrbanRuntime.configurationSource(appContext),
                    "lastFetchAt" to UrbanRuntime.configurationLastFetchAt(appContext),
                    "heartbeatIntervalSeconds" to config.heartbeatIntervalSeconds,
                    "syncIntervalSeconds" to config.syncIntervalSeconds,
                    "trackChunkSize" to config.trackChunkSize,
                    "gpsProfile" to config.gpsProfile,
                    "enabledModules" to config.enabledModules,
                    "featureFlags" to config.featureFlags
                )
            )

            firestore.collection("installations")
                .document(identity.installationId)
                .update(payload)
                .await()
        }
    }

    private data class BatterySnapshot(val level: Int?, val charging: Boolean)

    private fun readBattery(context: Context): BatterySnapshot {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            ?: return BatterySnapshot(null, false)
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it >= 0 }
        val status = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        return BatterySnapshot(level, charging)
    }
}
