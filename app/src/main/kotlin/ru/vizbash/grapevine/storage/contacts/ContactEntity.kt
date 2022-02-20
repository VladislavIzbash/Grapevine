package ru.vizbash.grapevine.storage.contacts

import android.graphics.Bitmap
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import ru.vizbash.grapevine.network.Node
import java.security.PublicKey

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey @ColumnInfo(name = "node_id") val nodeId: Long,
    @ColumnInfo(name = "username") val username: String,
    @ColumnInfo(name = "public_key", typeAffinity = ColumnInfo.BLOB) val publicKey: PublicKey,
    @ColumnInfo(name = "photo", typeAffinity = ColumnInfo.BLOB) val photo: Bitmap?,
    @ColumnInfo(name = "state") val state: State,
) {
    enum class State { ACCEPTED, OUTGOING, INGOING }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ContactEntity

        if (nodeId != other.nodeId) return false
        if (username != other.username) return false
        if (publicKey != other.publicKey) return false
        if (state != other.state) return false

        return true
    }

    override fun hashCode(): Int {
        var result = nodeId.hashCode()
        result = 31 * result + username.hashCode()
        result = 31 * result + publicKey.hashCode()
        result = 31 * result + state.hashCode()
        return result
    }
}