package com.oropeza.urbanapp.core.diagnostics

import com.oropeza.urbanapp.core.bootstrap.UrbanBootstrapStatus

data class UrbanDiagnosticsSnapshot(
    val generatedAt: Long = System.currentTimeMillis(),
    
    // Bootstrap
    val bootstrapStatus: UrbanBootstrapStatus,

    // Identity
    val installationId: String,
    val ownerUid: String?,
    val shortInstallationId: String,
    val androidId: String?,
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val sdkInt: Int,
    
    // App
    val packageName: String,
    val appVersionName: String,
    val appVersionCode: Long,
    val buildType: String,
    val isDebug: Boolean,
    
    // Platform
    val workspaceId: String,
    val projectId: String,
    val environment: String,
    val configurationSource: String,
    val configurationUpdatedAt: Long,
    val enabledModules: List<String>,
    val remoteConfigEnabled: Boolean,

    // Sync
    val pendingSyncCount: Int,
    val failedSyncCount: Int,
    val lastSyncAt: Long?,
    val lastSyncStatus: String?,
    val lastSyncError: String?,
    val lastSyncFailedPath: String?,
    
    // Runtime
    val runtimeStatus: String,
    val cloudAvailable: Boolean?,
    val firebaseConfigured: Boolean?,
    
    // Storage
    val databaseAvailable: Boolean,
    val databaseName: String?,
    val localDataStatus: String?,
    
    // Permissions
    val locationPermissionGranted: Boolean,
    val backgroundLocationPermissionGranted: Boolean?,
    val notificationsPermissionGranted: Boolean?,
    
    // Battery
    val batteryLevel: Int?,
    val isCharging: Boolean?,
    
    // Network
    val isNetworkAvailable: Boolean,
    val networkType: String?
)
