package com.oropeza.urbanapp.core.platform

import android.content.Context

object UrbanPlatformSettings {

    private const val PREFS_NAME = "urban_platform_settings"
    private const val KEY_ORG_ID = "organization_id"
    private const val KEY_PROJECT_ID = "project_id"
    private const val KEY_LICENSE_ID = "license_id"
    private const val KEY_ENVIRONMENT = "environment"

    fun getOrganizationId(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ORG_ID, "demo_org") ?: "demo_org"
    }

    fun getProjectId(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PROJECT_ID, "demo_project") ?: "demo_project"
    }

    fun getLicenseId(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LICENSE_ID, null)
    }

    fun getEnvironment(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ENVIRONMENT, "pilot") ?: "pilot"
    }

    fun saveSettings(context: Context, orgId: String, projectId: String, env: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_ORG_ID, orgId)
            .putString(KEY_PROJECT_ID, projectId)
            .putString(KEY_ENVIRONMENT, env)
            .apply()
    }
}
