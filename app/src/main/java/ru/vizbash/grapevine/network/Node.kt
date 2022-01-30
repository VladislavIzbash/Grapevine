package ru.vizbash.grapevine.network

import com.google.protobuf.ByteString
import ru.vizbash.grapevine.network.messages.direct.NodeMessage
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec

data class Node(var id: Long, var username: String, var publicKey: PublicKey) {
    constructor(msg: NodeMessage) : this(
        msg.userId,
        msg.username,
        KeyFactory
            .getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(msg.publicKey.toByteArray()))
    )

    fun toMessage() = NodeMessage.newBuilder()
        .setUserId(id)
        .setUsername(username)
        .setPublicKey(ByteString.copyFrom(publicKey.encoded))
        .build()
}
