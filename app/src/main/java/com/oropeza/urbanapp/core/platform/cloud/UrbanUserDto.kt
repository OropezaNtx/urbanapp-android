package com.oropeza.urbanapp.core.platform.cloud

data class UrbanUserDto(
    val userId: String = "",
    val email: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val organizationId: String = "",
    val roleId: String = "",
    val status: String = "ACTIVE",
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)
