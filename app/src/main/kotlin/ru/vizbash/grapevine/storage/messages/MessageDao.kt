package ru.vizbash.grapevine.storage.messages

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface MessageDao {
    @Transaction
    @Query("SELECT * FROM messages WHERE sender_id = :contactId")
    fun getAllForContact(contactId: Long): PagingSource<Int, MessageWithOrig>
}