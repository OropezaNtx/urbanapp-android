package com.oropeza.urbanapp.asd.sync.cloud

interface CloudSyncTarget {
    suspend fun upsert(item: CloudSyncItem): CloudSyncResult
    suspend fun delete(item: CloudSyncItem): CloudSyncResult
}
