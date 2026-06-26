package com.oropeza.urbanapp.core.platform

import kotlinx.coroutines.flow.Flow

/**
 * Interface to provide synchronization status without core depending on feature packages.
 */
interface UrbanSyncStatusProvider {
    fun pendingSyncCountFlow(): Flow<Int>
    fun failedSyncCountFlow(): Flow<Int>
    fun lastSyncTimeFlow(): Flow<Long?>
    suspend fun lastSyncError(): String?
    suspend fun lastSyncFailedPath(): String?
}
