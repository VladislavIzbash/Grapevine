package ru.vizbash.grapevine.storage

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import ru.vizbash.grapevine.storage.contacts.ContactDao
import ru.vizbash.grapevine.storage.contacts.ContactEntity
import ru.vizbash.grapevine.storage.messages.MessageDao
import ru.vizbash.grapevine.storage.messages.MessageEntity

@Database(entities = [ContactEntity::class, MessageEntity::class], version = 1)
@TypeConverters(Converters::class)
abstract class UserDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao

    abstract fun messageDao(): MessageDao
}