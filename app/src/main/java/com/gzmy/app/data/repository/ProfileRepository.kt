package com.gzmy.app.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.gzmy.app.data.model.UserProfile
import kotlinx.coroutines.tasks.await

class ProfileRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth get() = FirebaseAuth.getInstance()

    suspend fun ensureProfile(displayNameFallback: String = ""): UserProfile {
        val uid = requireUid()
        val docRef = db.collection("profiles").document(uid)
        val existing = docRef.get().await()
        if (existing.exists()) {
            return existing.toObject(UserProfile::class.java)?.copy(uid = uid) ?: UserProfile(uid = uid)
        }

        val profile = UserProfile(
            uid = uid,
            displayName = displayNameFallback,
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )
        docRef.set(profile).await()
        return profile
    }

    suspend fun getMyProfile(): UserProfile {
        val uid = requireUid()
        val doc = db.collection("profiles").document(uid).get().await()
        return doc.toObject(UserProfile::class.java)?.copy(uid = uid) ?: UserProfile(uid = uid)
    }

    suspend fun updateProfile(
        displayName: String,
        bio: String,
        photoUrl: String
    ) {
        val uid = requireUid()
        db.collection("profiles").document(uid).set(
            mapOf(
                "uid" to uid,
                "displayName" to displayName,
                "bio" to bio,
                "photoUrl" to photoUrl,
                "updatedAt" to Timestamp.now()
            ),
            SetOptions.merge()
        ).await()
    }

    suspend fun setDiscoverable(discoverable: Boolean) {
        val uid = requireUid()
        db.collection("profiles").document(uid).set(
            mapOf(
                "uid" to uid,
                "discoverable" to discoverable,
                "updatedAt" to Timestamp.now()
            ),
            SetOptions.merge()
        ).await()
    }

    suspend fun setInCouple(inCouple: Boolean) {
        val uid = requireUid()
        db.collection("profiles").document(uid).set(
            mapOf(
                "uid" to uid,
                "inCouple" to inCouple,
                "updatedAt" to Timestamp.now()
            ),
            SetOptions.merge()
        ).await()
    }

    private fun requireUid(): String {
        return auth.currentUser?.uid ?: throw IllegalStateException("User not authenticated")
    }
}
