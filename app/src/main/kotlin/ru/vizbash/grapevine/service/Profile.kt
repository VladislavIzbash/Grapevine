package ru.vizbash.grapevine.service

import android.graphics.Bitmap
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.io.Serializable
import java.security.PrivateKey
import java.security.PublicKey
import javax.crypto.interfaces.DHPrivateKey
import javax.crypto.interfaces.DHPublicKey

data class Profile(
    val nodeId: Long,
    val username: String,
    val pubKey: PublicKey,
    val privKey: PrivateKey,
    val sessionPubKey: DHPublicKey,
    val sessionPrivKey: DHPrivateKey,
    val photo: Bitmap?,
)