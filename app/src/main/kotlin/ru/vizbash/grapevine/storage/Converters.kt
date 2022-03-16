package ru.vizbash.grapevine.storage

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.room.TypeConverter
import ru.vizbash.grapevine.util.decodeRsaPublicKey
import ru.vizbash.grapevine.storage.contacts.ContactEntity
import ru.vizbash.grapevine.storage.messages.MessageEntity
import ru.vizbash.grapevine.util.decodeSecretKey
import java.io.ByteArrayOutputStream
import java.security.PublicKey
import java.util.*
import javax.crypto.SecretKey

class Converters {
    @TypeConverter
    fun publicKeyFromBytes(bytes: ByteArray?) = bytes?.let { decodeRsaPublicKey(it) }

    @TypeConverter
    fun publicKeyToBytes(publicKey: PublicKey?) = publicKey?.encoded

    @TypeConverter
    fun secretKeyFromBytes(bytes: ByteArray?) = bytes?.let { decodeSecretKey(it) }

    @TypeConverter
    fun secretKeyToBytes(secretKey: SecretKey?) = secretKey?.encoded

    @TypeConverter
    fun bitmapFromBytes(bytes: ByteArray?) = bytes?.let {
        BitmapFactory.decodeByteArray(it, 0, it.size)
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

    @TypeConverter
    fun uriToString(uri: Uri?): String? = uri?.toString()

    @TypeConverter
    fun stringToUri(str: String?): Uri? = str?.let { Uri.parse(it) }
}