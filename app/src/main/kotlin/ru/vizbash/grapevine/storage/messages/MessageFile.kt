package ru.vizbash.grapevine.storage.messages

import androidx.room.ColumnInfo

data class MessageFile(
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "size") val size: Int,
    @ColumnInfo(name = "is_downloaded") val downloaded: Boolean,
)
