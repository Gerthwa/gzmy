package com.gzmy.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.gzmy.app.data.model.Message

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val coupleCode: String,
    val senderId: String,
    val senderName: String,
    val type: String,
    val content: String,
    val vibrationPattern: String,
    val timestamp: Long,
    val isRead: Boolean = false,
    val isSynced: Boolean = false
) {
    /** Room entity -> domain model */
    fun toMessage(): Message = Message(
        id = id,
        coupleCode = coupleCode,
        senderId = senderId,
        senderName = senderName,
        type = try { Message.MessageType.valueOf(type) } catch (_: Exception) { Message.MessageType.NOTE },
        content = content,
        vibrationPattern = try { Message.VibrationPattern.valueOf(vibrationPattern) } catch (_: Exception) { Message.VibrationPattern.GENTLE },
        timestamp = com.google.firebase.Timestamp(timestamp / 1000, ((timestamp % 1000) * 1_000_000).toInt()),
        isRead = isRead
    )

    companion object {
        /** Domain model -> Room entity */
        fun from(msg: Message, synced: Boolean = true): MessageEntity = MessageEntity(
            id = msg.id,
            coupleCode = msg.coupleCode,
            senderId = msg.senderId,
            senderName = msg.senderName,
            type = msg.type.name,
            content = msg.content,
            vibrationPattern = msg.vibrationPattern.name,
            timestamp = msg.timestamp.toDate().time,
            isRead = msg.isRead,
            isSynced = synced
        )
    }
}
