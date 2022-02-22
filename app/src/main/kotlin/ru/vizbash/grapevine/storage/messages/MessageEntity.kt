package ru.vizbash.grapevine.storage.messages

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(
        entity = MessageEntity::class,
        parentColumns = ["id"],
        childColumns = ["orig_msg_id"],
        onDelete = ForeignKey.NO_ACTION,
    )],
)
data class MessageEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "sender_id") val senderId: Long,
    @ColumnInfo(name = "chat_id") val chatId: Long?,
    @ColumnInfo(name = "text") val text: String,
    @ColumnInfo(name = "orig_msg_id") val originalMessageId: Long?,
    @ColumnInfo(name = "is_unread") val unread: Boolean,
    @ColumnInfo(name = "is_delivered") val delivered: Boolean,
    @ColumnInfo(name = "is_deliver_failed") val deliver_failed: Boolean,
    @ColumnInfo(name = "has_file") val hasFile: Boolean,
    @ColumnInfo(name = "file_path") val filePath: String?,
)