package ru.vizbash.grapevine.storage.messages

import android.net.Uri
import androidx.room.ColumnInfo

data class MessageFile(
    @ColumnInfo(name = "uri") val uri: Uri?,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "size") val size: Int,
    @ColumnInfo(name = "is_downloaded") val downloaded: Boolean,
)
