package ru.vizbash.grapevine.storage.node

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface NodeDao {
    @Query("SELECT * FROM KnownNode WHERE id = :id")
    suspend fun getById(id: Long): KnownNode?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(node: KnownNode)
}