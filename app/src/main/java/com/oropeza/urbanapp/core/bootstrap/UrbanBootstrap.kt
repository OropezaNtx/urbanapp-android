package com.oropeza.urbanapp.core.bootstrap

import android.content.Context
import android.util.Log
import com.oropeza.urbanapp.core.runtime.UrbanRuntime
import com.oropeza.urbanapp.core.identity.UrbanIdentityManager
import com.oropeza.urbanapp.core.events.UrbanEventFactory
import com.oropeza.urbanapp.core.events.UrbanEventTypes
import com.oropeza.urbanapp.core.config.UrbanConfigurationCloudDatasource
import com.oropeza.urbanapp.core.config.UrbanFeatureFlags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object UrbanBootstrap {
    private const val TAG = "UrbanBootstrap"
    
    private var currentStatus = UrbanBootstrapStatus(startedAt = System.currentTimeMillis())

    fun status(): UrbanBootstrapStatus = currentStatus

    fun resetForTesting() {
        currentStatus = UrbanBootstrapStatus(startedAt = System.currentTimeMillis())
    }

    suspend fun initialize(context: Context): UrbanBootstrapResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        currentStatus = currentStatus.copy(
            startedAt = startTime,
            status = "RUNNING",
            errors = emptyList(),
            warnings = emptyList()
        )
        
        UrbanRuntime.publishEvent(UrbanEventFactory.platform(UrbanEventTypes.PLATFORM_BOOTSTRAP_STARTED))

        try {
            // 0. Authentication (Critical for Sync/Security)
            try {
                UrbanIdentityManager.ensureAuthenticated().getOrThrow()
                Log.d(TAG, "Authenticated with UID: ${UrbanIdentityManager.getUid()}")
            } catch (e: Exception) {
                Log.e(TAG, "Authentication initialization failed", e)
                // If we have a valid offline license, we might continue
                val isLicenseActive = try { 
                    UrbanRuntime.licenseStatus(context) == com.oropeza.urbanapp.core.license.UrbanLicenseStatus.ACTIVE 
                } catch (e: Exception) { false }
                
                if (isLicenseActive) {
                    currentStatus = currentStatus.addWarning("Offline: Auth failed but using cached license")
                } else {
                    val res = failure("Critical failure: Authentication could not be established")
                    UrbanRuntime.publishEvent(UrbanEventFactory.error(UrbanEventTypes.PLATFORM_BOOTSTRAP_FAILED, "BOOTSTRAP", "AUTH", mapOf("error" to e.message)))
                    return@withContext res
                }
            }

            // 1. Identity (Critical)
            try {
                UrbanRuntime.identity(context)
                currentStatus = currentStatus.copy(identityReady = true)
                UrbanRuntime.publishEvent(UrbanEventFactory.platform(UrbanEventTypes.IDENTITY_READY))
            } catch (e: Exception) {
                Log.e(TAG, "Identity initialization failed", e)
                val res = failure("Critical failure: Identity could not be initialized")
                UrbanRuntime.publishEvent(UrbanEventFactory.error(UrbanEventTypes.PLATFORM_BOOTSTRAP_FAILED, "BOOTSTRAP", "IDENTITY", mapOf("error" to e.message)))
                return@withContext res
            }

            // 1.1 Installation (Critical)
            try {
                UrbanRuntime.syncInstallationStatus(context)
                currentStatus = currentStatus.copy(installationReady = true)
            } catch (e: Exception) {
                Log.e(TAG, "Installation sync failed", e)
                // If we have local status, we can proceed, otherwise failure
                if (UrbanRuntime.installationStatus(context) == com.oropeza.urbanapp.core.platform.InstallationStatus.PENDING) {
                     // Still pending, maybe not a critical failure yet but we should warn
                     currentStatus = currentStatus.addWarning("Installation pending activation")
                }
            }

            // 2. Workspace (Critical)
            try {
                UrbanRuntime.workspace(context)
                currentStatus = currentStatus.copy(workspaceReady = true)
                UrbanRuntime.publishEvent(UrbanEventFactory.platform(UrbanEventTypes.WORKSPACE_READY))
            } catch (e: Exception) {
                Log.e(TAG, "Workspace initialization failed", e)
                val res = failure("Critical failure: Workspace could not be loaded")
                UrbanRuntime.publishEvent(UrbanEventFactory.error(UrbanEventTypes.PLATFORM_BOOTSTRAP_FAILED, "BOOTSTRAP", "WORKSPACE", mapOf("error" to e.message)))
                return@withContext res
            }

            // 3. Permissions
            try {
                UrbanRuntime.permissions(context)
                currentStatus = currentStatus.copy(permissionsReady = true)
            } catch (e: Exception) {
                Log.w(TAG, "Permissions initialization warning", e)
                currentStatus = currentStatus.addWarning("Permissions using local defaults due to initialization error")
            }

            // 4. License
            try {
                // If installation is ACTIVE, try to sync remote license
                if (UrbanRuntime.installationStatus(context) == com.oropeza.urbanapp.core.platform.InstallationStatus.ACTIVE) {
                    UrbanRuntime.syncRemoteLicense(context)
                }
                UrbanRuntime.license(context)
                currentStatus = currentStatus.copy(licenseReady = true)
            } catch (e: Exception) {
                Log.w(TAG, "License initialization warning", e)
                currentStatus = currentStatus.addWarning("License verification deferred due to initialization error")
            }

            // 5. Configuration
            try {
                val workspace = UrbanRuntime.workspace(context)
                val configManager = com.oropeza.urbanapp.core.config.UrbanConfigurationManager
                val currentConfig = UrbanRuntime.configuration(context)
                
                if (currentConfig.featureFlags[UrbanFeatureFlags.REMOTE_CONFIG_ENABLED] == true) {
                    UrbanRuntime.publishEvent(UrbanEventFactory.platform(UrbanEventTypes.CONFIGURATION_FETCH_STARTED))
                    
                    val cloudConfig = UrbanConfigurationCloudDatasource().fetchConfiguration(workspace)
                    if (cloudConfig != null) {
                        configManager.getRepository().saveLocalConfiguration(context, cloudConfig, source = "CLOUD")
                        UrbanRuntime.publishEvent(UrbanEventFactory.platform(UrbanEventTypes.CONFIGURATION_FETCH_SUCCESS))
                    } else {
                        UrbanRuntime.publishEvent(UrbanEventFactory.platform(UrbanEventTypes.CONFIGURATION_FETCH_FAILED))
                    }
                }

                currentStatus = currentStatus.copy(configurationReady = true)
                UrbanRuntime.publishEvent(UrbanEventFactory.platform(UrbanEventTypes.CONFIGURATION_LOADED))
            } catch (e: Exception) {
                Log.w(TAG, "Configuration initialization warning", e)
                currentStatus = currentStatus.addWarning("Using local configuration defaults")
                UrbanRuntime.publishEvent(UrbanEventFactory.warning(UrbanEventTypes.CONFIGURATION_WARNING, "BOOTSTRAP", "CONFIG", mapOf("error" to e.message)))
            }

            // 6. Sync status
            try {
                UrbanRuntime.syncStatus()
                currentStatus = currentStatus.copy(syncReady = true)
            } catch (e: Exception) {
                Log.w(TAG, "Sync status initialization warning", e)
                currentStatus = currentStatus.addWarning("Sync status monitoring partially ready")
            }

            // 7. Diagnostics
            try {
                UrbanRuntime.diagnostics(context)
                currentStatus = currentStatus.copy(diagnosticsReady = true)
            } catch (e: Exception) {
                Log.w(TAG, "Diagnostics initialization warning", e)
                currentStatus = currentStatus.addWarning("Full diagnostics capture partially ready")
            }

            // Finalize status
            val finishedAt = System.currentTimeMillis()
            val finalStatus = if (currentStatus.warnings.isNotEmpty()) "WARNING" else "SUCCESS"
            currentStatus = currentStatus.copy(
                status = finalStatus,
                finishedAt = finishedAt
            )

            if (finalStatus == "SUCCESS") {
                UrbanRuntime.publishEvent(UrbanEventFactory.platform(UrbanEventTypes.PLATFORM_BOOTSTRAP_SUCCESS))
                UrbanBootstrapResult.Success(currentStatus)
            } else {
                UrbanRuntime.publishEvent(UrbanEventFactory.platform(UrbanEventTypes.PLATFORM_BOOTSTRAP_WARNING, payload = mapOf("warnings" to currentStatus.warnings)))
                UrbanBootstrapResult.Warning(currentStatus)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Unexpected bootstrap failure", e)
            failure("Unexpected bootstrap failure: ${e.message}")
        }
    }

    private fun failure(error: String): UrbanBootstrapResult {
        currentStatus = currentStatus.copy(
            status = "FAILED",
            finishedAt = System.currentTimeMillis(),
            errors = currentStatus.errors + error
        )
        return UrbanBootstrapResult.Failure(currentStatus)
    }

    private fun UrbanBootstrapStatus.addWarning(warning: String): UrbanBootstrapStatus {
        return this.copy(warnings = this.warnings + warning)
    }
}
