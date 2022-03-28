package ru.vizbash.grapevine.storage.message

import androidx.room.Embedded
import androidx.room.Relation
import ru.vizbash.grapevine.storage.node.KnownNode

data class MessageWithSender(
    @Embedded val msg: Message,
    @Relation(
        parentColumn = "senderId",
        entityColumn = "id",
    )
    val sender: KnownNode,
)