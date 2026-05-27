package com.oropeza.urbanapp.asd.data.local

import androidx.room.*

@Entity
data class Trip(
    @PrimaryKey(autoGenerate = true) val tripId: Long = 0,

    // ✅ ID fijo de planeación (obligatorio)
    val planningRouteId: String = "",

    // Header actual
    val routeName: String,
    val company: String? = null,
    val vehicleEco: String? = null,
    val direction: String, // "IDA" | "REGRESO"
    val startTime: Long,
    val endTime: Long? = null,
    val notes: String? = null,

    // waypoint autoincremental
    val nextWaypointId: Int = 1,

    // campos del encabezado (layout)
    val routeNumber: Int? = null,      // No. Recorrido
    val esFs: String? = null,          // ES / FS
    val baseStart: String? = null,
    val baseEnd: String? = null,
    val plateNumber: String? = null,   // No. Placa
    val vehicleType: String? = null,   // Tipo de vehículo
    val seatCapacity: Int? = null      // Capacidad de asientos
)

@Entity(
    foreignKeys = [ForeignKey(
        entity = Trip::class,
        parentColumns = ["tripId"],
        childColumns = ["tripId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("tripId")]
)
data class StopEvent(
    @PrimaryKey(autoGenerate = true) val eventId: Long = 0,
    val tripId: Long,

    // timestamp para orden/export
    val timestamp: Long,

    // "ASCENSO" | "DESCENSO" | "BANDERA"
    val stopType: String,
    val count: Int,
    val stopName: String? = null,
    val notes: String? = null,

    // waypoint + tiempos parada/arranque
    val waypointStopId: Int = 0,
    val waypointStartId: Int = 0,

    val stopTime: Long = 0L,   // cuando se “detuvo”
    val startTime: Long = 0L,  // cuando “arranca”

    // coords (parada/arranque)
    val stopLat: Double = 0.0,
    val stopLon: Double = 0.0,
    val startLat: Double = 0.0,
    val startLon: Double = 0.0,

    // ✅ Metadatos de calidad GPS
    val stopAccM: Double = 0.0,
    val stopProvider: String = "",
    val stopFixTime: Long = 0L,

    val startAccM: Double = 0.0,
    val startProvider: String = "",
    val startFixTime: Long = 0L,

    // FIX_OK | LAST_KNOWN | CACHED | MANUAL | NO_FIX
    val locationStatus: String = "NO_FIX",

    // pax por sexo + maleta
    val paxMenUp: Int = 0,
    val paxWomenUp: Int = 0,
    val paxMenDown: Int = 0,
    val paxWomenDown: Int = 0,
    val hasLuggage: Boolean = false,

    val delayCodes: String? = null,
    val otherDelayDesc: String? = null
)

@Entity(
    foreignKeys = [ForeignKey(
        entity = Trip::class,
        parentColumns = ["tripId"],
        childColumns = ["tripId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("tripId")]
)
data class DelayEvent(
    @PrimaryKey(autoGenerate = true) val delayId: Long = 0,
    val tripId: Long,
    val timestampStart: Long,
    val timestampEnd: Long? = null,
    val delayType: String,
    val notes: String? = null,

    val startLat: Double = 0.0,
    val startLon: Double = 0.0,
    val endLat: Double? = null,
    val endLon: Double? = null
)


@Entity
data class CcSession(
    @PrimaryKey(autoGenerate = true) val sessionId: Long = 0,

    // Encabezado
    val planningId: String = "",       // Ej: "6"
    val locationName: String = "",     // Ubicación
    val aforador: String = "",

    // Día del levantamiento (00:00)
    val dateDayMs: Long = 0L,

    // Base: CENTRO | PERIFERIA
    val base: String = "CENTRO",

    val terminalOrigin: String = "",
    val terminalDestination: String = "",

    // ✅ Sentido fijo
    val direction: String = "IDA",     // IDA | REGRESO

    val companyName: String = "",
    val derrotero: String = "",

    val createdAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null
)

@Entity(
    foreignKeys = [ForeignKey(
        entity = CcSession::class,
        parentColumns = ["sessionId"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("sessionId"), Index("timeMs"), Index("seqInSession")]
)
data class CcEvent(
    @PrimaryKey(autoGenerate = true) val eventId: Long = 0,
    val sessionId: Long,

    // Consecutivo dentro de la sesión (1..n)
    val seqInSession: Int = 1,

    // "LLEGADA" | "SALIDA"
    val eventType: String,

    // Timestamp del evento
    val timeMs: Long,

    // ✅ Auditoría de hora
    val timeIsManual: Boolean = false,

    // Vehículo
    val plate: String? = null,
    val eco: String? = null,
    val vehicleType: String? = null,

    // Pax con los que llega/sale
    val pax: Int = 0,

    // Maletero (solo aplica si PERIFERIA o cuando quieras usarlo)
    val luggageCount: Int? = null,

    val notes: String? = null,

    // ✅ GPS
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val accM: Double = 0.0,
    val provider: String = "",
    val fixTime: Long = 0L,
    val locationStatus: String = "NO_FIX" // FIX_OK | FIX_USABLE | NO_FIX
)

// =========================
// FOV (Field of View / Frecuencia Observable)
// =========================

@Entity
data class FovSession(
    @PrimaryKey(autoGenerate = true) val sessionId: Long = 0,

    // Header (captura 1 vez)
    val estacion: String,
    val ubicacion: String,
    val sentido: String,

    // Día del levantamiento (00:00) o fecha seleccionada
    val dateDayMs: Long,

    val esFs: String? = null,
    val supervisor: String? = null,
    val aforador: String? = null,

    // Clave simple para agrupar POI
    val poiKey: String,

    val createdAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null
)


@Entity(
    primaryKeys = ["poiKey", "observableId"],
    indices = [Index("poiKey"), Index("routeUid")]
)
data class FovPoiCatalogItem(
    val poiKey: String,
    val observableId: Int,     // ✅ consecutivo por POI
    val routeUid: String,      // ✅ referencia a biblioteca global
    val active: Boolean = true
)


@Entity(
    foreignKeys = [
        ForeignKey(
            entity = FovSession::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("sessionId"),
        Index("timeMs"),
        Index("seqInSession"),
        Index("folio"),
        Index("routeUid"),
        Index("observableId")
    ]
)
data class FovObservation(
    @PrimaryKey(autoGenerate = true) val obsId: Long = 0,
    val sessionId: Long,

    // ✅ Consecutivos del registro
    val folio: String,
    val seqInSession: Int,

    // Hora completa
    val timeMs: Long,

    // POI y observable local
    val poiKey: String,
    val observableId: Int,

    // ✅ master route
    val routeUid: String,

    // ✅ snapshot (para export exacto aunque master cambie)
    val ruta: String,
    val numeroRutaEmpresa: String,
    val derroteroLetrero: String,

    // Campos del evento
    val eco: String? = null,
    val placa: String? = null,
    val gradoOcupacion: String? = null,
    val tipoVehiculo: String? = null,
    val descTipoVehiculo: String? = null,
    val observaciones: String? = null
)


@Entity(
    indices = [
        Index("ruta"),
        Index("numeroRutaEmpresa")
    ]
)
data class FovRouteMaster(
    @PrimaryKey val routeUid: String,   // UUID string
    val ruta: String,                  // RUTA
    val numeroRutaEmpresa: String,     // Numero de Ruta/Empresa
    val derroteroLetrero: String,      // Derrotero/Letrero
    val createdAt: Long = System.currentTimeMillis(),
    val createdBy: String? = null      // opcional (supervisor/aforador)
)

@Entity
data class FovOccupancyScheme(
    @PrimaryKey(autoGenerate = true) val schemeId: Long = 0,
    val name: String,            // ej. "1-6", "Vacío/Lleno", "Rangos 0-160"
    val type: String,            // "NUMERIC" | "CATEGORY" | "RANGE"
    val optionsJson: String      // JSON: ["1","2","3","4","5","6"] o ["VACIO","SEMI","LLENO"]
)
