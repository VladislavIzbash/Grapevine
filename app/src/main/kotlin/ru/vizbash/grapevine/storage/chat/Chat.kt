package ru.vizbash.grapevine.storage.chat

import android.graphics.Bitmap
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.*

@Entity
data class Chat(
    @PrimaryKey val id: Long,
    val name: String,
    val photo: Bitmap?,
    val isGroup: Boolean,
    val ownerId: Long?,
    val updateTime: Date? = Date(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Chat

        if (id != other.id) return false
        if (name != other.name) return false
        if (isGroup != other.isGroup) return false
        if (ownerId != other.ownerId) return false
        if (updateTime != other.updateTime) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + isGroup.hashCode()
        result = 31 * result + (ownerId?.hashCode() ?: 0)
        result = 31 * result + (updateTime?.hashCode() ?: 0)
        return result
    }
}