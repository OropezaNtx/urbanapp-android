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
    private val config: CloudSyncConfig = CloudSyncConfig(),
    private val runId: String = "MANUAL"
) {
    private companion object {
        const val TAG = "AsdCloudSync"
        const val INTEGRITY_TAG = "CloudSyncIntegrity"
        const val TRACKING_INTEGRITY_TAG = "TrackingIntegrity"
    }

    suspend fun processNextBatch(batchNumber: Int = 1): Int = withContext(Dispatchers.IO) {
        repository.recoverStaleSyncItems()
        val pendingItems = repository.getPendingSyncItems(config.maxBatchSize)

        Log.i(
            INTEGRITY_TAG,
            "SYNC_BATCH_READ runId=$runId batch=$batchNumber count=${pendingItems.size} " +
                "maxBatchSize=${config.maxBatchSize}"
        )

        if (pendingItems.isEmpty()) {
            Log.i(INTEGRITY_TAG, "SYNC_QUEUE_EMPTY runId=$runId batch=$batchNumber")
            return@withContext 0
        }

        var syncedCount = 0
        for ((index, item) in pendingItems.withIndex()) {
            if (processItem(item, batchNumber, index + 1, pendingItems.size)) syncedCount++
        }

        Log.i(
            INTEGRITY_TAG,
            "SYNC_BATCH_CONFIRMED runId=$runId batch=$batchNumber read=${pendingItems.size} " +
                "confirmed=$syncedCount failed=${pendingItems.size - syncedCount}"
        )
        syncedCount
    }

    private suspend fun processItem(
        item: AsdSyncQueueItem,
        batchNumber: Int,
        itemNumber: Int,
        batchSize: Int
    ): Boolean {
        val startedAt = System.currentTimeMillis()
        repository.updateSyncItem(item.copy(status = "IN_PROGRESS", updatedAt = startedAt))

        Log.i(
            INTEGRITY_TAG,
            "SYNC_ITEM_STARTED runId=$runId batch=$batchNumber item=$itemNumber/$batchSize " +
                "queueId=${item.id} type=${item.entityType} operation=${item.operation} " +
                "tripId=${item.parentTripId ?: if (item.entityType == "TRIP") item.entityLocalId else -1L} " +
                "attempt=${item.attempts} cloudPath=${item.cloudPath}"
        )

        val domainItem = item.toDomain()
        val result = try {
            val cloudResult = if (item.operation == "DELETE") {
                target.delete(domainItem)
            } else {
                target.upsert(domainItem)
            }

            if (cloudResult is CloudSyncResult.Success) {
                Log.i(
                    INTEGRITY_TAG,
                    "SYNC_ITEM_UPLOADED runId=$runId queueId=${item.id} type=${item.entityType} " +
                        "elapsedMs=${System.currentTimeMillis() - startedAt} cloudPath=${item.cloudPath}"
                )
            }
            cloudResult
        } catch (e: Exception) {
            Log.e(
                INTEGRITY_TAG,
                "SYNC_ITEM_FAILED runId=$runId queueId=${item.id} type=${item.entityType} " +
                    "stage=UPLOAD exception=${e.javaClass.simpleName}:${e.message} cloudPath=${item.cloudPath}",
                e
            )
            CloudSyncResult.RetryableFailure(e.message ?: "Unknown exception")
        }

        return handleResult(item, result, startedAt)
    }

    private suspend fun handleResult(
        item: AsdSyncQueueItem,
        result: CloudSyncResult,
        startedAt: Long
    ): Boolean {
        val now = System.currentTimeMillis()
        return when (result) {
            is CloudSyncResult.Success -> {
                repository.markSyncItemSynced(item.id)

                val pointCount = if (item.entityType == "TRACK_CHUNK") {
                    try {
                        JsonParser.parseString(item.payloadJson).asJsonObject.get("pointCount")?.asInt ?: -1
                    } catch (_: Exception) {
                        -1
                    }
                } else {
                    -1
                }

                Log.i(
                    INTEGRITY_TAG,
                    "SYNC_ITEM_CONFIRMED runId=$runId queueId=${item.id} type=${item.entityType} " +
                        "tripId=${item.parentTripId ?: if (item.entityType == "TRIP") item.entityLocalId else -1L} " +
                        "pointCount=$pointCount elapsedMs=${now - startedAt} cloudPath=${item.cloudPath}"
                )

                if (item.entityType == "TRACK_CHUNK") {
                    Log.i(
                        TRACKING_INTEGRITY_TAG,
                        "TRACK_CHUNK_CONFIRMED queueId=${item.id} pointCount=$pointCount cloudPath=${item.cloudPath}"
                    )
                }
                true
            }

            is CloudSyncResult.RetryableFailure -> {
                val nextAttempts = item.attempts + 1
                if (nextAttempts >= config.maxAttempts) {
                    val error = "Max attempts reached: ${result.message}"
                    repository.updateSyncItem(
                        item.copy(
                            status = "DEAD_LETTER",
                            attempts = nextAttempts,
                            lastError = error,
                            updatedAt = now
                        )
                    )
                    Log.e(
                        INTEGRITY_TAG,
                        "SYNC_ITEM_FAILED runId=$runId queueId=${item.id} type=${item.entityType} " +
                            "result=DEAD_LETTER attempts=$nextAttempts error=$error cloudPath=${item.cloudPath}"
                    )
                } else {
                    val backoff = (config.baseBackoffMs * 2.0.pow(nextAttempts.toDouble())).toLong()
                    val nextAttemptAt = now + min(backoff, config.maxBackoffMs)
                    repository.updateSyncItem(
                        item.copy(
                            status = "FAILED",
                            attempts = nextAttempts,
                            lastError = result.message,
                            nextAttemptAt = nextAttemptAt,
                            updatedAt = now
                        )
                    )
                    Log.w(
                        INTEGRITY_TAG,
                        "SYNC_ITEM_FAILED runId=$runId queueId=${item.id} type=${item.entityType} " +
                            "result=RETRY attempts=$nextAttempts nextAttemptAt=$nextAttemptAt " +
                            "error=${result.message} cloudPath=${item.cloudPath}"
                    )
                }
                false
            }

            is CloudSyncResult.PermanentFailure -> {
                repository.updateSyncItem(
                    item.copy(
                        status = "DEAD_LETTER",
                        lastError = result.message,
                        updatedAt = now
                    )
                )
                Log.e(
                    INTEGRITY_TAG,
                    "SYNC_ITEM_FAILED runId=$runId queueId=${item.id} type=${item.entityType} " +
                        "result=PERMANENT_FAILURE error=${result.message} cloudPath=${item.cloudPath}"
                )
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
