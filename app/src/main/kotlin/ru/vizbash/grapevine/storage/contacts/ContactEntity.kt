package ru.vizbash.grapevine.storage.contacts

import android.graphics.Bitmap
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
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
}