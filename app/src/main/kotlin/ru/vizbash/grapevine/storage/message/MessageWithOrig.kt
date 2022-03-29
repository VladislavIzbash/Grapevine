package ru.vizbash.grapevine.storage.message

import androidx.room.Embedded
import androidx.room.Relation

data class MessageWithOrig(
    @Embedded val msg: Message,
    @Relation(
        parentColumn = "origMsgId",
        entityColumn = "id",
    )
    val orig: Message?,
)