package ru.vizbash.grapevine.storage.chat

import androidx.room.Entity
import androidx.room.ForeignKey
import ru.vizbash.grapevine.storage.node.KnownNode

@Entity(
    primaryKeys = ["chatId", "nodeId"],
    foreignKeys = [
        ForeignKey(
            entity = Chat::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class GroupChatMember(
    val chatId: Long,
    val nodeId: Long,
)
