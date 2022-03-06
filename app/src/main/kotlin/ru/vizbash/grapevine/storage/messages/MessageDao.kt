package ru.vizbash.grapevine.storage.messages

import androidx.paging.PagingSource
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages")
    fun getAll(): Flow<List<MessageEntity>>

    @Transaction
    @Query("SELECT * FROM messages WHERE chat_id = :chatId ORDER BY timestamp DESC")
    fun getAllForChat(chatId: Long): PagingSource<Int, MessageWithOrig>

    @Query("SELECT * FROM messages WHERE chat_id = :chatId ORDER BY timestamp DESC LIMIT 1")
    fun getLastMessage(chatId: Long): Flow<MessageEntity?>

    @Query("SELECT * FROM messages WHERE chat_id = :chatId AND state = :state ORDER BY timestamp")
    suspend fun getAllForChatWithState(chatId: Long, state: MessageEntity.State): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: MessageEntity)

    @Query("UPDATE messages SET state = :newState WHERE id = :msgId")
    suspend fun setState(msgId: Long, newState: MessageEntity.State)
}