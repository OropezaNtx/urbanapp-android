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
    val seatCapacity: Int? = null,

    // Campos operativos ASD
    val aforador: String? = null,
    val supervisor: String? = null,
    val deviceNumber: String? = null,
    val observerSex: String? = null // "H" | "M"
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

    val timestamp: Long,
    val stopType: String,
    val count: Int,
    val stopName: String? = null,
    val notes: String? = null,

    val waypointStopId: Int = 0,
    val waypointStartId: Int = 0,

    val stopTime: Long = 0L,
    val startTime: Long = 0L,

    val stopLat: Double = 0.0,
    val stopLon: Double = 0.0,
    val startLat: Double = 0.0,
    val startLon: Double = 0.0,

    val stopAccM: Double = 0.0,
    val stopProvider: String = "",
    val stopFixTime: Long = 0L,

    val startAccM: Double = 0.0,
    val startProvider: String = "",
    val startFixTime: Long = 0L,

    val locationStatus: String = "NO_FIX",

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
    val planningId: String = "",
    val locationName: String = "",
    val aforador: String = "",
    val dateDayMs: Long = 0L,
    val base: String = "CENTRO",
    val terminalOrigin: String = "",
    val terminalDestination: String = "",
    val direction: String = "IDA",
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
    val seqInSession: Int = 1,
    val eventType: String,
    val timeMs: Long,
    val timeIsManual: Boolean = false,
    val plate: String? = null,
    val eco: String? = null,
    val vehicleType: String? = null,
    val pax: Int = 0,
    val luggageCount: Int? = null,
    val notes: String? = null,
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val accM: Double = 0.0,
    val provider: String = "",
    val fixTime: Long = 0L,
    val locationStatus: String = "NO_FIX"
)

// =========================
// FOV (Field of View / Frecuencia Observable)
// =========================

@Entity
data class FovSession(
    @PrimaryKey(autoGenerate = true) val sessionId: Long = 0,
    val estacion: String,
    val ubicacion: String,
    val sentido: String,
    val dateDayMs: Long,
    val esFs: String? = null,
    val supervisor: String? = null,
    val aforador: String? = null,
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
    val observableId: Int,
    val routeUid: String,
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
    val folio: String,
    val seqInSession: Int,
    val timeMs: Long,
    val poiKey: String,
    val observableId: Int,
    val routeUid: String,
    val ruta: String,
    val numeroRutaEmpresa: String,
    val derroteroLetrero: String,
    val eco: String? = null,
    val placa: String? = null,
    val gradoOcupacion: String? = null,
    val tipoVehiculo: String? = null,
    val descTipoVehiculo: String? = null,
    val observaciones: String? = null,

    // ✅ GPS por observación
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val accM: Double = 0.0,
    val provider: String = "",
    val fixTime: Long = 0L,
    val locationStatus: String = "NO_FIX"
)

@Entity(
    indices = [
        Index("ruta"),
        Index("numeroRutaEmpresa")
    ]
)
data class FovRouteMaster(
    @PrimaryKey val routeUid: String,
    val ruta: String,
    val numeroRutaEmpresa: String,
    val derroteroLetrero: String,
    val createdAt: Long = System.currentTimeMillis(),
    val createdBy: String? = null
)

@Entity
data class FovOccupancyScheme(
    @PrimaryKey(autoGenerate = true) val schemeId: Long = 0,
    val name: String,
    val type: String,
    val optionsJson: String
)

@Entity(
    indices = [
        Index(value = ["catalogId", "direction"], unique = true),
        Index(value = ["routeName"]),
        Index(value = ["company"]),
        Index(value = ["active"])
    ]
)
data class AsdRouteCatalogItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val catalogId: String,
    val direction: String, // IDA | REGRESO
    val routeName: String,
    val company: String? = null,
    val derrotero: String? = null,
    val cromatica: String? = null,
    val baseStart: String? = null,
    val baseEnd: String? = null,
    val observacion: String? = null,
    val active: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    indices = [
        Index(value = ["personId"], unique = true),
        Index(value = ["name"]),
        Index(value = ["role"]),
        Index(value = ["active"])
    ]
)
data class AsdFieldPersonCatalogItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: String,
    val name: String,
    val role: String, // OBSERVADOR | SUPERVISOR | AMBOS
    val defaultSex: String? = null, // H | M
    val active: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity
data class AsdCatalogSyncState(
    @PrimaryKey val id: String = "ASD_CATALOG",
    val version: String? = null,
    val lastSyncAt: Long? = null,
    val source: String? = null, // XLSX, FIRESTORE, SEED
    val routesCount: Int = 0,
    val peopleCount: Int = 0,
    val status: String = "EMPTY", // EMPTY, READY, ERROR
    val message: String? = null
)
