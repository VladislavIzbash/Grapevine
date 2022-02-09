package ru.vizbash.grapevine.network

import com.google.protobuf.ByteString
import ru.vizbash.grapevine.storage.profile.Profile
import ru.vizbash.grapevine.decodePublicKey
import ru.vizbash.grapevine.network.messages.direct.NodeMessage
import java.security.PublicKey

data class Node(
    var id: Long,
    var username: String,
    var publicKey: PublicKey,
    var primarySource: SourceType? = null,
) {
    constructor(msg: NodeMessage) : this(
        msg.userId,
        msg.username,
        decodePublicKey(msg.publicKey.toByteArray()),
    )

    constructor(profile: Profile): this(
        profile.nodeId,
        profile.username,
        profile.publicKey,
    )

    fun toMessage(): NodeMessage = NodeMessage.newBuilder()
        .setUserId(id)
        .setUsername(username)
        .setPublicKey(ByteString.copyFrom(publicKey.encoded))
        .build()

    override fun toString() = "$username ($id)"

    override fun equals(other: Any?) = other is Node
            && other.id == id

    override fun hashCode() = id.hashCode()
}
