package ru.vizbash.grapevine.db.identity

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import ru.vizbash.grapevine.db.Converters

@Database(entities = [Identity::class], version = 1)
@TypeConverters(Converters::class)
abstract class IdentityDatabase : RoomDatabase() {
    abstract fun identityDao(): IdentityDao
}