package com.oropeza.urbanapp.core.platform.cloud

data class UrbanUser(
    val userId: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val workspaceId: String, // standardized to workspaceId
    val roleId: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long
)
