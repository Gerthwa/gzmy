package com.gzmy.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)

    @Query("SELECT * FROM messages WHERE coupleCode = :coupleCode ORDER BY timestamp ASC")
    fun getMessagesByCoupleCode(coupleCode: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE coupleCode = :coupleCode AND type = 'CHAT' ORDER BY timestamp ASC")
    fun getChatMessages(coupleCode: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE isSynced = 0")
    suspend fun getUnsyncedMessages(): List<MessageEntity>

    @Query("UPDATE messages SET isSynced = 1 WHERE id = :messageId")
    suspend fun markAsSynced(messageId: String)

    @Query("DELETE FROM messages WHERE coupleCode = :coupleCode")
    suspend fun deleteAllForCouple(coupleCode: String)
}
