package ru.vizbash.grapevine.storage.messages

import androidx.room.ColumnInfo
import androidx.room.DatabaseView
import androidx.room.Embedded

@DatabaseView(
    "SELECT m.*, c.username FROM messages m " +
        "LEFT JOIN contacts c ON m.sender_id = c.node_id"
)
data class MessageWithUsername(
    @Embedded val msg: MessageEntity,
    @ColumnInfo(name = "username") val username: String?,
)