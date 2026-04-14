package com.gzmy.app.data

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

object AuthSessionManager {
    private const val TAG = "AuthSessionManager"
    private const val PREFS_NAME = "gzmy_prefs"
    private const val KEY_AUTH_UID = "auth_uid"

    suspend fun ensureSignedIn(context: Context): String {
        val auth = FirebaseAuth.getInstance()
        val existing = auth.currentUser?.uid
        if (!existing.isNullOrEmpty()) {
            cacheAuthUid(context, existing)
            return existing
        }

        val result = auth.signInAnonymously().await()
        val uid = result.user?.uid ?: throw IllegalStateException("Firebase auth uid is null")
        cacheAuthUid(context, uid)
        Log.d(TAG, "Anonymous auth initialized: ${uid.take(8)}...")
        return uid
    }

    fun getCachedAuthUid(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_AUTH_UID, null)
    }

    fun cacheAuthUid(context: Context, uid: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_AUTH_UID, uid)
            .apply()
    }
}
