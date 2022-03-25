package ru.vizbash.grapevine.storage

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import ru.vizbash.grapevine.storage.chat.Chat
import ru.vizbash.grapevine.storage.chat.ChatDao
import ru.vizbash.grapevine.storage.chat.GroupChatMember
import ru.vizbash.grapevine.storage.message.Message
import ru.vizbash.grapevine.storage.message.MessageDao
import ru.vizbash.grapevine.storage.node.KnownNode
import ru.vizbash.grapevine.storage.node.NodeDao

@Database(
    version = 1,
    exportSchema = false,
    entities = [
        Chat::class,
        GroupChatMember::class,
        Message::class,
        KnownNode::class,
    ],
)
@TypeConverters(Converters::class)
abstract class ProfileDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    abstract fun messageDao(): MessageDao

    abstract fun nodeDao(): NodeDao
}