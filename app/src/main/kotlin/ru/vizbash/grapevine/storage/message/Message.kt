package ru.vizbash.grapevine.storage.message

import androidx.room.*
import java.util.*

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = Message::class,
            parentColumns = ["id"],
            childColumns = ["orig_msg_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["orig_msg_id"]),
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
