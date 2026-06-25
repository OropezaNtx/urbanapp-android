package com.oropeza.urbanapp.core.platform

data class UrbanProject(
    val projectId: String,
    val organizationId: String,
    val name: String,
    val description: String?,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long
)
