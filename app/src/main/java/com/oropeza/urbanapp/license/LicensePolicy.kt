package com.oropeza.urbanapp.license

import com.oropeza.urbanapp.BuildConfig

/**
 * Política central de acceso.
 *
 * Por defecto el enforcement está apagado para no afectar trabajo de campo.
 * Para activarlo en una build controlada:
 *
 * local.properties:
 * LICENSE_ENFORCEMENT_ENABLED=true
 */
object LicensePolicy {
    val enforcementEnabled: Boolean
        get() = BuildConfig.LICENSE_ENFORCEMENT_ENABLED

    fun canOpenApp(state: InstallationLicenseState?): Boolean {
        if (!enforcementEnabled) return true
        return state?.canUseApp == true || state?.canUseOffline == true
    }

    fun canUseModule(state: InstallationLicenseState?, module: Module): Boolean {
        if (!canOpenApp(state)) return false
        if (!enforcementEnabled) return true

        val modules = state?.license?.modules ?: return false
        return when (module) {
            Module.ASD -> modules.asd
            Module.DELAYS -> modules.delays
            Module.FLAGS -> modules.flags
            Module.LIVE -> modules.live
            Module.EXPORTS -> modules.exports
            Module.GARMIN -> modules.garmin
            Module.ANALYTICS -> modules.analytics
            Module.CC -> true
            Module.FOV -> true
            Module.DASHBOARD -> modules.analytics
        }
    }

    fun statusMessage(state: InstallationLicenseState?): String {
        if (!enforcementEnabled) return "Licenciamiento en modo diagnóstico"
        if (state == null) return "Validando licencia..."
        if (state.canUseApp) return "Licencia activa"
        if (state.canUseOffline) return "Modo offline permitido por periodo de gracia"
        return state.message.ifBlank { "Licencia no habilitada" }
    }

    enum class Module {
        ASD,
        DELAYS,
        FLAGS,
        LIVE,
        EXPORTS,
        GARMIN,
        ANALYTICS,
        CC,
        FOV,
        DASHBOARD
    }
}
