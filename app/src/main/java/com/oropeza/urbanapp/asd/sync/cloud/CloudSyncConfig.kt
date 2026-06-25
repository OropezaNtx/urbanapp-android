package com.oropeza.urbanapp.asd.sync.cloud

data class CloudSyncConfig(
    val maxBatchSize: Int = 25,
    val maxAttempts: Int = 10,
    val baseBackoffMs: Long = 30_000L,
    val maxBackoffMs: Long = 1_800_000L // 30 minutes
)
