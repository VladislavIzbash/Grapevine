package ru.vizbash.grapevine.db

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.room.TypeConverter
import ru.vizbash.grapevine.decodePublicKey
import java.io.ByteArrayOutputStream
import java.security.PublicKey

class Converters {
    @TypeConverter
    fun publicKeyFromBytes(bytes: ByteArray?) = bytes?.let { decodePublicKey(bytes) }

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
}