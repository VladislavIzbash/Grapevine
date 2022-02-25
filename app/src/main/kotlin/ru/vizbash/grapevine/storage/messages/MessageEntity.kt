package ru.vizbash.grapevine.storage.messages

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
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
    @ColumnInfo(name = "is_ingoing") val isIngoing: Boolean,
    @ColumnInfo(name = "text") val text: String,
    @ColumnInfo(name = "orig_msg_id") val originalMessageId: Long?,
    @ColumnInfo(name = "state") val state: State,
    @ColumnInfo(name = "has_file") val hasFile: Boolean,
    @ColumnInfo(name = "file_path") val filePath: String?,
) {
    enum class State { SENT, DELIVERED, READ, DELIVERY_FAILED }
}