package com.oropeza.urbanapp.core.diagnostics

import com.google.gson.GsonBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object UrbanDiagnosticsFormatter {

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun toShortText(snapshot: UrbanDiagnosticsSnapshot): String {
        return """
            ID: ${snapshot.shortInstallationId}
            Versión: ${snapshot.appVersionName} (${snapshot.appVersionCode})
            Sincronización: P:${snapshot.pendingSyncCount} / F:${snapshot.failedSyncCount}
            Red: ${snapshot.networkType} (${if (snapshot.isNetworkAvailable) "ON" else "OFF"})
            Batería: ${snapshot.batteryLevel}% ${if (snapshot.isCharging == true) "⚡" else ""}
        """.trimIndent()
    }

    fun toDebugText(snapshot: UrbanDiagnosticsSnapshot): String {
        val boot = snapshot.bootstrapStatus
        return """
            URBAN DIAGNOSTICS REPORT
            Generated: ${dateFmt.format(Date(snapshot.generatedAt))}
            -------------------------
            BOOTSTRAP
            Status: ${boot.status}
            Ready: ID:${if (boot.identityReady) "✅" else "❌"} WS:${if (boot.workspaceReady) "✅" else "❌"} PERM:${if (boot.permissionsReady) "✅" else "❌"} LIC:${if (boot.licenseReady) "✅" else "❌"}
            Errors: ${boot.errors.size} / Warnings: ${boot.warnings.size}

            IDENTITY
            Installation ID: ${snapshot.installationId}
            Owner UID: ${snapshot.ownerUid ?: "Not Authenticated"}
            Short ID: ${snapshot.shortInstallationId}
            Android ID: ${snapshot.androidId}
            Device: ${snapshot.manufacturer} ${snapshot.model} (Android ${snapshot.androidVersion}, API ${snapshot.sdkInt})
            
            APP
            Package: ${snapshot.packageName}
            Version: ${snapshot.appVersionName} (Build ${snapshot.appVersionCode})
            Type: ${snapshot.buildType}
            
            PLATFORM
            WS: ${snapshot.workspaceId}
            Proj: ${snapshot.projectId}
            Env: ${snapshot.environment}
            Config: ${snapshot.configurationSource} (Updated: ${dateFmt.format(Date(snapshot.configurationUpdatedAt))})
            
            SYNC
            Pending: ${snapshot.pendingSyncCount}
            Failed: ${snapshot.failedSyncCount}
            Last Sync: ${snapshot.lastSyncAt?.let { dateFmt.format(Date(it)) } ?: "Never"}
            Last Error: ${snapshot.lastSyncError ?: "None"}
            Failed Path: ${snapshot.lastSyncFailedPath ?: "None"}
            
            HEALTH & STATUS
            Runtime: ${snapshot.runtimeStatus}
            DB: ${snapshot.databaseName} (${snapshot.localDataStatus})
            Perms: Loc:${if (snapshot.locationPermissionGranted) "YES" else "NO"} / BGLoc:${if (snapshot.backgroundLocationPermissionGranted == true) "YES" else "NO"}
            
            ENVIRONMENT
            Network: ${snapshot.networkType} (${if (snapshot.isNetworkAvailable) "Available" else "Disconnected"})
            Battery: ${snapshot.batteryLevel}% (Charging: ${snapshot.isCharging})
        """.trimIndent()
    }

    fun toJson(snapshot: UrbanDiagnosticsSnapshot): String {
        return gson.toJson(snapshot)
    }
}
