package com.gzmy.app.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.gzmy.app.data.model.Message
import com.gzmy.app.data.model.Couple
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.channels.awaitClose

class MessageRepository {
    private val db = FirebaseFirestore.getInstance()
    private val messagesCollection = db.collection("messages")
    private val couplesCollection = db.collection("couples")
    
    // Real-time mesajları dinle
    fun listenToMessages(coupleCode: String): Flow<List<Message>> = callbackFlow {
        val subscription = messagesCollection
            .whereEqualTo("coupleCode", coupleCode)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                val messages = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Message::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                
                trySend(messages)
            }
        
        awaitClose { subscription.remove() }
    }
    
    // Titreşim gönder (app kapalıyken de FCM ile gider)
    suspend fun sendVibration(
        coupleCode: String,
        senderId: String,
        pattern: Message.VibrationPattern
    ): Result<Unit> = try {
        val message = Message(
            coupleCode = coupleCode,
            senderId = senderId,
            type = Message.MessageType.VIBRATION,
            vibrationPattern = pattern,
            content = "💓 Titreşim gönderdi"
        )
        
        messagesCollection.add(message).await()
        updateLastActivity(coupleCode)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
    
    // Not gönder (app kapalıyken de FCM ile gider)
    suspend fun sendNote(
        coupleCode: String,
        senderId: String,
        senderName: String,
        content: String
    ): Result<Unit> = try {
        val message = Message(
            coupleCode = coupleCode,
            senderId = senderId,
            senderName = senderName,
            type = Message.MessageType.NOTE,
            content = content
        )
        
        messagesCollection.add(message).await()
        updateLastActivity(coupleCode)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
    
    // Kalp atışı gönder (app kapalıyken de FCM ile gider)
    suspend fun sendHeartbeat(
        coupleCode: String,
        senderId: String
    ): Result<Unit> = try {
        val message = Message(
            coupleCode = coupleCode,
            senderId = senderId,
            type = Message.MessageType.HEARTBEAT,
            content = "💗 Kalp atışı gönderdi",
            vibrationPattern = Message.VibrationPattern.HEARTBEAT
        )
        
        messagesCollection.add(message).await()
        updateLastActivity(coupleCode)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
    
    // Mesajı okundu olarak işaretle
    suspend fun markAsRead(messageId: String) {
        messagesCollection.document(messageId)
            .update("isRead", true)
            .await()
    }
    
    private suspend fun updateLastActivity(coupleCode: String) {
        couplesCollection.document(coupleCode)
            .update("lastActivity", com.google.firebase.Timestamp.now())
            .await()
    }
}

class CoupleRepository {
    private val db = FirebaseFirestore.getInstance()
    private val couplesCollection = db.collection("couples")
    
    // Yeni çift oluştur
    suspend fun createCouple(code: String, partner1Id: String, partner1Name: String): Result<Couple> = try {
        val couple = Couple(
            code = code,
            partner1Id = partner1Id,
            partner1Name = partner1Name
        )
        
        couplesCollection.document(code).set(couple).await()
        Result.success(couple)
    } catch (e: Exception) {
        Result.failure(e)
    }
    
    // Çifte katıl
    suspend fun joinCouple(code: String, partner2Id: String, partner2Name: String): Result<Couple> = try {
        val doc = couplesCollection.document(code).get().await()
        
        if (!doc.exists()) {
            return Result.failure(Exception("Çift bulunamadı"))
        }
        
        val couple = doc.toObject(Couple::class.java)
            ?: return Result.failure(Exception("Veri hatası"))
        
        if (couple.partner2Id.isNotEmpty()) {
            return Result.failure(Exception("Bu çift zaten dolu"))
        }
        
        couplesCollection.document(code).update(
            "partner2Id", partner2Id,
            "partner2Name", partner2Name
        ).await()
        
        Result.success(couple.copy(partner2Id = partner2Id, partner2Name = partner2Name))
    } catch (e: Exception) {
        Result.failure(e)
    }
    
    // Çift bilgilerini getir
    suspend fun getCouple(code: String): Result<Couple> = try {
        val doc = couplesCollection.document(code).get().await()
        
        if (!doc.exists()) {
            return Result.failure(Exception("Çift bulunamadı"))
        }
        
        val couple = doc.toObject(Couple::class.java)
            ?: return Result.failure(Exception("Veri hatası"))
        
        Result.success(couple)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
