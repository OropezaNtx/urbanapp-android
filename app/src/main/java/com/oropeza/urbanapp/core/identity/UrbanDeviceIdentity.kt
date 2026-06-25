package com.oropeza.urbanapp.core.identity

data class UrbanDeviceIdentity(
    val installationId: String,
    val androidId: String?,
    val manufacturer: String,
    val model: String,
    val deviceName: String?,
    val androidVersion: String,
    val sdkInt: Int,
    val appVersionName: String,
    val appVersionCode: Long,
    val packageName: String,
    val createdAt: Long,
    val updatedAt: Long
)
