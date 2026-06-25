package com.oropeza.urbanapp.asd.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson
import com.oropeza.urbanapp.asd.AsdGraph
import com.oropeza.urbanapp.asd.data.local.StopEvent
import com.oropeza.urbanapp.asd.data.local.Trip
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

class AsdCloudSyncWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    private val gson = Gson()
    private val db = Firebase.firestore

    companion object {
        private const val TAG = "AsdCloudSyncWorker"
        private const val WORK_NAME = "AsdCloudSyncWork"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<AsdCloudSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }

    override suspend fun doWork(): ListenableWorker.Result {
        val pendingItems = AsdGraph.repo.getPendingSyncItems(50)
        if (pendingItems.isEmpty()) return ListenableWorker.Result.success()

        var successCount = 0
        for (item in pendingItems) {
            try {
                // Mock sync for Phase 1
                val success = true

                if (success) {
                    AsdGraph.repo.markSyncItemSynced(item.id)
                    successCount++
                } else {
                    markAsFailed(item, "Sync failed for ${item.entityType}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing item ${item.id}", e)
                markAsFailed(item, e.message ?: "Unknown error")
            }
        }

        return if (successCount < pendingItems.size) {
            ListenableWorker.Result.retry()
        } else {
            ListenableWorker.Result.success()
        }
    }

    private suspend fun markAsFailed(item: com.oropeza.urbanapp.asd.data.local.AsdSyncQueueItem, error: String) {
        val updated = item.copy(
            attempts = item.attempts + 1,
            lastError = error,
            status = "FAILED",
            updatedAt = System.currentTimeMillis()
        )
        AsdGraph.repo.updateSyncItem(updated)
    }

    private suspend fun syncTrip(json: String): Boolean {
        val trip = gson.fromJson(json, Trip::class.java)
        db.collection("asd_trips").document(trip.tripId.toString())
            .set(trip)
            .await()
        return true
    }

    private suspend fun syncEvent(json: String): Boolean {
        val event = gson.fromJson(json, StopEvent::class.java)
        db.collection("asd_trips").document(event.tripId.toString())
            .collection("events").document(event.eventId.toString())
            .set(event)
            .await()
        return true
    }

    private suspend fun syncTrackSummary(json: String): Boolean {
        val summary = gson.fromJson(json, TrackSummaryDto::class.java)
        db.collection("asd_trips").document(summary.tripId.toString())
            .collection("track_summary").document("latest")
            .set(summary)
            .await()
        return true
    }

    private suspend fun syncDeviceStatus(json: String): Boolean {
        val status = gson.fromJson(json, DeviceStatusDto::class.java)
        db.collection("asd_devices").document(status.deviceId)
            .collection("status").document("current")
            .set(status)
            .await()
        return true
    }
}
