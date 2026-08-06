package com.oropeza.urbanapp.core.platform

import android.content.Context

object UrbanPlatformSettings {

    const val DEFAULT_ORGANIZATION_ID = "afora"
    const val DEFAULT_PROJECT_ID = "urban_operations"

    private const val PREFS_NAME = "urban_platform_settings"
    private const val KEY_WORKSPACE_ID = "workspace_id" // Standardized
    private const val KEY_ORG_ID = "organization_id"   // Legacy support
    private const val KEY_PROJECT_ID = "project_id"
    private const val KEY_LICENSE_ID = "license_id"
    private const val KEY_ENVIRONMENT = "environment"

    fun getWorkspaceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_WORKSPACE_ID, null)
            ?: prefs.getString(KEY_ORG_ID, null)
            ?: DEFAULT_ORGANIZATION_ID
    }

    fun getProjectId(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PROJECT_ID, DEFAULT_PROJECT_ID) ?: DEFAULT_PROJECT_ID
    }

    fun getLicenseId(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LICENSE_ID, null)
    }

    fun getEnvironment(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ENVIRONMENT, "pilot") ?: "pilot"
    }

    fun saveSettings(context: Context, workspaceId: String, projectId: String, licenseId: String, env: String = "pilot") {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_WORKSPACE_ID, workspaceId)
            .putString(KEY_ORG_ID, workspaceId) // Support both during migration
            .putString(KEY_PROJECT_ID, projectId)
            .putString(KEY_LICENSE_ID, licenseId)
            .putString(KEY_ENVIRONMENT, env)
            .apply()
    }
}
