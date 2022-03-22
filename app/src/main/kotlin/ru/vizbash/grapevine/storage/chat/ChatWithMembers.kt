package ru.vizbash.grapevine.storage.chat

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import ru.vizbash.grapevine.storage.node.KnownNode

data class ChatWithMembers(
    @Embedded val chat: Chat,
    @Relation(
        parentColumn = "chatId",
        entityColumn = "nodeId",
        associateBy = Junction(GroupChatMember::class),
    )
    val members: List<KnownNode>,
)
