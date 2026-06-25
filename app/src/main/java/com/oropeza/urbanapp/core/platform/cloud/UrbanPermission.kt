package com.oropeza.urbanapp.core.platform.cloud

data class UrbanPermission(
    val permissionId: String,
    val name: String,
    val description: String?,
    val module: String,
    val action: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long
)
