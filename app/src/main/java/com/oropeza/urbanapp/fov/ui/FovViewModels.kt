package com.oropeza.urbanapp.fov.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.oropeza.urbanapp.asd.data.local.DbProvider
import com.oropeza.urbanapp.fov.data.FovRepository
import kotlinx.coroutines.launch

class FovVm(app: Application) : AndroidViewModel(app) {
    private val db = DbProvider.getInstance(app)
    val repo = FovRepository(db)

    val sessions = repo.sessionsFlow

    fun createSession(
        estacion: String,
        ubicacion: String,
        sentido: String,
        dateDayMs: Long,
        esFs: String?,
        supervisor: String?,
        aforador: String?,
        onCreated: (Long) -> Unit
    ) = viewModelScope.launch {
        val id = repo.createSession(estacion, ubicacion, sentido, dateDayMs, esFs, supervisor, aforador)
        onCreated(id)
    }
}
