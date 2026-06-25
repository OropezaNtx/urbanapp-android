package com.oropeza.urbanapp.core.diagnostics

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import com.oropeza.urbanapp.BuildConfig
import com.oropeza.urbanapp.core.runtime.UrbanRuntime
import kotlinx.coroutines.flow.firstOrNull

object UrbanDiagnosticsProvider {

    suspend fun buildSnapshot(context: Context): UrbanDiagnosticsSnapshot {
        val runtimeStatus = UrbanRuntime.getRuntimeStatus(context)
        val identity = UrbanRuntime.identity(context)

        // Battery
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
            context.registerReceiver(null, filter)
        }
        val batteryLevel = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val batteryScale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val batteryPct = if (batteryLevel != null && batteryScale != null && batteryScale > 0) {
            (batteryLevel * 100 / batteryScale.toFloat()).toInt()
        } else null
        
        val chargingStatus = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = chargingStatus == BatteryManager.BATTERY_STATUS_CHARGING ||
                chargingStatus == BatteryManager.BATTERY_STATUS_FULL

        // Network
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val nw = connectivityManager.activeNetwork
        val actNw = connectivityManager.getNetworkCapabilities(nw)
        val isNetworkAvailable = actNw != null && (
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        )
        val networkType = when {
            actNw == null -> "NONE"
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
            else -> "OTHER"
        }

        // Permissions
        val hasFineLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarseLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        
        val hasBgLoc = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else true

        return UrbanDiagnosticsSnapshot(
            installationId = identity.installationId,
            shortInstallationId = runtimeStatus.shortInstallationId,
            androidId = identity.androidId,
            manufacturer = identity.manufacturer,
            model = identity.model,
            androidVersion = identity.androidVersion,
            sdkInt = identity.sdkInt,
            packageName = identity.packageName,
            appVersionName = identity.appVersionName,
            appVersionCode = identity.appVersionCode,
            buildType = if (BuildConfig.DEBUG) "debug" else "release",
            isDebug = BuildConfig.DEBUG,
            organizationId = runtimeStatus.organizationId,
            projectId = runtimeStatus.projectId,
            environment = runtimeStatus.environment,
            pendingSyncCount = runtimeStatus.pendingSyncCount ?: 0,
            failedSyncCount = runtimeStatus.failedSyncCount ?: 0,
            lastSyncAt = runtimeStatus.lastSyncAt,
            lastSyncStatus = if (runtimeStatus.lastSyncAt != null) "SUCCESS" else null,
            lastSyncError = null, // To be implemented if we track last error time
            runtimeStatus = runtimeStatus.status,
            cloudAvailable = isNetworkAvailable, // Basic mapping
            firebaseConfigured = true, // We assume since app runs
            databaseAvailable = true, // Simplified
            databaseName = "AppDatabase",
            localDataStatus = "OK",
            locationPermissionGranted = hasFineLoc || hasCoarseLoc,
            backgroundLocationPermissionGranted = hasBgLoc,
            notificationsPermissionGranted = true, // Simplified
            batteryLevel = batteryPct,
            isCharging = isCharging,
            isNetworkAvailable = isNetworkAvailable,
            networkType = networkType
        )
    }
}
