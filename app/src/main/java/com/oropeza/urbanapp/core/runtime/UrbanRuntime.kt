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
import com.oropeza.urbanapp.core.config.UrbanConfigurationRepository
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

    fun events(): UrbanEventBus {
        return UrbanEventBus
    }

    suspend fun publishEvent(event: UrbanEvent) {
        UrbanEventBus.publish(event)
    }

    fun eventFlow(): Flow<UrbanEvent> {
        return UrbanEventBus.eventsFlow()
    }

    suspend fun bootstrap(context: Context): UrbanBootstrapResult {
        return UrbanBootstrap.initialize(context)
    }

    fun bootstrapStatus(): UrbanBootstrapStatus {
        return UrbanBootstrap.status()
    }

    fun isPlatformReady(context: Context): Boolean {
        val status = bootstrapStatus()
        return status.identityReady && status.workspaceReady
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

    fun workspace(context: Context): UrbanWorkspace {
        return UrbanWorkspaceRepository(context).loadWorkspace()
    }

    fun reloadWorkspace(context: Context): UrbanWorkspace {
        return UrbanWorkspaceRepository(context).loadWorkspace()
    }

    fun saveWorkspace(context: Context, workspace: UrbanWorkspace) {
        UrbanWorkspaceRepository(context).saveWorkspace(workspace)
    }

    fun cloudPaths(): UrbanCloudPaths {
        return UrbanPlatformManager.getCloudPaths()
    }

    fun configuration(context: Context): UrbanConfiguration {
        return UrbanConfigurationManager.configuration(context)
    }

    fun isFeatureEnabled(context: Context, flag: String): Boolean {
        return UrbanConfigurationManager.isFeatureEnabled(context, flag)
    }

    fun enabledModules(context: Context): List<String> {
        return UrbanConfigurationManager.enabledModules(context)
    }

    fun configurationSource(context: Context): String {
        return UrbanConfigurationManager.configurationSource(context)
    }

    fun license(context: Context): UrbanLicense {
        return UrbanLicenseManager.currentLicense(context)
    }

    fun canUseModule(context: Context, module: String): Boolean {
        return UrbanLicenseManager.canUseModule(context, module)
    }

    fun canUseFeature(context: Context, feature: String): Boolean {
        return UrbanLicenseManager.canUseFeature(context, feature)
    }

    fun licenseStatus(context: Context): UrbanLicenseStatus {
        return UrbanLicenseManager.status(context)
    }

    fun currentUser(context: Context): UrbanUser {
        return UrbanAccessManager.currentUser(context)
    }

    fun permissions(context: Context): UrbanPermissionSet {
        return UrbanAccessManager.permissions(context)
    }

    fun hasPermission(context: Context, code: String): Boolean {
        return UrbanAccessManager.hasPermission(context, code)
    }

    fun can(context: Context, module: String, action: String): Boolean {
        return UrbanAccessManager.can(context, module, action)
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
