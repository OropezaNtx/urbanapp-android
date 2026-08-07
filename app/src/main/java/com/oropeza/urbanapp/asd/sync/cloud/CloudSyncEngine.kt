package com.oropeza.urbanapp.asd.sync.cloud

import android.util.Log
import com.google.gson.JsonParser
import com.oropeza.urbanapp.asd.data.local.AsdSyncQueueItem
import com.oropeza.urbanapp.asd.data.repository.AsdRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.pow

class CloudSyncEngine(
    private val repository: AsdRepository,
    private val target: CloudSyncTarget,
    private val config: CloudSyncConfig = CloudSyncConfig()
) {
    private companion object {
        const val TAG = "AsdCloudSync"
        const val INTEGRITY_TAG = "TrackingIntegrity"
    }

    suspend fun processNextBatch(): Int = withContext(Dispatchers.IO) {
        repository.recoverStaleSyncItems()
        val pendingItems = repository.getPendingSyncItems(config.maxBatchSize)
        if (pendingItems.isEmpty()) return@withContext 0

        Log.i(TAG, "Syncing batch of ${pendingItems.size} items...")
        var syncedCount = 0
        for (item in pendingItems) {
            if (processItem(item)) syncedCount++
        }
        Log.i(TAG, "Batch sync finished. Success: $syncedCount, Failed: ${pendingItems.size - syncedCount}")
        syncedCount
    }

    private suspend fun processItem(item: AsdSyncQueueItem): Boolean {
        repository.updateSyncItem(item.copy(status = "IN_PROGRESS", updatedAt = System.currentTimeMillis()))
        Log.d(TAG, "Syncing ${item.entityType} (${item.operation}) to ${item.cloudPath}")
        val domainItem = item.toDomain()

        val result = try {
            if (item.operation == "DELETE") target.delete(domainItem) else target.upsert(domainItem)
        } catch (e: Exception) {
            Log.e(TAG, "Exception during sync for item ${item.id} at ${item.cloudPath}", e)
            CloudSyncResult.RetryableFailure(e.message ?: "Unknown exception")
        }
        return handleResult(item, result)
    }

    private suspend fun handleResult(item: AsdSyncQueueItem, result: CloudSyncResult): Boolean {
        val now = System.currentTimeMillis()
        return when (result) {
            is CloudSyncResult.Success -> {
                repository.markSyncItemSynced(item.id)
                if (item.entityType == "TRACK_CHUNK") {
                    val pointCount = try {
                        JsonParser.parseString(item.payloadJson).asJsonObject.get("pointCount")?.asInt ?: -1
                    } catch (_: Exception) { -1 }
                    Log.i(INTEGRITY_TAG, "TRACK_CHUNK_CONFIRMED queueId=${item.id} pointCount=$pointCount cloudPath=${item.cloudPath}")
                }
                true
            }
            is CloudSyncResult.RetryableFailure -> {
                Log.w(TAG, "Retryable failure for item ${item.id}: ${result.message}")
                val nextAttempts = item.attempts + 1
                if (nextAttempts >= config.maxAttempts) {
                    repository.updateSyncItem(item.copy(
                        status = "DEAD_LETTER",
                        attempts = nextAttempts,
                        lastError = "Max attempts reached: ${result.message}",
                        updatedAt = now
                    ))
                } else {
                    val backoff = (config.baseBackoffMs * 2.0.pow(nextAttempts.toDouble())).toLong()
                    val nextAttemptAt = now + min(backoff, config.maxBackoffMs)
                    repository.updateSyncItem(item.copy(
                        status = "FAILED",
                        attempts = nextAttempts,
                        lastError = result.message,
                        nextAttemptAt = nextAttemptAt,
                        updatedAt = now
                    ))
                }
                false
            }
            is CloudSyncResult.PermanentFailure -> {
                repository.updateSyncItem(item.copy(
                    status = "DEAD_LETTER",
                    lastError = result.message,
                    updatedAt = now
                ))
                false
            }
        }
    }

    private fun AsdSyncQueueItem.toDomain() = CloudSyncItem(
        queueId = id,
        entityType = entityType,
        operation = operation,
        entityLocalId = entityLocalId,
        cloudPath = cloudPath,
        payloadJson = payloadJson,
        priority = priority,
        attempts = attempts
    )
}
