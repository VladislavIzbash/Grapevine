package ru.vizbash.grapevine.storage.profile

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import ru.vizbash.grapevine.storage.Converters

@Database(
    version = 1,
    exportSchema = false,
    entities = [ProfileEntity::class],
)
@TypeConverters(Converters::class)
abstract class ProfileDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
}