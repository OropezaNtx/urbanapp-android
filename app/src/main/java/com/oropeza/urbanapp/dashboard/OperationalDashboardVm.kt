package com.oropeza.urbanapp.dashboard

import androidx.lifecycle.ViewModel

class OperationalDashboardVm : ViewModel() {

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

    val state = kotlinx.coroutines.flow.MutableStateFlow(DashboardState())
}
