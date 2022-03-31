package ru.vizbash.grapevine.storage.chat

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import ru.vizbash.grapevine.storage.node.KnownNode

data class ChatWithMembers(
    @Embedded val chat: Chat,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = GroupChatMember::class,
            parentColumn = "chatId",
            entityColumn = "nodeId",
        ),
    )
    val members: List<KnownNode>,
)
