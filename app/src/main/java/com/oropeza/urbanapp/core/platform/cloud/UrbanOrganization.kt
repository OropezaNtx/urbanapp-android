package com.oropeza.urbanapp.core.platform.cloud

data class UrbanOrganization(
    val workspaceId: String, // standardized to workspaceId
    val name: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long
)
