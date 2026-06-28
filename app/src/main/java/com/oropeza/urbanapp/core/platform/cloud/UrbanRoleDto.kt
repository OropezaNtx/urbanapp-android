package com.oropeza.urbanapp.core.platform.cloud

data class UrbanRoleDto(
    val roleId: String = "",
    val workspaceId: String = "", // standardized to workspaceId
    val name: String = "",
    val description: String? = null,
    val permissionIds: List<String> = emptyList(),
    val status: String = "ACTIVE",
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)
