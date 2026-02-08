package com.gzmy.app.data.repository

import android.content.Context
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.gzmy.app.data.local.AppDatabase
import com.gzmy.app.data.local.MessageEntity
import com.gzmy.app.data.model.Message
import com.gzmy.app.data.model.Couple
import com.gzmy.app.worker.SyncMessagesWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Offline-First MessageRepository
 *
 * Send flow:
 *   1. Insert into Room (isSynced=false) → UI sees it instantly
 *   2. Try Firestore write
 *      - success → mark isSynced=true
 *      - failure → stays in Room, SyncMessagesWorker will retry
 *
 * Receive flow:
 *   - Room Flow feeds the UI (always has data, even offline)
 *   - Firestore snapshotListener writes into Room (remote → local sync)
 */
class MessageRepository(private val context: Context) {

    private val db = FirebaseFirestore.getInstance()
    private val dao = AppDatabase.getInstance(context).messageDao()
    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        private const val TAG = "MessageRepo"
    }

    // ─── READ ───

    /** All messages for a couple (Room Flow → reactive UI) */
    fun getMessages(coupleCode: String): Flow<List<Message>> =
        dao.getMessagesByCoupleCode(coupleCode).map { entities ->
            entities.map { it.toMessage() }
        }

    /** Only CHAT messages (Room Flow) */
    fun getChatMessages(coupleCode: String): Flow<List<Message>> =
        dao.getChatMessages(coupleCode).map { entities ->
            entities.map { it.toMessage() }
        }

    // ─── WRITE (Offline-First) ───

    /** Send any message type: Room first, then Firestore */
    suspend fun sendMessage(
        coupleCode: String,
        senderId: String,
        senderName: String,
        type: Message.MessageType,
        content: String,
        vibrationPattern: Message.VibrationPattern = Message.VibrationPattern.GENTLE
    ) {
        val message = Message(
            id = UUID.randomUUID().toString(),
            coupleCode = coupleCode,
            senderId = senderId,
            senderName = senderName,
            type = type,
            content = content,
            vibrationPattern = vibrationPattern,
            timestamp = Timestamp.now()
        )

        // 1. Room'a kaydet (UI anında görür)
        val entity = MessageEntity.from(message, synced = false)
        dao.insert(entity)
        Log.d(TAG, "Saved to Room (unsynced): ${message.id}")

        // 2. Firestore'a gönder (aynı ID ile, duplikasyon önleme)
        try {
            db.collection("messages").document(message.id).set(message).await()
            dao.markAsSynced(message.id)
            Log.d(TAG, "Synced to Firestore: ${message.id}")

            // lastActivity güncelle
            db.collection("couples").document(coupleCode)
                .update("lastActivity", Timestamp.now())
        } catch (e: Exception) {
            Log.w(TAG, "Firestore write failed (will retry via worker): ${e.message}")
            // Room'da isSynced=false kalır, SyncMessagesWorker halledecek
            // Worker'ı tekrar enqueue et (internet gelince calisacak)
            SyncMessagesWorker.enqueue(context)
        }
    }

    // ─── REMOTE → LOCAL SYNC ───

    /**
     * Start Firestore snapshot listener that syncs remote messages into Room.
     * Call from fragment/activity lifecycle.
     */
    fun startRemoteSync(coupleCode: String): com.google.firebase.firestore.ListenerRegistration {
        return db.collection("messages")
            .whereEqualTo("coupleCode", coupleCode)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Remote sync error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                val remoteMessages = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Message::class.java)?.copy(id = doc.id)
                }

                // Batch insert into Room (REPLACE avoids duplicates)
                scope.launch {
                    val entities = remoteMessages.map { MessageEntity.from(it, synced = true) }
                    dao.insertAll(entities)
                    Log.d(TAG, "Remote sync: ${entities.size} messages → Room")
                }
            }
    }
}

/**
 * CoupleRepository — couple CRUD operations.
 */
class CoupleRepository {
    private val db = FirebaseFirestore.getInstance()
    private val couplesCollection = db.collection("couples")

    suspend fun createCouple(code: String, partner1Id: String, partner1Name: String): Result<Couple> {
        return try {
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
    }

    suspend fun joinCouple(code: String, partner2Id: String, partner2Name: String): Result<Couple> {
        val doc = couplesCollection.document(code).get().await()
        if (!doc.exists()) return Result.failure(Exception("Çift bulunamadı"))

        val couple = doc.toObject(Couple::class.java)
            ?: return Result.failure(Exception("Veri hatası"))

        if (couple.partner2Id.isNotEmpty()) return Result.failure(Exception("Bu çift zaten dolu"))

        couplesCollection.document(code).update(
            "partner2Id", partner2Id,
            "partner2Name", partner2Name
        ).await()

        return Result.success(couple.copy(partner2Id = partner2Id, partner2Name = partner2Name))
    }

    suspend fun getCouple(code: String): Result<Couple> {
        val doc = couplesCollection.document(code).get().await()
        if (!doc.exists()) return Result.failure(Exception("Çift bulunamadı"))
        val couple = doc.toObject(Couple::class.java)
            ?: return Result.failure(Exception("Veri hatası"))
        return Result.success(couple)
    }
}
