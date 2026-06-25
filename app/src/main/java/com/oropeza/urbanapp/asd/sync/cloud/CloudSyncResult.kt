package com.oropeza.urbanapp.asd.sync.cloud

sealed class CloudSyncResult {
    object Success : CloudSyncResult()
    data class RetryableFailure(val message: String) : CloudSyncResult()
    data class PermanentFailure(val message: String) : CloudSyncResult()
}
