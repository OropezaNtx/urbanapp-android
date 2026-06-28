package com.oropeza.urbanapp.core.identity

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import android.util.Log

/**
 * Manages the authenticated identity of the device using Firebase Anonymous Authentication.
 * This UID (ownerUid) is used for security rules and ownership of Firestore documents.
 */
object UrbanIdentityManager {
    private const val TAG = "UrbanIdentityManager"
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()

    /**
     * Ensures the device is authenticated. If no session exists, it performs an anonymous sign-in.
     */
    suspend fun ensureAuthenticated(): Result<String> {
        return try {
            val currentUser = auth.currentUser
            if (currentUser != null) {
                Log.d(TAG, "Already authenticated as ${currentUser.uid}")
                Result.success(currentUser.uid)
            } else {
                Log.d(TAG, "Starting anonymous sign-in...")
                val result = auth.signInAnonymously().await()
                val uid = result.user?.uid ?: throw IllegalStateException("Firebase UID is null after login")
                Log.d(TAG, "Successfully signed in anonymously as $uid")
                Result.success(uid)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to authenticate device", e)
            Result.failure(e)
        }
    }

    /**
     * Returns the current UID or null if not authenticated.
     */
    fun getUid(): String? {
        return auth.currentUser?.uid
    }

    /**
     * Checks if the device is currently authenticated.
     */
    fun isAuthenticated(): Boolean {
        return auth.currentUser != null
    }
}
