package com.oropeza.urbanapp.core.platform.cloud

data class UrbanRole(
    val roleId: String,
    val name: String,
    val description: String?,
    val permissionIds: List<String>,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long
)
