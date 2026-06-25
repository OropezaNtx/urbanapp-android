package com.oropeza.urbanapp.core.platform.cloud

data class UrbanPermissionDto(
    val permissionId: String = "",
    val name: String = "",
    val description: String? = null,
    val module: String = "",
    val action: String = "",
    val status: String = "ACTIVE",
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)
