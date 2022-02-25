package ru.vizbash.grapevine.storage

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.room.TypeConverter
import ru.vizbash.grapevine.decodeRsaPublicKey
import ru.vizbash.grapevine.storage.contacts.ContactEntity
import ru.vizbash.grapevine.storage.messages.MessageEntity
import java.io.ByteArrayOutputStream
import java.security.PublicKey
import java.time.Instant
import java.util.*

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

    @TypeConverter
    fun intToMessageState(value: Int) = enumValues<MessageEntity.State>()[value]

    @TypeConverter
    fun messageStateToInt(value: MessageEntity.State) = value.ordinal

    @TypeConverter
    fun dateToLong(date: Date): Long = date.time / 1000

    @TypeConverter
    fun longToDate(value: Long): Date = Date(value * 1000)
}