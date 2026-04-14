package com.gzmy.app.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.gzmy.app.data.model.UserProfile
import kotlinx.coroutines.tasks.await

class DiscoverRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth get() = FirebaseAuth.getInstance()

    suspend fun fetchCandidates(limit: Long = 40): List<UserProfile> {
        val uid = requireUid()
        val swipedIds = getSwipedIds(uid)
        val snapshot = db.collection("profiles")
            .whereEqualTo("discoverable", true)
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .limit(limit)
            .get()
            .await()

        return snapshot.documents
            .mapNotNull { it.toObject(UserProfile::class.java)?.copy(uid = it.id) }
            .filter { it.uid != uid && !swipedIds.contains(it.uid) }
    }

    suspend fun swipe(toUid: String, direction: String) {
        val fromUid = requireUid()
        val swipeDocId = "${fromUid}_$toUid"
        db.collection("swipes").document(swipeDocId).set(
            mapOf(
                "fromUid" to fromUid,
                "toUid" to toUid,
                "direction" to direction,
                "createdAt" to Timestamp.now()
            )
        ).await()
    }

    private suspend fun getSwipedIds(uid: String): Set<String> {
        val snapshot = db.collection("swipes")
            .whereEqualTo("fromUid", uid)
            .get()
            .await()
        return snapshot.documents.mapNotNull { it.getString("toUid") }.toSet()
    }

    private fun requireUid(): String {
        return auth.currentUser?.uid ?: throw IllegalStateException("User not authenticated")
    }
}
