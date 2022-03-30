package ru.vizbash.grapevine.service.profile

import android.graphics.Bitmap
import ru.vizbash.grapevine.storage.node.KnownNode
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
) {
    fun toKnownNode() = KnownNode(nodeId, username, pubKey, photo)
}