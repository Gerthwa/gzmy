package com.gzmy.app.data.model

import com.google.firebase.Timestamp

data class UserProfile(
    val uid: String = "",
    val displayName: String = "",
    val bio: String = "",
    val photoUrl: String = "",
    val discoverable: Boolean = true,
    val inCouple: Boolean = false,
    val createdAt: Timestamp = Timestamp.now(),
    val updatedAt: Timestamp = Timestamp.now()
)

data class SwipeAction(
    val fromUid: String = "",
    val toUid: String = "",
    val direction: String = "pass",
    val createdAt: Timestamp = Timestamp.now()
)

data class MatchRecord(
    val userIds: List<String> = emptyList(),
    val createdAt: Timestamp = Timestamp.now()
)
