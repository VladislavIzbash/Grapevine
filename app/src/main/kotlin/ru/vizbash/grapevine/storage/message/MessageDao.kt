package ru.vizbash.grapevine.storage.message

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MessageDao {
    @Query("SELECT * FROM Message WHERE id = :id")
    suspend fun getById(id: Long): Message?

    @Query("SELECT * FROM Message ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getAllWithState(state: Message.State, limit: Int): List<Message>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: Message)

    @Query("UPDATE Message SET state = :newState WHERE id = :id")
    suspend fun changeState(id: Long, newState: Message.State)
}