package com.oropeza.urbanapp.core.platform.cloud

data class UrbanOrganization(
    val organizationId: String,
    val name: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long
)
