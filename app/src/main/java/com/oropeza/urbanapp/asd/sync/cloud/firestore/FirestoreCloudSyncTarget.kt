package com.oropeza.urbanapp.asd.sync.cloud.firestore

import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.oropeza.urbanapp.asd.AsdGraph
import com.oropeza.urbanapp.asd.sync.cloud.CloudSyncItem
import com.oropeza.urbanapp.asd.sync.cloud.CloudSyncResult
import com.oropeza.urbanapp.asd.sync.cloud.CloudSyncTarget
import com.oropeza.urbanapp.core.runtime.UrbanRuntime
import kotlinx.coroutines.tasks.await

class FirestoreCloudSyncTarget : CloudSyncTarget {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val gson = Gson()

    private suspend fun ensureAuthenticated() {
        if (auth.currentUser == null) {
            Log.i(PERMISSION_TAG, "FIRESTORE_AUTH_START mode=ANONYMOUS")
            auth.signInAnonymously().await()
        }
    }

    private val mapType = object : TypeToken<Map<String, Any?>>() {}.type

    private companion object {
        const val TAG = "UrbanCloudSync"
        const val PERMISSION_TAG = "FirestorePermission"
    }

    private fun logPermissionContext(operation: String, item: CloudSyncItem, outcome: String? = null) {
        val user = auth.currentUser
        val context = AsdGraph.appContext
        val workspace = UrbanRuntime.workspace(context)
        val firebaseProjectId = runCatching { FirebaseApp.getInstance().options.projectId }.getOrNull()
        val suffix = outcome?.let { " outcome=$it" }.orEmpty()

        Log.i(
            PERMISSION_TAG,
            "FIRESTORE_PERMISSION_CONTEXT operation=$operation$suffix " +
                "uid=${user?.uid ?: "NONE"} anonymous=${user?.isAnonymous ?: false} " +
                "firebaseProject=${firebaseProjectId ?: "UNKNOWN"} " +
                "workspace=${workspace.organization.workspaceId} project=${workspace.project.projectId} " +
                "entityType=${item.entityType} queueId=${item.queueId} cloudPath=${item.cloudPath}"
        )
    }

    override suspend fun upsert(item: CloudSyncItem): CloudSyncResult {
        val path = item.cloudPath
        if (path.isNullOrBlank()) {
            return CloudSyncResult.PermanentFailure("cloudPath missing for entity ${item.entityType} ${item.entityLocalId}")
        }

        return try {
            ensureAuthenticated()
            logPermissionContext("UPSERT", item, "ATTEMPT")
            val payloadMap: Map<String, Any?> = gson.fromJson(item.payloadJson, mapType)

            db.document(path)
                .set(payloadMap, SetOptions.merge())
                .await()

            logPermissionContext("UPSERT", item, "SUCCESS")
            CloudSyncResult.Success
        } catch (e: FirebaseFirestoreException) {
            Log.e(
                PERMISSION_TAG,
                "FIRESTORE_PERMISSION_DENIED operation=UPSERT code=${e.code} " +
                    "uid=${auth.currentUser?.uid ?: "NONE"} anonymous=${auth.currentUser?.isAnonymous ?: false} " +
                    "cloudPath=$path message=${e.message}"
            )
            handleFirestoreException(e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during upsert for $path", e)
            CloudSyncResult.RetryableFailure(e.message ?: "Unknown upsert error")
        }
    }

    override suspend fun delete(item: CloudSyncItem): CloudSyncResult {
        val path = item.cloudPath
        if (path.isNullOrBlank()) {
            return CloudSyncResult.PermanentFailure("cloudPath missing for deletion")
        }

        return try {
            ensureAuthenticated()
            logPermissionContext("DELETE", item, "ATTEMPT")
            db.document(path)
                .delete()
                .await()

            logPermissionContext("DELETE", item, "SUCCESS")
            CloudSyncResult.Success
        } catch (e: FirebaseFirestoreException) {
            Log.e(
                PERMISSION_TAG,
                "FIRESTORE_PERMISSION_DENIED operation=DELETE code=${e.code} " +
                    "uid=${auth.currentUser?.uid ?: "NONE"} anonymous=${auth.currentUser?.isAnonymous ?: false} " +
                    "cloudPath=$path message=${e.message}"
            )
            handleFirestoreException(e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during delete for $path", e)
            CloudSyncResult.RetryableFailure(e.message ?: "Unknown delete error")
        }
    }

    private fun handleFirestoreException(e: FirebaseFirestoreException): CloudSyncResult {
        return when (e.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                CloudSyncResult.PermanentFailure("Permission denied: ${e.message}")
            FirebaseFirestoreException.Code.INVALID_ARGUMENT ->
                CloudSyncResult.PermanentFailure("Invalid argument: ${e.message}")
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
            FirebaseFirestoreException.Code.ABORTED ->
                CloudSyncResult.RetryableFailure("Transient error (${e.code}): ${e.message}")
            else ->
                CloudSyncResult.RetryableFailure("Firestore error (${e.code}): ${e.message}")
        }
    }
}
