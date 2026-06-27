package com.oropeza.urbanapp.license

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Gestor central de licencia.
 *
 * Fase 1: solo consulta remota + caché local + estado efectivo.
 * Fase 2: se conectará a UI para bloquear captura si corresponde.
 */
class LicenseManager(
    context: Context,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val cache: LicenseCache = LicenseCache(context)
) {
    private val appContext = context.applicationContext
    private val installationId: String = resolveInstallationId(appContext)

    suspend fun checkLicense(): InstallationLicenseState {
        return try {
            val remote = fetchRemoteState()
            cache.save(remote)
            remote
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo validar licencia remota; intentando caché", e)
            val cached = cache.load()
            if (cached != null && cached.canUseOffline) {
                cached.copy(message = "Sin conexión. Uso permitido por periodo de gracia.")
            } else {
                InstallationLicenseState(
                    installationId = installationId,
                    installationStatus = InstallationLicenseState.INSTALLATION_UNKNOWN,
                    license = cached?.license ?: UrbanLicense(),
                    checkedAt = System.currentTimeMillis(),
                    message = "Licencia no disponible o periodo de gracia agotado."
                )
            }
        }
    }

    fun getCachedState(): InstallationLicenseState? = cache.load()

    fun installationId(): String = installationId

    private suspend fun fetchRemoteState(): InstallationLicenseState {
        val now = System.currentTimeMillis()
        val installationSnap = firestore.collection("installations")
            .document(installationId)
            .get()
            .await()

        if (!installationSnap.exists()) {
            return InstallationLicenseState(
                installationId = installationId,
                installationStatus = InstallationLicenseState.INSTALLATION_PENDING,
                license = UrbanLicense(lastCheckedAt = now, source = UrbanLicense.SOURCE_REMOTE),
                checkedAt = now,
                message = "Instalación no registrada. Pendiente de activación."
            )
        }

        val installation = installationSnap.data.orEmpty()
        val installationStatus = installation["status"] as? String
            ?: InstallationLicenseState.INSTALLATION_PENDING
        val licenseId = installation["licenseId"] as? String
            ?: ""

        if (licenseId.isBlank()) {
            return InstallationLicenseState(
                installationId = installationId,
                installationStatus = installationStatus,
                license = UrbanLicense(lastCheckedAt = now, source = UrbanLicense.SOURCE_REMOTE),
                checkedAt = now,
                message = "Instalación sin licencia asignada."
            )
        }

        val licenseSnap = firestore.collection("licenses")
            .document(licenseId)
            .get()
            .await()

        if (!licenseSnap.exists()) {
            return InstallationLicenseState(
                installationId = installationId,
                installationStatus = installationStatus,
                license = UrbanLicense(
                    licenseId = licenseId,
                    status = UrbanLicense.STATUS_UNKNOWN,
                    lastCheckedAt = now,
                    source = UrbanLicense.SOURCE_REMOTE
                ),
                checkedAt = now,
                message = "Licencia asignada no existe."
            )
        }

        val data = licenseSnap.data.orEmpty()
        val modulesMap = data["modules"] as? Map<*, *> ?: emptyMap<String, Any>()

        val license = UrbanLicense(
            licenseId = licenseId,
            customerId = data["customerId"] as? String ?: installation["customerId"] as? String ?: "",
            projectId = installation["projectId"] as? String ?: data["projectId"] as? String ?: "",
            status = data["status"] as? String ?: UrbanLicense.STATUS_UNKNOWN,
            plan = data["plan"] as? String ?: "",
            expiresAt = readLong(data["expiresAt"]),
            maxDevices = readInt(data["maxDevices"]),
            maxProjects = readInt(data["maxProjects"]),
            gracePeriodDays = readInt(data["gracePeriodDays"]) ?: UrbanLicense.DEFAULT_GRACE_DAYS,
            modules = LicenseModules(
                asd = modulesMap["asd"] as? Boolean ?: true,
                delays = modulesMap["delays"] as? Boolean ?: true,
                flags = modulesMap["flags"] as? Boolean ?: true,
                live = modulesMap["live"] as? Boolean ?: true,
                exports = modulesMap["exports"] as? Boolean ?: true,
                garmin = modulesMap["garmin"] as? Boolean ?: true,
                analytics = modulesMap["analytics"] as? Boolean ?: true
            ),
            lastCheckedAt = now,
            source = UrbanLicense.SOURCE_REMOTE
        )

        firestore.collection("installations")
            .document(installationId)
            .set(
                mapOf(
                    "installationId" to installationId,
                    "lastSeen" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    "lastLicenseCheck" to now,
                    "deviceModel" to android.os.Build.MODEL,
                    "manufacturer" to android.os.Build.MANUFACTURER,
                    "androidVersion" to android.os.Build.VERSION.RELEASE
                ),
                com.google.firebase.firestore.SetOptions.merge()
            )

        return InstallationLicenseState(
            installationId = installationId,
            installationStatus = installationStatus,
            license = license,
            checkedAt = now,
            message = "Licencia validada correctamente."
        )
    }

    private fun resolveInstallationId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() }
            ?: "unknown-device"
    }

    private fun readLong(value: Any?): Long? = when (value) {
        is Long -> value
        is Int -> value.toLong()
        is Double -> value.toLong()
        is Number -> value.toLong()
        else -> null
    }

    private fun readInt(value: Any?): Int? = when (value) {
        is Int -> value
        is Long -> value.toInt()
        is Double -> value.toInt()
        is Number -> value.toInt()
        else -> null
    }

    companion object {
        private const val TAG = "LicenseManager"
    }
}
