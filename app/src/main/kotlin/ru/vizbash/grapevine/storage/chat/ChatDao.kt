package ru.vizbash.grapevine.storage.chat

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.*

@Dao
interface ChatDao {
    @Query("SELECT * FROM Chat ORDER BY updateTime DESC")
    fun observeAll(): Flow<List<Chat>>

    @Query("SELECT * FROM Chat WHERE id = :id")
    suspend fun getById(id: Long): Chat?

    @Query("SELECT nodeId FROM GroupChatMember WHERE chatId = :id")
    suspend fun getGroupChatMembers(id: Long): List<Long>

    @Query("UPDATE Chat SET updateTime = :date WHERE id = :id")
    suspend fun setUpdateTIme(id: Long, date: Date)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(chat: Chat)

    @Update
    suspend fun update(chat: Chat)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertChatMembers(chatMembers: List<GroupChatMember>)

    @Delete
    suspend fun delete(chat: Chat)
}