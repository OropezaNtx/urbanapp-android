package com.oropeza.urbanapp.core.diagnostics

import com.google.gson.GsonBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object UrbanDiagnosticsFormatter {

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun toShortText(snapshot: UrbanDiagnosticsSnapshot): String {
        return """
            ID: ${snapshot.installationId.take(8).uppercase()}
            Versión: ${snapshot.appVersionName}
            Sincronización: ${snapshot.pendingSyncCount} pendientes
            Red: ${snapshot.networkType} (${if (snapshot.isNetworkAvailable) "ACTIVA" else "DESCONECTADA"})
            Batería: ${snapshot.batteryLevel}% ${if (snapshot.isCharging == true) "⚡" else ""}
        """.trimIndent()
    }

    fun toDebugText(snapshot: UrbanDiagnosticsSnapshot): String {
        val boot = snapshot.bootstrapStatus
        return """
            REPORTE DE ESTADO DEL SISTEMA
            Generado: ${dateFmt.format(Date(snapshot.generatedAt))}
            -------------------------
            INICIO DE PLATAFORMA
            Estado: ${boot.status}
            Preparado: ID:${if (boot.identityReady) "✅" else "❌"} Cliente:${if (boot.workspaceReady) "✅" else "❌"} Permisos:${if (boot.permissionsReady) "✅" else "❌"} Licencia:${if (boot.licenseReady) "✅" else "❌"}

            IDENTIFICACIÓN DEL EQUIPO
            ID de Equipo: ${snapshot.installationId}
            UID de Usuario: ${snapshot.ownerUid ?: "No Autenticado"}
            Dispositivo: ${snapshot.manufacturer} ${snapshot.model} (Android ${snapshot.androidVersion})
            
            APLICACIÓN
            Paquete: ${snapshot.packageName}
            Versión: ${snapshot.appVersionName} (Build ${snapshot.appVersionCode})
            Tipo: ${snapshot.buildType}
            
            PROYECTO Y CLIENTE
            Cliente: ${snapshot.workspaceId}
            Estudio: ${snapshot.projectId}
            Ambiente: ${snapshot.environment}
            Ajustes: ${snapshot.configurationSource} (Actualizado: ${dateFmt.format(Date(snapshot.configurationUpdatedAt))})
            
            COPIA DE SEGURIDAD (SYNC)
            Pendientes: ${snapshot.pendingSyncCount}
            Con error: ${snapshot.failedSyncCount}
            Último envío: ${snapshot.lastSyncAt?.let { dateFmt.format(Date(it)) } ?: "Nunca"}
            Último fallo: ${snapshot.lastSyncError ?: "Ninguno"}
            
            SALUD DEL SISTEMA
            Estado Operativo: ${snapshot.runtimeStatus}
            Base de datos: ${snapshot.databaseName} (${snapshot.localDataStatus})
            Permisos: Ubicación:${if (snapshot.locationPermissionGranted) "SÍ" else "NO"} / Rastreo GPS:${if (snapshot.backgroundLocationPermissionGranted == true) "SÍ" else "NO"}
            
            ENTORNO
            Red: ${snapshot.networkType} (${if (snapshot.isNetworkAvailable) "Disponible" else "Desconectado"})
            Batería: ${snapshot.batteryLevel}% (Cargando: ${snapshot.isCharging})
        """.trimIndent()
    }

    fun toJson(snapshot: UrbanDiagnosticsSnapshot): String {
        return gson.toJson(snapshot)
    }
}
