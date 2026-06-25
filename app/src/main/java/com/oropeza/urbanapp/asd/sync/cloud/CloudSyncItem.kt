package com.oropeza.urbanapp.asd.sync.cloud

data class CloudSyncItem(
    val queueId: Long,
    val entityType: String,
    val operation: String,
    val entityLocalId: Long,
    val cloudPath: String?,
    val payloadJson: String,
    val priority: Int,
    val attempts: Int
)
