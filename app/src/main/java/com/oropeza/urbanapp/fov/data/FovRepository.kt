package com.oropeza.urbanapp.fov.data

import com.oropeza.urbanapp.asd.data.local.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import androidx.room.withTransaction


class FovRepository(private val db: AppDatabase) {

    private val sessionDao = db.fovSessionDao()
    private val masterDao = db.fovRouteMasterDao()
    private val poiDao = db.fovPoiCatalogDao()
    private val obsDao = db.fovObservationDao()

    val sessionsFlow: Flow<List<FovSession>> = sessionDao.getAll()
    val sessionsWithCatalogCountFlow = sessionDao.getAllWithCatalogCount()


    fun sessionFlow(id: Long) = sessionDao.getById(id)
    fun observationsFlow(sessionId: Long) = obsDao.getBySession(sessionId)

    fun poiCatalogFlow(poiKey: String) = poiDao.getByPoi(poiKey)

    fun buildPoiKey(estacion: String, ubicacion: String, sentido: String): String {
        return "${estacion.trim()}|${ubicacion.trim()}|${sentido.trim()}"
    }

    suspend fun createSession(
        estacion: String,
        ubicacion: String,
        sentido: String,
        dateDayMs: Long,
        esFs: String?,
        supervisor: String?,
        aforador: String?
    ): Long {
        val poiKey = buildPoiKey(estacion, ubicacion, sentido)
        return sessionDao.insert(
            FovSession(
                estacion = estacion.trim(),
                ubicacion = ubicacion.trim(),
                sentido = sentido.trim(),
                dateDayMs = dateDayMs,
                esFs = esFs?.trim()?.ifBlank { null },
                supervisor = supervisor?.trim()?.ifBlank { null },
                aforador = aforador?.trim()?.ifBlank { null },
                poiKey = poiKey
            )
        )
    }

    suspend fun endSession(sessionId: Long): Boolean {
        val session = sessionDao.getByIdOnce(sessionId) ?: error("Sesión FOV no encontrada")
        if (session.endedAt != null) return true
        return sessionDao.endSession(sessionId, System.currentTimeMillis()) > 0
    }

    // ------------------------
    // Biblioteca global (master)
    // ------------------------
    suspend fun createMasterRoute(
        ruta: String,
        numeroRutaEmpresa: String,
        derroteroLetrero: String,
        createdBy: String? = null
    ): FovRouteMaster {
        fun norm(s: String) = s.trim().uppercase()
        val key = "${norm(ruta)}|${norm(numeroRutaEmpresa)}|${norm(derroteroLetrero)}"
        val stableUid = java.util.UUID.nameUUIDFromBytes(key.toByteArray(Charsets.UTF_8)).toString()

        val master = FovRouteMaster(
            routeUid = stableUid,
            ruta = ruta.trim(),
            numeroRutaEmpresa = numeroRutaEmpresa.trim(),
            derroteroLetrero = derroteroLetrero.trim(),
            createdBy = createdBy?.trim()?.ifBlank { null }
        )
        masterDao.upsert(master)
        return master
    }


    suspend fun searchMasterRoutes(q: String): List<FovRouteMaster> {
        val query = q.trim()
        if (query.isBlank()) return emptyList()
        return masterDao.searchOnce(query)
    }

    suspend fun assignRouteToPoi(poiKey: String, routeUid: String): FovPoiCatalogItem {
        return db.withTransaction {
            val existing = poiDao.getByRouteOnce(poiKey, routeUid)
            if (existing != null) return@withTransaction existing

            val nextObsId = (poiDao.getMaxObservableId(poiKey) ?: 0) + 1
            val item = FovPoiCatalogItem(
                poiKey = poiKey,
                observableId = nextObsId,
                routeUid = routeUid,
                active = true
            )
            poiDao.upsert(item)
            item
        }
    }

    suspend fun createAndAssignToPoi(
        poiKey: String,
        ruta: String,
        numeroRutaEmpresa: String,
        derroteroLetrero: String,
        createdBy: String? = null
    ): FovPoiCatalogItem {
        return db.withTransaction {
            val master = createMasterRoute(ruta, numeroRutaEmpresa, derroteroLetrero, createdBy)
            assignRouteToPoi(poiKey, master.routeUid)
        }
    }

    suspend fun searchPoiCatalog(poiKey: String, q: String): List<FovPoiCatalogRow> {
        val query = q.trim()
        if (query.isBlank()) return emptyList()
        return poiDao.searchPoiCatalogOnce(poiKey, query)
    }

    suspend fun getPoiCatalogItem(poiKey: String, observableId: Int): FovPoiCatalogItem {
        return poiDao.getByObservableOnce(poiKey, observableId)
            ?: error("No existe ID $observableId en el catálogo del POI.")
    }

    suspend fun getMaster(routeUid: String): FovRouteMaster {
        return masterDao.getByUidOnce(routeUid)
            ?: error("Ruta master no encontrada.")
    }

    // ------------------------
    // Observaciones (registro)
    // ------------------------
    suspend fun addObservation(
        sessionId: Long,
        observableId: Int,
        timeMs: Long = System.currentTimeMillis(),
        eco: String?,
        placa: String?,
        gradoOcupacion: String?,
        tipoVehiculo: String?,
        descTipoVehiculo: String?,
        observaciones: String?
    ): Long {
        val session = sessionDao.getByIdOnce(sessionId) ?: error("Sesión no encontrada")
        if (session.endedAt != null) error("La sesión FOV ya está cerrada. No se pueden agregar más registros.")

        val poiKey = session.poiKey

        val poiItem = getPoiCatalogItem(poiKey, observableId)
        val master = getMaster(poiItem.routeUid)

        val nextSeq = (obsDao.getMaxSeq(sessionId) ?: 0) + 1
        val folio = "FOV-${sessionId}-${nextSeq}-${UUID.randomUUID().toString().take(8)}"

        return obsDao.insert(
            FovObservation(
                sessionId = sessionId,
                folio = folio,
                seqInSession = nextSeq,
                timeMs = timeMs,
                poiKey = poiKey,
                observableId = observableId,
                routeUid = master.routeUid,

                ruta = master.ruta,
                numeroRutaEmpresa = master.numeroRutaEmpresa,
                derroteroLetrero = master.derroteroLetrero,

                eco = eco?.trim()?.ifBlank { null },
                placa = placa?.trim()?.ifBlank { null },
                gradoOcupacion = gradoOcupacion?.trim()?.ifBlank { null },
                tipoVehiculo = tipoVehiculo?.trim()?.ifBlank { null },
                descTipoVehiculo = descTipoVehiculo?.trim()?.ifBlank { null },
                observaciones = observaciones?.trim()?.ifBlank { null }
            )
        )
    }
}
