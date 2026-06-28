package com.oropeza.urbanapp.core.auth

data class UrbanRole(
    val roleId: String,
    val workspaceId: String, // standardized to workspaceId
    val name: String,
    val description: String?,
    val permissionIds: List<String>,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long
)
