package com.oropeza.urbanapp.asd.live

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Bus interno, ligero y desacoplado.
 * Repository avisa que ocurrió algo operativo; TrackingService decide si publica Live.
 */
object LiveEventBus {
    private val _events = MutableSharedFlow<LiveSignal>(
        replay = 0,
        extraBufferCapacity = 64
    )

    val events = _events.asSharedFlow()

    fun tryEmit(signal: LiveSignal) {
        _events.tryEmit(signal)
    }
}

data class LiveSignal(
    val tripId: Long,
    val reason: String,
    val eventType: String? = null,
    val eventId: Long? = null,
    val waypointId: Int? = null,
    val timestampMs: Long = System.currentTimeMillis()
) {
    companion object {
        const val TRIP_START = "TRIP_START"
        const val TRIP_END = "TRIP_END"
        const val EVENT = "EVENT"
        const val WAYPOINT = "WAYPOINT"
        const val GPS_STATUS = "GPS_STATUS"
    }
}
