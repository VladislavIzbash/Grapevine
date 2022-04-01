package ru.vizbash.grapevine.storage.message

import androidx.paging.PagingSource
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM Message WHERE id = :id")
    suspend fun getById(id: Long): Message?

    @Query("SELECT * FROM Message WHERE state = :state ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getAllWithStateLimit(state: Message.State, limit: Int): List<Message>

    @Query("SELECT * FROM Message WHERE chatId = :chatId AND state = :state ORDER BY timestamp DESC")
    suspend fun getAllFromChatWithState(chatId: Long, state: Message.State): List<Message>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: Message)

    @Query("UPDATE Message SET state = :newState WHERE id = :id")
    suspend fun setState(id: Long, newState: Message.State)

    @Transaction
    @Query("SELECT * FROM Message WHERE chatId = :chatId ORDER BY timestamp DESC LIMIT 1")
    fun observeLastMessage(chatId: Long): Flow<MessageWithSender?>

    @Transaction
    @Query("SELECT * FROM Message WHERE chatId = :chatId ORDER BY timestamp DESC")
    fun pageMessagesFromChat(chatId: Long): PagingSource<Int, MessageWithOrig>
}