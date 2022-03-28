package ru.vizbash.grapevine.storage.node

import android.graphics.Bitmap
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.security.PublicKey

@Entity
data class KnownNode(
    @PrimaryKey val id: Long,
    val username: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val pubKey: PublicKey,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val photo: Bitmap?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as KnownNode

        if (id != other.id) return false
        if (username != other.username) return false
        if (pubKey != other.pubKey) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + username.hashCode()
        result = 31 * result + pubKey.hashCode()
        return result
    }
}