package com.oropeza.urbanapp.core.events

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter

object UrbanEventBus {
    
    // In-memory buffer with replay of last 20 events
    private val _events = MutableSharedFlow<UrbanEvent>(replay = 20, extraBufferCapacity = 50)
    val events = _events.asSharedFlow()

    suspend fun publish(event: UrbanEvent) {
        _events.emit(event)
    }

    fun eventsFlow(): Flow<UrbanEvent> = events

    fun eventsByType(type: String): Flow<UrbanEvent> {
        return events.filter { it.type == type }
    }

    fun clearMemoryBuffer() {
        _events.resetReplayCache()
    }
}
