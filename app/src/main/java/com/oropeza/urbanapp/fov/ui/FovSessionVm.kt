package com.oropeza.urbanapp.fov.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.oropeza.urbanapp.asd.data.local.DbProvider
import com.oropeza.urbanapp.fov.data.FovRepository
import com.oropeza.urbanapp.fov.export.FovCsvExporter
import com.oropeza.urbanapp.fov.importer.FovExcelImporter
import com.oropeza.urbanapp.fov.importer.FovImportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FovSessionVm(app: Application) : AndroidViewModel(app) {
    private val db = DbProvider.getInstance(app)
    val repo = FovRepository(db)
    val sessions = repo.sessionsWithCatalogCountFlow

    private val _selectedObservableId = MutableStateFlow<Int?>(null)
    val selectedObservableId: StateFlow<Int?> = _selectedObservableId

    fun setSelectedObservableId(id: Int?) {
        _selectedObservableId.value = id
    }

    suspend fun searchMaster(q: String) = repo.searchMasterRoutes(q)

    fun assignExistingMasterToPoi(
        poiKey: String,
        routeUid: String,
        onDone: (Int) -> Unit,
        onError: (String) -> Unit
    ) = viewModelScope.launch {
        try {
            val item = repo.assignRouteToPoi(poiKey, routeUid)
            _selectedObservableId.value = item.observableId
            onDone(item.observableId)
        } catch (t: Throwable) {
            onError(t.message ?: "Error asignando ruta")
        }
    }

    fun createNewMasterAndAssign(
        poiKey: String,
        ruta: String,
        empresa: String,
        derrotero: String,
        createdBy: String?,
        onDone: (Int) -> Unit,
        onError: (String) -> Unit
    ) = viewModelScope.launch {
        try {
            val item = repo.createAndAssignToPoi(poiKey, ruta, empresa, derrotero, createdBy)
            _selectedObservableId.value = item.observableId
            onDone(item.observableId)
        } catch (t: Throwable) {
            onError(t.message ?: "Error creando ruta")
        }
    }

    fun addObservation(
        sessionId: Long,
        eco: String?,
        placa: String?,
        ocupacion: String?,
        tipoVehiculo: String?,
        descTipoVehiculo: String?,
        observaciones: String?,
        onSaved: () -> Unit,
        onError: (String) -> Unit
    ) = viewModelScope.launch {
        val id = _selectedObservableId.value
        if (id == null) {
            onError("Selecciona una ruta observable (ID) primero.")
            return@launch
        }

        try {
            repo.addObservation(
                sessionId = sessionId,
                observableId = id,
                eco = eco,
                placa = placa,
                gradoOcupacion = ocupacion,
                tipoVehiculo = tipoVehiculo,
                descTipoVehiculo = descTipoVehiculo,
                observaciones = observaciones
            )
            onSaved()
        } catch (t: Throwable) {
            onError(t.message ?: "Error al guardar")
        }
    }

    fun endSession(
        sessionId: Long,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) = viewModelScope.launch {
        try {
            repo.endSession(sessionId)
            onDone()
        } catch (t: Throwable) {
            onError(t.message ?: "Error cerrando sesión FOV")
        }
    }

    fun exportSessionCsv(
        sessionId: Long,
        uri: Uri,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) = viewModelScope.launch {
        try {
            val session = db.fovSessionDao().getByIdOnce(sessionId)
                ?: error("Sesión FOV no encontrada")
            val observations = db.fovObservationDao().getBySessionOnce(sessionId)
            FovCsvExporter.exportSession(getApplication(), uri, session, observations)
            onDone()
        } catch (t: Throwable) {
            onError(t.message ?: "Error exportando sesión FOV")
        }
    }

    fun importCatalogFromExcel(
        uri: Uri,
        onDone: (FovImportResult) -> Unit,
        onError: (String) -> Unit
    ) = viewModelScope.launch {
        try {
            val result = withContext(Dispatchers.IO) {
                FovExcelImporter(getApplication(), db).importFromXlsx(uri)
            }
            onDone(result)
        } catch (t: Throwable) {
            onError(t.message ?: "Error importando Excel")
        }
    }

    fun refreshIfNeeded() {
        // No-op: la lista se actualiza por Flow.
    }
}
