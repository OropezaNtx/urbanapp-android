package com.oropeza.urbanapp.core.diagnostics

object UrbanHealthEvaluator {

    fun evaluate(snapshot: UrbanDiagnosticsSnapshot): UrbanHealthStatus {
        val details = mutableListOf<String>()
        var errorCount = 0
        var warningCount = 0

        // Identity rules
        if (snapshot.installationId.isBlank()) {
            details.add("Identity: installationId is missing")
            errorCount++
        }

        // Database rules
        if (!snapshot.databaseAvailable) {
            details.add("Storage: Database is not available")
            errorCount++
        }

        // Sync rules
        if (snapshot.failedSyncCount >= 5) {
            details.add("Sync: High number of failed items (${snapshot.failedSyncCount})")
            errorCount++
        } else if (snapshot.failedSyncCount > 0) {
            details.add("Sync: Some items failed to sync (${snapshot.failedSyncCount})")
            warningCount++
        }
        
        if (snapshot.pendingSyncCount > 100) {
            details.add("Sync: Large backlog of pending items (${snapshot.pendingSyncCount})")
            warningCount++
        }

        // Connectivity rules
        if (!snapshot.isNetworkAvailable) {
            details.add("Network: Offline")
            warningCount++
        }

        // Permission rules
        if (!snapshot.locationPermissionGranted) {
            details.add("Permissions: Location permission missing")
            warningCount++
        }

        val status = when {
            errorCount > 0 -> "ERROR"
            warningCount > 0 -> "WARNING"
            else -> "OK"
        }

        val message = when (status) {
            "ERROR" -> "Critical issues detected. Support required."
            "WARNING" -> "App is functional but has some issues."
            else -> "All systems operational."
        }

        return UrbanHealthStatus(
            status = status,
            message = message,
            details = details
        )
    }
}
