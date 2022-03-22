package ru.vizbash.grapevine.storage.chat

import android.graphics.Bitmap
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Chat(
    @PrimaryKey val id: Long,
    val name: String,
    val photo: Bitmap?,
    val isGroup: Boolean,
    val ownerId: Long?,
)