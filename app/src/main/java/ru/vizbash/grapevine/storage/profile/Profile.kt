package ru.vizbash.grapevine.storage.profile

import android.graphics.Bitmap
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.security.PublicKey

@Entity(tableName = "profiles")
data class Profile(
    @ColumnInfo(name = "node_id") @PrimaryKey val nodeId: Long,
    @ColumnInfo(name = "username") val username: String,
    @ColumnInfo(name = "public_key", typeAffinity = ColumnInfo.BLOB) val publicKey: PublicKey,
    @ColumnInfo(name = "private_key_enc", typeAffinity = ColumnInfo.BLOB) val privateKeyEnc: ByteArray,
    @ColumnInfo(name = "photo", typeAffinity = ColumnInfo.BLOB) val photo: Bitmap?,
)
