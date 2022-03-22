package ru.vizbash.grapevine.storage.message

import android.net.Uri

data class MessageFile(
    val uri: Uri?,
    val name: String,
    val size: Int,
    val isDownloaded: Boolean,
)
