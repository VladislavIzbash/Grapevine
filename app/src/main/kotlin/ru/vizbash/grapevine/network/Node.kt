package ru.vizbash.grapevine.network

import com.google.protobuf.ByteString
import ru.vizbash.grapevine.service.Profile
import ru.vizbash.grapevine.util.decodeDhPublicKey
import ru.vizbash.grapevine.util.decodeRsaPublicKey
import ru.vizbash.grapevine.network.messages.direct.NodeMessage
import java.security.PublicKey

data class Node(
    var id: Long,
    var username: String,
    var publicKey: PublicKey,
    var dhPublicKey: PublicKey,
    var primarySource: SourceType? = null,
) {
    constructor(msg: NodeMessage) : this(
        msg.userId,
        msg.username,
        decodeRsaPublicKey(msg.publicKey.toByteArray()),
        decodeDhPublicKey(msg.dhPublicKey.toByteArray()),
    )

    constructor(profile: Profile): this(
        profile.entity.nodeId,
        profile.entity.username,
        profile.entity.publicKey,
        profile.dhPublicKey,
    )

    fun toMessage(): NodeMessage = NodeMessage.newBuilder()
        .setUserId(id)
        .setUsername(username)
        .setPublicKey(ByteString.copyFrom(publicKey.encoded))
        .setDhPublicKey(ByteString.copyFrom(dhPublicKey.encoded))
        .build()

    override fun toString() = "$username ($id)"

    override fun equals(other: Any?) = other is Node
            && other.id == id

    override fun hashCode() = id.hashCode()
}
