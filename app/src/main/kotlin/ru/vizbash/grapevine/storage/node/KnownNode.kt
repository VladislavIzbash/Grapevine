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
)