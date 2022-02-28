package ru.vizbash.grapevine.storage.messages

import androidx.room.*
import java.util.*

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
    @ColumnInfo(name = "timestamp") val timestamp: Date,
    @ColumnInfo(name = "chat_id") val chatId: Long,
    @ColumnInfo(name = "sender_id") val senderId: Long,
    @ColumnInfo(name = "text") val text: String,
    @ColumnInfo(name = "orig_msg_id") val originalMessageId: Long?,
    @ColumnInfo(name = "state") val state: State,
    @Embedded(prefix = "file_") val file: MessageFile?,
) {
    enum class State { SENT, DELIVERED, READ, DELIVERY_FAILED }
}