package com.oropeza.urbanapp.asd.telemetry

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import com.oropeza.urbanapp.core.runtime.UrbanRuntime

/** Lightweight sampler reused by the existing tracking watchdog. */
object TripTelemetrySystemSampler {
    suspend fun sample(context: Context, tripId: Long, finished: Boolean = false) {
        if (tripId <= 0L) return
        val battery = readBattery(context)
        val network = readNetwork(context)
        TripTelemetryRecorder.sampleDevice(
            context = context.applicationContext,
            tripId = tripId,
            batteryPct = battery.first,
            charging = battery.second,
            networkConnected = network.first,
            networkType = network.second,
            finished = finished
        )

        // Reuse the already-scheduled watchdog/telemetry cadence as the platform
        // presence cadence. This avoids another timer/service while keeping the
        // Operations Center fed with battery, GPS and active-trip state.
        UrbanRuntime.syncHeartbeat(
            context.applicationContext,
            activeTripId = if (finished) null else tripId
        )
    }

    private fun readBattery(context: Context): Pair<Int?, Boolean> {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct = if (level >= 0 && scale > 0) {
            ((level * 100f) / scale).toInt().coerceIn(0, 100)
        } else null
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        return pct to charging
    }

    private fun readNetwork(context: Context): Pair<Boolean, String> {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false to "NONE"
        val caps = cm.getNetworkCapabilities(network) ?: return false to "UNKNOWN"
        val connected = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val type = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "OTHER"
        }
        return connected to type
    }
}
