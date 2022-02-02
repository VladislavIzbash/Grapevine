package ru.vizbash.grapevine.network.transport

import ru.vizbash.grapevine.network.messages.direct.DirectMessage

interface Neighbor {
    fun send(msg: DirectMessage)

    fun setOnReceive(cb: (DirectMessage) -> Unit)

    fun setOnDisconnect(cb: () -> Unit)

    fun identify(): String
}