package com.oropeza.urbanapp.core.auth

import android.content.Context
import com.oropeza.urbanapp.core.runtime.UrbanRuntime

object UrbanAccessManager {

    fun currentUser(context: Context): UrbanUser {
        return UrbanAccessRepository(context).getCurrentUser()
    }

    fun permissions(context: Context): UrbanPermissionSet {
        val user = currentUser(context)
        return UrbanAccessRepository(context).getPermissionsForUser(user)
    }

    fun hasPermission(context: Context, code: String): Boolean {
        return permissions(context).hasPermission(code)
    }

    fun can(context: Context, module: String, action: String): Boolean {
        return permissions(context).can(module, action)
    }

    fun roles(context: Context): List<UrbanRole> {
        return UrbanAccessRepository(context).getDefaultRoles()
    }
}
