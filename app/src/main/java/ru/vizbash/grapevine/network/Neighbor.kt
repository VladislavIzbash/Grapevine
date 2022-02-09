package ru.vizbash.grapevine.network

import ru.vizbash.grapevine.network.messages.direct.DirectMessage

interface Neighbor {
    val sourceType: SourceType

    fun send(msg: DirectMessage)

    fun setOnReceive(cb: (DirectMessage) -> Unit)

    fun setOnDisconnect(cb: () -> Unit)
}