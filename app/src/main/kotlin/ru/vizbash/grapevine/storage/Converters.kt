package ru.vizbash.grapevine.storage

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.room.TypeConverter
import ru.vizbash.grapevine.storage.message.Message
import ru.vizbash.grapevine.storage.message.MessageFile
import ru.vizbash.grapevine.util.encodeBitmap
import ru.vizbash.grapevine.util.decodeRsaPublicKey
import java.security.PublicKey
import java.util.*

class Converters {
    @TypeConverter
    fun toPublicKey(bytes: ByteArray?) = bytes?.let { decodeRsaPublicKey(it) }

    @TypeConverter
    fun fromPublicKey(publicKey: PublicKey?) = publicKey?.encoded

    @TypeConverter
    fun toBitmap(bytes: ByteArray?) = bytes?.let {
        BitmapFactory.decodeByteArray(it, 0, it.size)
    }

    @TypeConverter
    fun fromBitmap(bitmap: Bitmap?) = bitmap?.let { encodeBitmap(it) }

    @TypeConverter
    fun toMessageState(value: Int) = enumValues<Message.State>()[value]

    @TypeConverter
    fun fromMessageState(value: Message.State) = value.ordinal

    @TypeConverter
    fun toFileState(value: Int) = enumValues<MessageFile.State>()[value]

    @TypeConverter
    fun fromFileState(value: MessageFile.State) = value.ordinal

    @TypeConverter
    fun fromDate(date: Date): Long = date.time / 1000

    @TypeConverter
    fun toDate(value: Long): Date = Date(value * 1000)

    @TypeConverter
    fun fromUri(uri: Uri?): String? = uri?.toString()

    @TypeConverter
    fun toUri(str: String?): Uri? = str?.let { Uri.parse(it) }
}