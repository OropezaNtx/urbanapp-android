package com.oropeza.urbanapp.asd.backup

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.oropeza.urbanapp.asd.data.local.StopEvent

object AsdOnlineBackup {
    private const val TAG = "AsdOnlineBackup"

    fun backupStopEvent(event: StopEvent) {
        runCatching {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"
            val payload = mapOf(
                "uid" to uid,
                "tripId" to event.tripId,
                "eventId" to event.eventId,
                "timestamp" to event.timestamp,
                "stopType" to event.stopType,
                "stopName" to event.stopName,
                "notes" to event.notes,
                "waypointStopId" to event.waypointStopId,
                "waypointStartId" to event.waypointStartId,
                "stopTime" to event.stopTime,
                "startTime" to event.startTime,
                "stopLat" to event.stopLat,
                "stopLon" to event.stopLon,
                "startLat" to event.startLat,
                "startLon" to event.startLon,
                "stopAccM" to event.stopAccM,
                "startAccM" to event.startAccM,
                "stopProvider" to event.stopProvider,
                "startProvider" to event.startProvider,
                "locationStatus" to event.locationStatus,
                "paxMenUp" to event.paxMenUp,
                "paxWomenUp" to event.paxWomenUp,
                "paxMenDown" to event.paxMenDown,
                "paxWomenDown" to event.paxWomenDown,
                "hasLuggage" to event.hasLuggage,
                "delayCodes" to event.delayCodes,
                "otherDelayDesc" to event.otherDelayDesc,
                "backupCreatedAt" to System.currentTimeMillis(),
                "backupSource" to "urbanapp_asd_demo"
            )

            FirebaseFirestore.getInstance()
                .collection("urbanapp_asd_backups")
                .document("trip_${event.tripId}_event_${event.eventId}")
                .set(payload)
                .addOnFailureListener { e -> Log.w(TAG, "Backup Firestore fallido", e) }
        }.onFailure { e ->
            Log.w(TAG, "No se pudo iniciar backup Firestore", e)
        }
    }
}
