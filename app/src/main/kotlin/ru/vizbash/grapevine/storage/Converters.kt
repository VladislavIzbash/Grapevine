package ru.vizbash.grapevine.storage

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.room.TypeConverter
import ru.vizbash.grapevine.decodeRsaPublicKey
import ru.vizbash.grapevine.storage.contacts.ContactEntity
import java.io.ByteArrayOutputStream
import java.security.PublicKey

class Converters {
    @TypeConverter
    fun publicKeyFromBytes(bytes: ByteArray?) = bytes?.let { decodeRsaPublicKey(bytes) }

    @TypeConverter
    fun publicKeyToBytes(publicKey: PublicKey?) = publicKey?.encoded

    @TypeConverter
    fun bitmapFromBytes(bytes: ByteArray?) = bytes?.let {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    @TypeConverter
    fun bitmapToBytes(bitmap: Bitmap?) = bitmap?.let {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        out.toByteArray()
    }

    @TypeConverter
    fun intToContactState(value: Int) = enumValues<ContactEntity.State>()[value]

    @TypeConverter
    fun contactStateToInt(value: ContactEntity.State) = value.ordinal
}