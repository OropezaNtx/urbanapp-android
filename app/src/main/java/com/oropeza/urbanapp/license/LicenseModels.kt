package com.oropeza.urbanapp.license

/**
 * Modelo estable de licenciamiento para UrbanApp.
 *
 * No controla UI directamente. Solo representa el estado efectivo que otros
 * módulos pueden consultar para habilitar o bloquear funciones.
 */
data class UrbanLicense(
    val licenseId: String = "",
    val customerId: String = "",
    val projectId: String = "",
    val status: String = STATUS_UNKNOWN,
    val plan: String = "",
    val expiresAt: Long? = null,
    val maxDevices: Int? = null,
    val maxProjects: Int? = null,
    val gracePeriodDays: Int = DEFAULT_GRACE_DAYS,
    val modules: LicenseModules = LicenseModules(),
    val lastCheckedAt: Long = 0L,
    val source: String = SOURCE_NONE
) {
    val isActive: Boolean
        get() = status.equals(STATUS_ACTIVE, ignoreCase = true)

    val isBlocked: Boolean
        get() = status.equals(STATUS_BLOCKED, ignoreCase = true) ||
            status.equals(STATUS_SUSPENDED, ignoreCase = true) ||
            status.equals(STATUS_EXPIRED, ignoreCase = true)

    val isExpiredByDate: Boolean
        get() = expiresAt != null && System.currentTimeMillis() > expiresAt

    companion object {
        const val STATUS_ACTIVE = "ACTIVE"
        const val STATUS_SUSPENDED = "SUSPENDED"
        const val STATUS_EXPIRED = "EXPIRED"
        const val STATUS_BLOCKED = "BLOCKED"
        const val STATUS_UNKNOWN = "UNKNOWN"

        const val SOURCE_NONE = "NONE"
        const val SOURCE_REMOTE = "REMOTE"
        const val SOURCE_CACHE = "CACHE"

        const val DEFAULT_GRACE_DAYS = 7
    }
}

data class LicenseModules(
    val asd: Boolean = true,
    val delays: Boolean = true,
    val flags: Boolean = true,
    val live: Boolean = true,
    val exports: Boolean = true,
    val garmin: Boolean = true,
    val analytics: Boolean = true
)

data class InstallationLicenseState(
    val installationId: String,
    val installationStatus: String = INSTALLATION_UNKNOWN,
    val license: UrbanLicense = UrbanLicense(),
    val checkedAt: Long = 0L,
    val message: String = ""
) {
    val canUseApp: Boolean
        get() = installationStatus.equals(INSTALLATION_ACTIVE, ignoreCase = true) &&
            license.isActive &&
            !license.isExpiredByDate

    val canUseOffline: Boolean
        get() {
            val days = license.gracePeriodDays.coerceAtLeast(0)
            val maxAgeMs = days * 24L * 60L * 60L * 1000L
            return license.lastCheckedAt > 0L &&
                System.currentTimeMillis() - license.lastCheckedAt <= maxAgeMs &&
                !license.isBlocked
        }

    companion object {
        const val INSTALLATION_ACTIVE = "ACTIVE"
        const val INSTALLATION_PENDING = "PENDING"
        const val INSTALLATION_BLOCKED = "BLOCKED"
        const val INSTALLATION_REVOKED = "REVOKED"
        const val INSTALLATION_UNKNOWN = "UNKNOWN"
    }
}
