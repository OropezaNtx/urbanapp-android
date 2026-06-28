package com.oropeza.urbanapp.core.runtime

import android.content.Context
import com.oropeza.urbanapp.core.platform.UrbanCloudPaths

object UrbanPlatformManager {
    fun getWorkspaceId(context: Context): String {
        return UrbanRuntime.workspace(context).organization.workspaceId
    }

    fun getProjectId(context: Context): String {
        return UrbanRuntime.workspace(context).project.projectId
    }

    fun getEnvironment(context: Context): String {
        return UrbanRuntime.workspace(context).environment
    }

    fun getCloudPaths(): UrbanCloudPaths {
        return UrbanCloudPaths
    }
}
