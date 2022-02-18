package ru.vizbash.grapevine.storage.contacts

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts")
    fun getAll(): Flow<List<ContactEntity>>

    @Insert
    suspend fun insert(contact: ContactEntity)
}