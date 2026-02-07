package com.gzmy.app.data.model

import com.google.firebase.Timestamp

data class Message(
    val id: String = "",
    val coupleCode: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val type: MessageType = MessageType.NOTE,
    val content: String = "",
    val vibrationPattern: VibrationPattern = VibrationPattern.GENTLE,
    val timestamp: Timestamp = Timestamp.now(),
    val isRead: Boolean = false
) {
    enum class MessageType {
        VIBRATION,
        NOTE,
        HEARTBEAT,
        CHAT
    }

    enum class VibrationPattern {
        GENTLE,
        HEARTBEAT,
        INTENSE
    }
}

data class Couple(
    val code: String = "",
    val partner1Id: String = "",
    val partner1Name: String = "",
    val partner2Id: String = "",
    val partner2Name: String = "",
    val missYouLevel: Map<String, Int> = emptyMap(),
    val createdAt: Timestamp = Timestamp.now(),
    val lastActivity: Timestamp = Timestamp.now()
)

data class Partner(
    val id: String = "",
    val name: String = "",
    val fcmToken: String = "",
    val isOnline: Boolean = false,
    val lastSeen: Timestamp = Timestamp.now()
)
