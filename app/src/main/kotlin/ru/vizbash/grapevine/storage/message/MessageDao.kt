package ru.vizbash.grapevine.storage.message

import androidx.paging.PagingSource
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM Message WHERE id = :id")
    suspend fun getById(id: Long): Message?

    @Query("SELECT * FROM Message WHERE chatId = :chatId AND state = :state ORDER BY timestamp DESC")
    suspend fun getFromChatWithState(chatId: Long, state: Message.State): List<Message>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: Message)

    @Query("UPDATE Message SET state = :newState WHERE id = :id")
    suspend fun setState(id: Long, newState: Message.State)

    @Query("SELECT * FROM Message WHERE fullyDelivered = 0 ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getUndeliveredLimit(limit: Int): List<Message>

    @Query("UPDATE Message SET fullyDelivered = 1 WHERE id = :id")
    suspend fun setFullyDelivered(id: Long)

    @Transaction
    @Query("SELECT * FROM Message WHERE chatId = :chatId ORDER BY timestamp DESC LIMIT 1")
    fun observeLastMessage(chatId: Long): Flow<MessageWithSender?>

    @Transaction
    @Query("SELECT * FROM Message WHERE chatId = :chatId ORDER BY timestamp DESC")
    fun pageMessagesFromChat(chatId: Long): PagingSource<Int, MessageWithOrig>
}