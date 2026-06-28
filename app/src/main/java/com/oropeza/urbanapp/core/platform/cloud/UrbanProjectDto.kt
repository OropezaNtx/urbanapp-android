package com.oropeza.urbanapp.core.platform.cloud

data class UrbanProjectDto(
    val projectId: String = "",
    val workspaceId: String = "", // standardized to workspaceId
    val name: String = "",
    val description: String? = null,
    val status: String = "ACTIVE",
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)
