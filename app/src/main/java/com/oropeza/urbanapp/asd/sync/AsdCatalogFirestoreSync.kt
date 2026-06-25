package com.oropeza.urbanapp.asd.sync

import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.oropeza.urbanapp.asd.AsdGraph
import com.oropeza.urbanapp.asd.data.local.AsdCatalogSyncState
import com.oropeza.urbanapp.asd.data.local.AsdFieldPersonCatalogItem
import com.oropeza.urbanapp.asd.data.local.AsdRouteCatalogItem
import com.oropeza.urbanapp.asd.data.local.AsdVehicleTypeCatalogItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class AsdCatalogSyncResult(
    val version: String?,
    val routesCount: Int,
    val peopleCount: Int,
    val vehicleTypesCount: Int,
    val message: String
)

data class AsdCatalogVersionFirestoreDto(
    val version: String = "",
    val updatedAt: Long = 0L,
    val active: Boolean = true
)

data class AsdRouteCatalogFirestoreDto(
    val catalogId: String = "",
    val direction: String = "IDA",
    val routeName: String = "",
    val company: String? = null,
    val derrotero: String? = null,
    val cromatica: String? = null,
    val baseStart: String? = null,
    val baseEnd: String? = null,
    val observacion: String? = null,
    val active: Boolean = true
)

data class AsdFieldPersonCatalogFirestoreDto(
    val personId: String = "",
    val name: String = "",
    val role: String = "AMBOS",
    val defaultSex: String? = null,
    val active: Boolean = true
)

data class AsdVehicleTypeCatalogFirestoreDto(
    val vehicleTypeId: String = "",
    val name: String = "",
    val displayName: String = "",
    val defaultSeatCapacity: Int = 0,
    val capacityApplies: Boolean = false,
    val sortOrder: Int = 100,
    val active: Boolean = true
)

// Mappers
fun AsdRouteCatalogFirestoreDto.toEntity() = AsdRouteCatalogItem(
    catalogId = catalogId,
    direction = direction,
    routeName = routeName,
    company = company,
    derrotero = derrotero,
    cromatica = cromatica,
    baseStart = baseStart,
    baseEnd = baseEnd,
    observacion = observacion,
    active = active,
    updatedAt = System.currentTimeMillis()
)

fun AsdFieldPersonCatalogFirestoreDto.toEntity() = AsdFieldPersonCatalogItem(
    personId = personId,
    name = name,
    role = role,
    defaultSex = defaultSex,
    active = active,
    updatedAt = System.currentTimeMillis()
)

fun AsdVehicleTypeCatalogFirestoreDto.toEntity() = AsdVehicleTypeCatalogItem(
    vehicleTypeId = vehicleTypeId,
    name = name,
    displayName = displayName,
    defaultSeatCapacity = defaultSeatCapacity,
    capacityApplies = capacityApplies,
    sortOrder = sortOrder,
    active = active,
    updatedAt = System.currentTimeMillis()
)

object AsdCatalogFirestoreSync {

    suspend fun checkVersionAndSyncIfNeeded(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val db = Firebase.firestore
            val versionDoc = db.collection("asd_catalog_versions").document("current").get().await()
            if (!versionDoc.exists()) return@withContext Result.success(false)

            val remoteVersion = versionDoc.getString("version") ?: ""
            val localState = AsdGraph.repo.getCatalogSyncStateOnce()
            
            if (remoteVersion != localState?.version || localState.status != "READY") {
                syncFromFirestore()
                Result.success(true)
            } else {
                Result.success(false)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun syncFromFirestore(): Result<AsdCatalogSyncResult> = withContext(Dispatchers.IO) {
        try {
            val db = Firebase.firestore
            val versionDoc = db.collection("asd_catalog_versions").document("current").get().await()
            
            if (!versionDoc.exists()) {
                return@withContext Result.failure(Exception("No existe catálogo web actual."))
            }

            val versionData = versionDoc.toObject(AsdCatalogVersionFirestoreDto::class.java)
            if (versionData?.active != true) {
                return@withContext Result.failure(Exception("El catálogo web no está activo."))
            }

            val versionStr = versionData.version

            // 1. Routes
            val routesSnap = versionDoc.reference.collection("routes").get().await()
            val routes = mutableListOf<AsdRouteCatalogItem>()
            val routesWarnings = mutableListOf<String>()

            for (doc in routesSnap.documents) {
                val dto = doc.toObject(AsdRouteCatalogFirestoreDto::class.java) ?: continue
                val catalogId = dto.catalogId.trim().uppercase()
                val routeName = dto.routeName.trim().uppercase()
                val direction = if (dto.direction.uppercase().contains("REGRESO")) "REGRESO" else "IDA"

                if (catalogId.isBlank() || routeName.isBlank()) {
                    routesWarnings.add("Ruta ${doc.id} saltada: ID o NOMBRE vacíos.")
                    continue
                }

                routes.add(dto.copy(catalogId = catalogId, routeName = routeName, direction = direction).toEntity())
            }

            if (routes.isEmpty()) {
                return@withContext Result.failure(Exception("El catálogo web no contiene rutas."))
            }

            // 2. People
            val peopleSnap = versionDoc.reference.collection("people").get().await()
            val people = mutableListOf<AsdFieldPersonCatalogItem>()
            
            for (doc in peopleSnap.documents) {
                val dto = doc.toObject(AsdFieldPersonCatalogFirestoreDto::class.java) ?: continue
                val personId = dto.personId.trim().uppercase()
                val name = dto.name.trim().uppercase()

                if (personId.isBlank() || name.isBlank()) continue

                val role = if (dto.role.uppercase() in listOf("OBSERVADOR", "SUPERVISOR", "AMBOS")) dto.role.uppercase() else "AMBOS"
                val sex = when(dto.defaultSex?.uppercase()) {
                    "H", "HOMBRE" -> "H"
                    "M", "MUJER" -> "M"
                    else -> null
                }

                people.add(dto.copy(personId = personId, name = name, role = role, defaultSex = sex).toEntity())
            }

            // 3. Vehicle Types
            val vehiclesSnap = versionDoc.reference.collection("vehicle_types").get().await()
            val vehicles = mutableListOf<AsdVehicleTypeCatalogItem>()
            for (doc in vehiclesSnap.documents) {
                val dto = doc.toObject(AsdVehicleTypeCatalogFirestoreDto::class.java) ?: continue
                if (dto.vehicleTypeId.isBlank() || dto.name.isBlank()) continue
                vehicles.add(dto.toEntity())
            }

            // Guardar en Room
            AsdGraph.repo.replaceAsdRouteCatalog(routes)
            if (people.isNotEmpty()) {
                AsdGraph.repo.replaceAsdPeopleCatalog(people)
            }
            if (vehicles.isNotEmpty()) {
                AsdGraph.repo.replaceAsdVehicleTypeCatalog(vehicles)
            }

            val syncMsg = "Catálogo web sincronizado: ${routes.size} rutas, ${people.size} personas, ${vehicles.size} unidades."

            val state = AsdCatalogSyncState(
                source = "FIRESTORE",
                version = versionStr,
                lastSyncAt = System.currentTimeMillis(),
                routesCount = routes.size,
                peopleCount = people.size,
                vehicleTypesCount = vehicles.size,
                status = "READY",
                message = syncMsg
            )
            AsdGraph.repo.updateCatalogSyncState(state)

            Result.success(
                AsdCatalogSyncResult(
                    version = versionStr,
                    routesCount = routes.size,
                    peopleCount = people.size,
                    vehicleTypesCount = vehicles.size,
                    message = syncMsg
                )
            )
        } catch (e: Exception) {
            val errMsg = "Error en sincronización web: ${e.message}"
            AsdGraph.repo.updateCatalogSyncState(
                AsdCatalogSyncState(
                    source = "FIRESTORE",
                    lastSyncAt = System.currentTimeMillis(),
                    status = "ERROR",
                    message = errMsg
                )
            )
            Result.failure(e)
        }
    }
}
