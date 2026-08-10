package com.oropeza.urbanapp.asd.readiness

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.StatFs
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

enum class ReadinessSeverity { PASS, WARNING, BLOCKER }

data class ReadinessCheck(
    val id: String,
    val title: String,
    val severity: ReadinessSeverity,
    val summary: String,
    val value: String? = null,
)

data class FieldReadinessReport(
    val generatedAt: Long,
    val checks: List<ReadinessCheck>,
) {
    val blockers get() = checks.filter { it.severity == ReadinessSeverity.BLOCKER }
    val warnings get() = checks.filter { it.severity == ReadinessSeverity.WARNING }
    val passed get() = checks.filter { it.severity == ReadinessSeverity.PASS }
    val canStart get() = blockers.isEmpty()
    val state get() = when {
        blockers.isNotEmpty() -> "BLOCKED"
        warnings.isNotEmpty() -> "READY_WITH_WARNINGS"
        else -> "READY"
    }
}

object FieldReadiness {
    const val VERSION = "3.3.5A.4"

    private const val PREFS_NAME = "asd_field_readiness"
    private fun evidenceKey(tripId: Long) = "trip_${tripId}_evidence"

    fun evaluate(context: Context): FieldReadinessReport {
        val c = context.applicationContext
        return FieldReadinessReport(
            generatedAt = System.currentTimeMillis(),
            checks = listOf(
                locationPermission(c),
                locationServices(c),
                battery(c),
                storage(c),
                network(c),
                notifications(c),
                background(c),
            ),
        )
    }

    fun persistEvidence(context: Context, tripId: Long, report: FieldReadinessReport) {
        if (tripId <= 0L) return
        val checks = JSONArray()
        report.checks.forEach { check ->
            checks.put(
                JSONObject()
                    .put("id", check.id)
                    .put("severity", check.severity.name)
                    .put("value", check.value ?: JSONObject.NULL)
                    .put("summary", check.summary)
            )
        }
        val payload = JSONObject()
            .put("version", VERSION)
            .put("generatedAt", report.generatedAt)
            .put("state", report.state)
            .put("canStart", report.canStart)
            .put("blockers", report.blockers.size)
            .put("warnings", report.warnings.size)
            .put("passed", report.passed.size)
            .put("checks", checks)

        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(evidenceKey(tripId), payload.toString())
            .apply()
    }

    fun readEvidenceJson(context: Context, tripId: Long): String? {
        if (tripId <= 0L) return null
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(evidenceKey(tripId), null)
    }

    private fun locationPermission(context: Context): ReadinessCheck {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return when {
            fine -> ReadinessCheck("LOCATION_PERMISSION", "Permiso de ubicación", ReadinessSeverity.PASS, "Ubicación precisa autorizada para captura GPS.", "Precisa")
            coarse -> ReadinessCheck("LOCATION_PERMISSION", "Permiso de ubicación", ReadinessSeverity.BLOCKER, "La ubicación aproximada no es suficiente para un levantamiento profesional. Autoriza ubicación precisa.", "Aproximada")
            else -> ReadinessCheck("LOCATION_PERMISSION", "Permiso de ubicación", ReadinessSeverity.BLOCKER, "Afora necesita permiso de ubicación precisa para iniciar el tracking.", "Sin permiso")
        }
    }

    private fun locationServices(context: Context): ReadinessCheck {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val enabled = try {
            manager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true || manager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
        } catch (_: Throwable) { false }
        return if (enabled) ReadinessCheck("LOCATION_SERVICES", "Servicios de ubicación", ReadinessSeverity.PASS, "Los servicios de ubicación están activos.", "Activos")
        else ReadinessCheck("LOCATION_SERVICES", "Servicios de ubicación", ReadinessSeverity.BLOCKER, "Activa la ubicación del teléfono antes de iniciar el levantamiento.", "Desactivados")
    }

    private fun battery(context: Context): ReadinessCheck {
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val pct = manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 }
            ?: return ReadinessCheck("BATTERY", "Batería", ReadinessSeverity.WARNING, "No fue posible leer el nivel de batería.")
        val severity = if (pct < 25) ReadinessSeverity.WARNING else ReadinessSeverity.PASS
        val summary = if (severity == ReadinessSeverity.WARNING) {
            if (pct <= 10) "Batería críticamente baja. Conecta el equipo a energía antes de continuar si es posible."
            else "Batería baja. Se recomienda cargar el dispositivo antes de una jornada larga."
        } else "Nivel de batería adecuado para iniciar."
        return ReadinessCheck("BATTERY", "Batería", severity, summary, "$pct%")
    }

    private fun storage(context: Context): ReadinessCheck {
        return try {
            val freeMb = StatFs(context.filesDir.absolutePath).availableBytes / (1024L * 1024L)
            val severity = when {
                freeMb < 250L -> ReadinessSeverity.BLOCKER
                freeMb < 750L -> ReadinessSeverity.WARNING
                else -> ReadinessSeverity.PASS
            }
            val summary = when (severity) {
                ReadinessSeverity.BLOCKER -> "Espacio insuficiente para una captura prolongada. Libera almacenamiento antes de iniciar."
                ReadinessSeverity.WARNING -> "El almacenamiento disponible es reducido para una jornada larga."
                ReadinessSeverity.PASS -> "Hay espacio local suficiente para continuar capturando offline."
            }
            val value = if (freeMb >= 1024L) String.format("%.1f GB libres", freeMb / 1024.0) else "$freeMb MB libres"
            ReadinessCheck("STORAGE", "Almacenamiento local", severity, summary, value)
        } catch (_: Throwable) {
            ReadinessCheck("STORAGE", "Almacenamiento local", ReadinessSeverity.WARNING, "No fue posible verificar el espacio disponible.", "No disponible")
        }
    }

    private fun network(context: Context): ReadinessCheck {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val capabilities = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
            val connected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            val type = when {
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Datos móviles"
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
                connected -> "Conectado"
                else -> "Sin Internet"
            }
            if (connected) ReadinessCheck("NETWORK", "Conectividad", ReadinessSeverity.PASS, "Internet disponible. La sincronización podrá ejecutarse durante el recorrido.", type)
            else ReadinessCheck("NETWORK", "Conectividad", ReadinessSeverity.WARNING, "Sin Internet. Puedes trabajar offline; Afora sincronizará cuando vuelva la conexión.", type)
        } catch (_: Throwable) {
            ReadinessCheck("NETWORK", "Conectividad", ReadinessSeverity.WARNING, "No fue posible verificar la conectividad. La captura local sigue disponible.", "No disponible")
        }
    }

    private fun notifications(context: Context): ReadinessCheck {
        if (android.os.Build.VERSION.SDK_INT < 33) return ReadinessCheck("NOTIFICATIONS", "Notificaciones", ReadinessSeverity.PASS, "El sistema no requiere permiso runtime de notificaciones.", "Disponible")
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return if (granted) ReadinessCheck("NOTIFICATIONS", "Notificaciones", ReadinessSeverity.PASS, "Afora puede mostrar el estado persistente del tracking.", "Permitidas")
        else ReadinessCheck("NOTIFICATIONS", "Notificaciones", ReadinessSeverity.WARNING, "Las notificaciones están desactivadas. El tracking puede ser menos visible para el operador.", "Desactivadas")
    }

    private fun background(context: Context): ReadinessCheck {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val restricted = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P && manager?.isBackgroundRestricted == true
        return if (restricted) ReadinessCheck("BACKGROUND", "Ejecución en segundo plano", ReadinessSeverity.WARNING, "Android restringe actividad en segundo plano. Conviene retirar restricciones de batería para una jornada larga.", "Restringida")
        else ReadinessCheck("BACKGROUND", "Ejecución en segundo plano", ReadinessSeverity.PASS, "No se detectan restricciones generales de segundo plano.", "Disponible")
    }
}
