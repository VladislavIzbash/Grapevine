package ru.vizbash.grapevine.storage.chats

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "chat_members",
    primaryKeys = ["chat_id", "node_id"],
    foreignKeys = [ForeignKey(
        entity = ChatEntity::class,
        parentColumns = ["id"],
        childColumns = ["chat_id"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class ChatMember(
    @ColumnInfo(name = "chat_id") val chatId: Long,
    @ColumnInfo(name = "node_id") val nodeId: Long,
)
