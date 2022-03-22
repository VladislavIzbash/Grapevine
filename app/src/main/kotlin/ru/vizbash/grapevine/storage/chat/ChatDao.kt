package ru.vizbash.grapevine.storage.chat

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM Chat")
    fun observeAll(): Flow<List<Chat>>

    @Query("SELECT * FROM Chat WHERE id = :id")
    suspend fun getById(id: Long): Chat?

    @Query("SELECT nodeId FROM GroupChatMember WHERE chatId = :id")
    suspend fun getGroupChatMembers(id: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(chat: Chat)

    @Update
    suspend fun update(chat: Chat)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertChatMembers(chatMembers: List<GroupChatMember>)

    @Delete
    suspend fun delete(chat: Chat)
}