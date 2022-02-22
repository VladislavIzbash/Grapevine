package ru.vizbash.grapevine.storage.messages

import androidx.room.Embedded
import androidx.room.Relation

data class MessageWithOrig(
    @Embedded val msg: MessageEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "orig_msg_id",
    )
    val orig_msg: MessageEntity,
)