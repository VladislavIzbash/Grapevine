package ru.vizbash.grapevine.storage.message

import androidx.room.*
import ru.vizbash.grapevine.storage.chat.Chat
import java.util.*

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = Chat::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Message::class,
            parentColumns = ["id"],
            childColumns = ["origMsgId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["origMsgId"]),
        Index(value = ["chatId"]),
    ],
)
data class Message(
    @PrimaryKey val id: Long,
    val timestamp: Date,
    val chatId: Long,
    val senderId: Long,
    val text: String,
    val origMsgId: Long?,
    @Embedded(prefix = "file") val file: MessageFile?,
    val state: State,
) {
    enum class State { SENT, DELIVERED, READ, DELIVERY_FAILED }
}
