package com.oropeza.urbanapp.license

import android.content.Context

/**
 * Caché local simple y estable para la licencia.
 *
 * Se usa para permitir periodo de gracia offline sin depender de Room ni de
 * migraciones. Más adelante puede migrarse a DataStore si conviene.
 */
class LicenseCache(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(state: InstallationLicenseState) {
        prefs.edit()
            .putString(KEY_INSTALLATION_ID, state.installationId)
            .putString(KEY_INSTALLATION_STATUS, state.installationStatus)
            .putString(KEY_LICENSE_ID, state.license.licenseId)
            .putString(KEY_CUSTOMER_ID, state.license.customerId)
            .putString(KEY_PROJECT_ID, state.license.projectId)
            .putString(KEY_LICENSE_STATUS, state.license.status)
            .putString(KEY_PLAN, state.license.plan)
            .putLong(KEY_EXPIRES_AT, state.license.expiresAt ?: 0L)
            .putInt(KEY_GRACE_DAYS, state.license.gracePeriodDays)
            .putLong(KEY_LAST_CHECKED_AT, state.license.lastCheckedAt)
            .putBoolean(KEY_ASD, state.license.modules.asd)
            .putBoolean(KEY_DELAYS, state.license.modules.delays)
            .putBoolean(KEY_FLAGS, state.license.modules.flags)
            .putBoolean(KEY_LIVE, state.license.modules.live)
            .putBoolean(KEY_EXPORTS, state.license.modules.exports)
            .putBoolean(KEY_GARMIN, state.license.modules.garmin)
            .putBoolean(KEY_ANALYTICS, state.license.modules.analytics)
            .apply()
    }

    fun load(): InstallationLicenseState? {
        val installationId = prefs.getString(KEY_INSTALLATION_ID, null) ?: return null
        val lastCheckedAt = prefs.getLong(KEY_LAST_CHECKED_AT, 0L)

        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L).takeIf { it > 0L }
        val modules = LicenseModules(
            asd = prefs.getBoolean(KEY_ASD, true),
            delays = prefs.getBoolean(KEY_DELAYS, true),
            flags = prefs.getBoolean(KEY_FLAGS, true),
            live = prefs.getBoolean(KEY_LIVE, true),
            exports = prefs.getBoolean(KEY_EXPORTS, true),
            garmin = prefs.getBoolean(KEY_GARMIN, true),
            analytics = prefs.getBoolean(KEY_ANALYTICS, true)
        )

        val license = UrbanLicense(
            licenseId = prefs.getString(KEY_LICENSE_ID, "").orEmpty(),
            customerId = prefs.getString(KEY_CUSTOMER_ID, "").orEmpty(),
            projectId = prefs.getString(KEY_PROJECT_ID, "").orEmpty(),
            status = prefs.getString(KEY_LICENSE_STATUS, UrbanLicense.STATUS_UNKNOWN).orEmpty(),
            plan = prefs.getString(KEY_PLAN, "").orEmpty(),
            expiresAt = expiresAt,
            gracePeriodDays = prefs.getInt(KEY_GRACE_DAYS, UrbanLicense.DEFAULT_GRACE_DAYS),
            modules = modules,
            lastCheckedAt = lastCheckedAt,
            source = UrbanLicense.SOURCE_CACHE
        )

        return InstallationLicenseState(
            installationId = installationId,
            installationStatus = prefs.getString(KEY_INSTALLATION_STATUS, InstallationLicenseState.INSTALLATION_UNKNOWN).orEmpty(),
            license = license,
            checkedAt = lastCheckedAt,
            message = "Licencia cargada desde caché local"
        )
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "urban_license_cache"
        private const val KEY_INSTALLATION_ID = "installation_id"
        private const val KEY_INSTALLATION_STATUS = "installation_status"
        private const val KEY_LICENSE_ID = "license_id"
        private const val KEY_CUSTOMER_ID = "customer_id"
        private const val KEY_PROJECT_ID = "project_id"
        private const val KEY_LICENSE_STATUS = "license_status"
        private const val KEY_PLAN = "plan"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_GRACE_DAYS = "grace_days"
        private const val KEY_LAST_CHECKED_AT = "last_checked_at"
        private const val KEY_ASD = "module_asd"
        private const val KEY_DELAYS = "module_delays"
        private const val KEY_FLAGS = "module_flags"
        private const val KEY_LIVE = "module_live"
        private const val KEY_EXPORTS = "module_exports"
        private const val KEY_GARMIN = "module_garmin"
        private const val KEY_ANALYTICS = "module_analytics"
    }
}
