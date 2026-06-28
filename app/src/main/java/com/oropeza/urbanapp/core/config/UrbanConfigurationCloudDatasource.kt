package com.oropeza.urbanapp.core.config

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.oropeza.urbanapp.core.platform.UrbanCloudPaths
import com.oropeza.urbanapp.core.platform.UrbanWorkspace
import kotlinx.coroutines.tasks.await

class UrbanConfigurationCloudDatasource {

    private val db = FirebaseFirestore.getInstance()

    private companion object {
        const val TAG = "UrbanConfigCloud"
    }

    suspend fun fetchConfiguration(workspace: UrbanWorkspace): UrbanConfiguration? {
        val path = UrbanCloudPaths.configurationPath(
            workspace.organization.workspaceId,
            workspace.project.projectId
        )

        return try {
            val snapshot = db.document(path).get().await()
            if (snapshot.exists()) {
                val data = snapshot.data
                if (data != null) {
                    UrbanConfigurationCloudMapper.fromMap(data)
                } else null
            } else {
                Log.d(TAG, "Configuration document does not exist at $path")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch cloud configuration from $path: ${e.message}")
            null
        }
    }
}
