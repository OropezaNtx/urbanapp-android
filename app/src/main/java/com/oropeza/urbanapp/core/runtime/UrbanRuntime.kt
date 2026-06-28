package com.oropeza.urbanapp.core.runtime

import android.content.Context
import com.oropeza.urbanapp.core.identity.UrbanDeviceIdentity
import com.oropeza.urbanapp.core.platform.UrbanCloudPaths
import com.oropeza.urbanapp.core.platform.UrbanSyncStatusProvider
import com.oropeza.urbanapp.core.platform.UrbanWorkspace
import com.oropeza.urbanapp.core.platform.UrbanWorkspaceRepository
import com.oropeza.urbanapp.core.events.UrbanEvent
import com.oropeza.urbanapp.core.events.UrbanEventBus
import com.oropeza.urbanapp.core.bootstrap.UrbanBootstrap
import com.oropeza.urbanapp.core.bootstrap.UrbanBootstrapResult
import com.oropeza.urbanapp.core.bootstrap.UrbanBootstrapStatus
import com.oropeza.urbanapp.core.auth.UrbanAccessManager
import com.oropeza.urbanapp.core.auth.UrbanUser
import com.oropeza.urbanapp.core.auth.UrbanPermissionSet
import com.oropeza.urbanapp.core.config.UrbanConfiguration
import com.oropeza.urbanapp.core.config.UrbanConfigurationManager
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
            override suspend fun lastSyncError(): String? = null
            override suspend fun lastSyncFailedPath(): String? = null
        }
    }

    fun events(): UrbanEventBus = UrbanEventBus

    suspend fun publishEvent(event: UrbanEvent) {
        UrbanEventBus.publish(event)
    }

    fun eventFlow(): Flow<UrbanEvent> = UrbanEventBus.eventsFlow()

    suspend fun bootstrap(context: Context): UrbanBootstrapResult = UrbanBootstrap.initialize(context)

    fun bootstrapStatus(): UrbanBootstrapStatus = UrbanBootstrap.status()

    fun isPlatformReady(context: Context): Boolean {
        val status = bootstrapStatus()
        return status.identityReady && status.workspaceReady
    }

    fun identity(context: Context): UrbanDeviceIdentity = UrbanIdentityManager.getIdentity(context)

    fun getShortInstallationId(context: Context): String = UrbanIdentityManager.getShortInstallationId(context)

    fun platformSettings(): com.oropeza.urbanapp.core.platform.UrbanPlatformSettings {
        return com.oropeza.urbanapp.core.platform.UrbanPlatformSettings
    }

    fun workspace(context: Context): UrbanWorkspace = UrbanWorkspaceRepository(context).loadWorkspace()

    fun reloadWorkspace(context: Context): UrbanWorkspace = UrbanWorkspaceRepository(context).loadWorkspace()

    fun saveWorkspace(context: Context, workspace: UrbanWorkspace) {
        UrbanWorkspaceRepository(context).saveWorkspace(workspace)
    }

    fun cloudPaths(): UrbanCloudPaths = UrbanPlatformManager.getCloudPaths()

    fun configuration(context: Context): UrbanConfiguration = UrbanConfigurationManager.configuration(context)

    suspend fun refreshConfiguration(context: Context): UrbanConfiguration = UrbanConfigurationManager.refreshRemote(context)

    fun configurationLastFetchAt(context: Context): Long = UrbanConfigurationManager.lastFetchAt(context)

    fun isFeatureEnabled(context: Context, flag: String): Boolean = UrbanConfigurationManager.isFeatureEnabled(context, flag)

    fun enabledModules(context: Context): List<String> = UrbanConfigurationManager.enabledModules(context)

    fun configurationSource(context: Context): String = UrbanConfigurationManager.configurationSource(context)

    fun license(context: Context): UrbanLicense = UrbanLicenseManager.currentLicense(context)

    fun canUseModule(context: Context, module: String): Boolean = UrbanLicenseManager.canUseModule(context, module)

    fun canUseFeature(context: Context, feature: String): Boolean = UrbanLicenseManager.canUseFeature(context, feature)

    fun licenseStatus(context: Context): UrbanLicenseStatus = UrbanLicenseManager.status(context)

    suspend fun syncRemoteLicense(context: Context): UrbanLicense? = UrbanLicenseManager.syncRemoteLicense(context)

    fun currentUser(context: Context): UrbanUser = UrbanAccessManager.currentUser(context)

    fun permissions(context: Context): UrbanPermissionSet = UrbanAccessManager.permissions(context)

    fun hasPermission(context: Context, code: String): Boolean = UrbanAccessManager.hasPermission(context, code)

    fun can(context: Context, module: String, action: String): Boolean = UrbanAccessManager.can(context, module, action)

    fun installationStatus(context: Context): com.oropeza.urbanapp.core.platform.InstallationStatus {
        return com.oropeza.urbanapp.core.platform.UrbanInstallationManager(context).getLocalStatus()
    }

    suspend fun syncInstallationStatus(context: Context): com.oropeza.urbanapp.core.platform.InstallationStatus {
        return com.oropeza.urbanapp.core.platform.UrbanInstallationManager(context).syncInstallationStatus()
    }

    suspend fun syncNow(context: Context): Result<Unit> = UrbanSyncManager.syncNow(context)

    fun syncInstallation(context: Context) {
        UrbanSyncManager.syncInstallation(context)
    }

    fun syncHeartbeat(context: Context, activeTripId: Long? = null) {
        UrbanSyncManager.syncHeartbeat(context, activeTripId)
    }

    suspend fun publishPlatformHeartbeat(context: Context, activeTripId: Long? = null): Result<Unit> {
        return UrbanSyncManager.publishPlatformHeartbeat(context, activeTripId)
    }

    suspend fun diagnostics(context: Context): UrbanDiagnosticsSnapshot = UrbanDiagnosticsProvider.buildSnapshot(context)

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
        val workspace = workspace(context)
        val pendingCount = syncStatus().pendingSyncCountFlow().firstOrNull()
        val failedCount = syncStatus().failedSyncCountFlow().firstOrNull()
        val lastSyncAt = syncStatus().lastSyncTimeFlow().firstOrNull()
        val lastError = syncStatus().lastSyncError()
        val lastPath = syncStatus().lastSyncFailedPath()

        return UrbanRuntimeStatus(
            installationId = identity.installationId,
            shortInstallationId = getShortInstallationId(context),
            organizationId = workspace.organization.organizationId,
            projectId = workspace.project.projectId,
            environment = workspace.environment,
            appVersionName = identity.appVersionName,
            appVersionCode = identity.appVersionCode,
            androidModel = identity.model,
            pendingSyncCount = pendingCount,
            failedSyncCount = failedCount,
            lastSyncAt = lastSyncAt,
            lastSyncError = lastError,
            lastSyncFailedPath = lastPath,
            licenseStatus = workspace.license.status.name,
            licenseType = workspace.license.type.name,
            status = "READY"
        )
    }
}
