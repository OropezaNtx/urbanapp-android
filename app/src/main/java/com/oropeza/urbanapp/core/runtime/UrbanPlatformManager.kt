package com.oropeza.urbanapp.core.runtime

import android.content.Context
import com.oropeza.urbanapp.core.platform.UrbanCloudPaths
import com.oropeza.urbanapp.core.platform.UrbanPlatformSettings

object UrbanPlatformManager {
    fun getOrganizationId(context: Context): String {
        return UrbanPlatformSettings.getOrganizationId(context)
    }

    fun getProjectId(context: Context): String {
        return UrbanPlatformSettings.getProjectId(context)
    }

    fun getEnvironment(context: Context): String {
        return UrbanPlatformSettings.getEnvironment(context)
    }

    fun getCloudPaths(): UrbanCloudPaths {
        return UrbanCloudPaths
    }
}
