package ru.vizbash.grapevine.storage.messages

import androidx.room.Embedded
import androidx.room.Relation

data class MessageWithOrig(
    @Embedded val withUsername: MessageWithUsername,
    @Relation(
        parentColumn = "orig_msg_id",
        entityColumn = "id",
    )
    val origWithUsername: MessageWithUsername?,
)

