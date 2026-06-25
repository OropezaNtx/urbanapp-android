package com.oropeza.urbanapp.asd.data.repository

import com.google.gson.Gson
import com.oropeza.urbanapp.asd.data.local.AsdSyncQueueDao
import com.oropeza.urbanapp.asd.data.local.AsdSyncQueueItem
import com.oropeza.urbanapp.asd.data.local.StopEvent
import com.oropeza.urbanapp.asd.data.local.Trip

class AsdSyncQueueRepository(private val dao: AsdSyncQueueDao) {

    private val gson = Gson()

    suspend fun enqueueTripUpsert(operation: String, trip: Trip) {
        enqueue("TRIP", operation, trip.tripId, trip, priority = 0)
    }

    suspend fun enqueueEventUpsert(operation: String, event: StopEvent) {
        enqueue("EVENT", operation, event.eventId, event, priority = 0)
    }

    private suspend fun enqueue(
        type: String,
        operation: String,
        localId: Long,
        payload: Any,
        priority: Int = 1
    ) {
        try {
            val item = AsdSyncQueueItem(
                entityType = type,
                operation = operation,
                entityLocalId = localId,
                payloadJson = gson.toJson(payload),
                priority = priority,
                status = "PENDING"
            )
            dao.insert(item)
        } catch (e: Exception) {
            android.util.Log.e("AsdSyncQueueRepo", "Failed to enqueue sync for $type $localId", e)
        }
    }
}
