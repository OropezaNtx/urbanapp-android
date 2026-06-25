package com.oropeza.urbanapp.asd.sync.cloud

import android.util.Log

class NoopCloudSyncTarget : CloudSyncTarget {
    override suspend fun upsert(item: CloudSyncItem): CloudSyncResult {
        Log.d("NoopCloudSyncTarget", "Upserting item: ${item.entityType} ${item.entityLocalId}")
        return CloudSyncResult.Success
    }

    override suspend fun delete(item: CloudSyncItem): CloudSyncResult {
        Log.d("NoopCloudSyncTarget", "Deleting item: ${item.entityType} ${item.entityLocalId}")
        return CloudSyncResult.Success
    }
}
