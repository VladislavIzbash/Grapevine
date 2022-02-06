package ru.vizbash.grapevine.storage.profile

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles")
    fun getAll(): Flow<List<Profile>>

    @Insert
    suspend fun insert(profile: Profile)
}