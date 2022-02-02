package ru.vizbash.grapevine.network

import com.google.protobuf.ByteString
import ru.vizbash.grapevine.AuthService
import ru.vizbash.grapevine.db.identity.Identity
import ru.vizbash.grapevine.decodePublicKey
import ru.vizbash.grapevine.network.messages.direct.NodeMessage
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec

data class Node(var id: Long, var username: String, var publicKey: PublicKey) {
    constructor(msg: NodeMessage) : this(
        msg.userId,
        msg.username,
        decodePublicKey(msg.publicKey.toByteArray()),
    )

    constructor(ident: Identity): this(
        ident.nodeId,
        ident.username,
        ident.publicKey,
    )

    fun toMessage() = NodeMessage.newBuilder()
        .setUserId(id)
        .setUsername(username)
        .setPublicKey(ByteString.copyFrom(publicKey.encoded))
        .build()

    override fun toString() = "$username ($id)"
}
