package ru.vizbash.grapevine.storage

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import ru.vizbash.grapevine.storage.chats.ChatDao
import ru.vizbash.grapevine.storage.chats.ChatEntity
import ru.vizbash.grapevine.storage.chats.ChatMember
import ru.vizbash.grapevine.storage.contacts.ContactDao
import ru.vizbash.grapevine.storage.contacts.ContactEntity
import ru.vizbash.grapevine.storage.messages.MessageDao
import ru.vizbash.grapevine.storage.messages.MessageEntity
import ru.vizbash.grapevine.storage.messages.MessageWithUsername

@Database(
    version = 1,
    exportSchema = false,
    entities = [
        ContactEntity::class,
        ChatEntity::class,
        ChatMember::class,
        MessageEntity::class,
    ],
    views = [
        MessageWithUsername::class,
    ],
)
@TypeConverters(Converters::class)
abstract class UserDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao

    abstract fun chatDao(): ChatDao

    abstract fun messageDao(): MessageDao
}