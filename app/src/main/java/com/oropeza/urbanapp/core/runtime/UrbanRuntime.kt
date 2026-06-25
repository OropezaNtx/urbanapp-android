package com.oropeza.urbanapp.core.runtime

import android.content.Context
import com.oropeza.urbanapp.core.identity.UrbanDeviceIdentity
import com.oropeza.urbanapp.core.platform.UrbanCloudPaths
import com.oropeza.urbanapp.core.platform.UrbanSyncStatusProvider
import com.oropeza.urbanapp.core.config.UrbanConfigurationManager
import com.oropeza.urbanapp.core.config.UrbanConfigurationRepository
import com.oropeza.urbanapp.core.config.UrbanRemoteConfiguration
import com.oropeza.urbanapp.core.license.LicenseResult
import com.oropeza.urbanapp.core.license.UrbanLicense
import com.oropeza.urbanapp.core.license.UrbanLicenseManager
import com.oropeza.urbanapp.core.license.UrbanLicenseStatus
import com.oropeza.urbanapp.core.diagnostics.UrbanDiagnosticsProvider
import com.oropeza.urbanapp.core.diagnostics.UrbanDiagnosticsSnapshot
import com.oropeza.urbanapp.core.diagnostics.UrbanHealthEvaluator
import com.oropeza.urbanapp.core.diagnostics.UrbanHealthStatus
import com.oropeza.urbanapp.core.diagnostics.UrbanDiagnosticsFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf

object UrbanRuntime {

    private var syncStatusProvider: UrbanSyncStatusProvider? = null

    fun setSyncStatusProvider(provider: UrbanSyncStatusProvider) {
        syncStatusProvider = provider
    }

    fun syncStatus(): UrbanSyncStatusProvider {
        return syncStatusProvider ?: object : UrbanSyncStatusProvider {
            override fun pendingSyncCountFlow(): Flow<Int> = flowOf(0)
            override fun failedSyncCountFlow(): Flow<Int> = flowOf(0)
            override fun lastSyncTimeFlow(): Flow<Long?> = flowOf(null)
        }
    }

    fun identity(context: Context): UrbanDeviceIdentity {
        return UrbanIdentityManager.getIdentity(context)
    }

    fun getShortInstallationId(context: Context): String {
        return UrbanIdentityManager.getShortInstallationId(context)
    }

    /**
     * Returns the platform settings object using fully qualified name to avoid resolution issues.
     */
    fun platformSettings(): com.oropeza.urbanapp.core.platform.UrbanPlatformSettings {
        return com.oropeza.urbanapp.core.platform.UrbanPlatformSettings
    }

    fun cloudPaths(): UrbanCloudPaths {
        return UrbanPlatformManager.getCloudPaths()
    }

    fun configuration(): UrbanConfigurationRepository {
        return UrbanConfigurationManager.getConfiguration()
    }

    fun featureFlags(): UrbanConfigurationManager {
        return UrbanConfigurationManager
    }

    fun remoteConfig(): UrbanRemoteConfiguration {
        return UrbanConfigurationManager.getRemoteConfig()
    }

    fun license(context: Context): UrbanLicense {
        return UrbanLicenseManager.getLicense(context)
    }

    fun canUseModule(context: Context, moduleId: String): LicenseResult {
        return UrbanLicenseManager.canUseModule(context, moduleId)
    }

    fun canUseFeature(context: Context, featureId: String): LicenseResult {
        return UrbanLicenseManager.canUseFeature(context, featureId)
    }

    fun licenseStatus(context: Context): UrbanLicenseStatus {
        return UrbanLicenseManager.getLicenseStatus(context)
    }

    suspend fun syncNow(context: Context): Result<Unit> {
        return UrbanSyncManager.syncNow(context)
    }

    fun syncInstallation(context: Context) {
        UrbanSyncManager.syncInstallation(context)
    }

    fun syncHeartbeat(context: Context, activeTripId: Long? = null) {
        UrbanSyncManager.syncHeartbeat(context, activeTripId)
    }

    suspend fun diagnostics(context: Context): UrbanDiagnosticsSnapshot {
        return UrbanDiagnosticsProvider.buildSnapshot(context)
    }

    suspend fun health(context: Context): UrbanHealthStatus {
        val snapshot = diagnostics(context)
        return UrbanHealthEvaluator.evaluate(snapshot)
    }

    suspend fun diagnosticsText(context: Context): String {
        val snapshot = diagnostics(context)
        return UrbanDiagnosticsFormatter.toDebugText(snapshot)
    }

    suspend fun getRuntimeStatus(context: Context): UrbanRuntimeStatus {
        val identity = identity(context)
        val orgId = UrbanPlatformManager.getOrganizationId(context)
        val projId = UrbanPlatformManager.getProjectId(context)
        val env = UrbanPlatformManager.getEnvironment(context)
        
        val pendingCount = syncStatus().pendingSyncCountFlow().firstOrNull()
        val failedCount = syncStatus().failedSyncCountFlow().firstOrNull()
        val lastSyncAt = syncStatus().lastSyncTimeFlow().firstOrNull()

        return UrbanRuntimeStatus(
            installationId = identity.installationId,
            shortInstallationId = getShortInstallationId(context),
            organizationId = orgId,
            projectId = projId,
            environment = env,
            appVersionName = identity.appVersionName,
            appVersionCode = identity.appVersionCode,
            androidModel = identity.model,
            pendingSyncCount = pendingCount,
            failedSyncCount = failedCount,
            lastSyncAt = lastSyncAt,
            status = "READY"
        )
    }
}
