package com.oropeza.urbanapp.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.oropeza.urbanapp.asd.data.local.DbProvider
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class OperationalDashboardVm(app: Application) : AndroidViewModel(app) {

    data class DashboardState(
        val fovSessions: Int = 0,
        val fovOpenSessions: Int = 0,
        val fovObservations: Int = 0,
        val fovGpsValidPercent: Int = 0,
        val asdTrips: Int = 0,
        val asdOpenTrips: Int = 0,
        val asdEvents: Int = 0,
        val asdTrackPoints: Int = 0,
        val ccSessions: Int = 0,
        val ccOpenSessions: Int = 0,
        val ccEvents: Int = 0
    )

    private val dao = DbProvider.getInstance(app).operationalDashboardDao()

    val state = combine(
        listOf(
            dao.fovSessionsCount(),
            dao.fovOpenSessionsCount(),
            dao.fovObservationsCount(),
            dao.fovValidGpsCount(),
            dao.asdTripsCount(),
            dao.asdOpenTripsCount(),
            dao.asdEventsCount(),
            dao.asdTrackPointsCount(),
            dao.ccSessionsCount(),
            dao.ccOpenSessionsCount(),
            dao.ccEventsCount()
        )
    ) { values ->
        val fovObservations = values[2]
        val fovValidGps = values[3]
        val fovGpsPercent = if (fovObservations > 0) {
            ((fovValidGps.toDouble() / fovObservations.toDouble()) * 100.0).toInt()
        } else {
            0
        }

        DashboardState(
            fovSessions = values[0],
            fovOpenSessions = values[1],
            fovObservations = fovObservations,
            fovGpsValidPercent = fovGpsPercent,
            asdTrips = values[4],
            asdOpenTrips = values[5],
            asdEvents = values[6],
            asdTrackPoints = values[7],
            ccSessions = values[8],
            ccOpenSessions = values[9],
            ccEvents = values[10]
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardState()
    )
}
