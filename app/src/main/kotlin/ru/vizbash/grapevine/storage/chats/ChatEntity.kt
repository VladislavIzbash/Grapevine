package ru.vizbash.grapevine.storage.chats

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import javax.crypto.SecretKey

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "owner_id") val ownerId: Long,
)
