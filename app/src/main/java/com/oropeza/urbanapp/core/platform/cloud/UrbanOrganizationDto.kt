package com.oropeza.urbanapp.core.platform.cloud

data class UrbanOrganizationDto(
    val workspaceId: String = "", // standardized to workspaceId
    val name: String = "",
    val status: String = "ACTIVE",
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)
