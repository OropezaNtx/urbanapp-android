package com.oropeza.urbanapp.core.auth

data class UrbanUser(
    val userId: String,
    val workspaceId: String, // standardized to workspaceId
    val projectId: String,
    val name: String,
    val email: String,
    val roleIds: List<String>,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long
)
