package com.oropeza.urbanapp.core.auth

data class UrbanPermissionSet(
    val permissions: List<UrbanPermission>
) {
    fun hasPermission(code: String): Boolean {
        return permissions.any { it.code == code && it.status == "ACTIVE" }
    }

    fun can(module: String, action: String): Boolean {
        return permissions.any { 
            it.module == module && it.action == action && it.status == "ACTIVE" 
        }
    }
}
