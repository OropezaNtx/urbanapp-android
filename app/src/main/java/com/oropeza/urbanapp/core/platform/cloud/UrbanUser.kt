package com.oropeza.urbanapp.core.platform.cloud

data class UrbanUser(
    val userId: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val organizationId: String,
    val roleId: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long
)
