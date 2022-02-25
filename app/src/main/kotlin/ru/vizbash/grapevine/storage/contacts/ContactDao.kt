package ru.vizbash.grapevine.storage.contacts

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts")
    fun getAll(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE node_id = :nodeId")
    suspend fun getById(nodeId: Long): ContactEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(contact: ContactEntity)

    @Delete
    suspend fun delete(contact: ContactEntity)

    @Update
    suspend fun update(contact: ContactEntity)
}