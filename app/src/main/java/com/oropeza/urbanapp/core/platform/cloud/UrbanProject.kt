package com.oropeza.urbanapp.core.platform.cloud

data class UrbanProject(
    val projectId: String,
    val workspaceId: String, // standardized to workspaceId
    val name: String,
    val description: String?,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long
)
