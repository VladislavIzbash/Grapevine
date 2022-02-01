package ru.vizbash.grapevine.db.identity

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface IdentityDao {
    @Query("SELECT * FROM identities")
    fun getAll(): List<Identity>

    @Insert
    fun insert(identity: Identity)
}