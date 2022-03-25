package ru.vizbash.grapevine.network

import com.google.protobuf.ByteString
import ru.vizbash.grapevine.network.message.DirectMessages
import ru.vizbash.grapevine.network.message.node
import ru.vizbash.grapevine.service.profile.Profile
import ru.vizbash.grapevine.util.decodeDhPublicKey
import ru.vizbash.grapevine.util.decodeRsaPublicKey
import java.security.PublicKey
import javax.crypto.interfaces.DHPublicKey

data class Node(
    val id: Long,
    val username: String,
    val pubKey: PublicKey,
    val sessionPubKey: DHPublicKey,
    var primarySource: SourceType? = null,
) {
    constructor(proto: DirectMessages.Node) : this(
        proto.userId,
        proto.username,
        decodeRsaPublicKey(proto.pubKey.toByteArray()),
        decodeDhPublicKey(proto.sessionPubKey.toByteArray()),
    )

    constructor(profile: Profile) : this(
        profile.nodeId,
        profile.username,
        profile.pubKey,
        profile.sessionPubKey,
    )

    fun toProto(): DirectMessages.Node = node {
        userId = this@Node.id
        username = this@Node.username
        pubKey = ByteString.copyFrom(this@Node.pubKey.encoded)
        sessionPubKey = ByteString.copyFrom(this@Node.sessionPubKey.encoded)
    }

    override fun toString() = "$username ($id)"
}