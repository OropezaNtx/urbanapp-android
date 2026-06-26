package com.oropeza.urbanapp.core.auth

data class UrbanPermission(
    val permissionId: String,
    val code: String,
    val name: String,
    val description: String?,
    val module: String,
    val action: String,
    val status: String
)
