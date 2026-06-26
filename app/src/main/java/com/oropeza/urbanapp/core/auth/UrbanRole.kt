package com.oropeza.urbanapp.core.auth

data class UrbanRole(
    val roleId: String,
    val organizationId: String,
    val name: String,
    val description: String?,
    val permissionIds: List<String>,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long
)
