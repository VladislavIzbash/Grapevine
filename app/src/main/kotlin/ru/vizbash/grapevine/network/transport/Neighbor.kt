package ru.vizbash.grapevine.network.transport

import ru.vizbash.grapevine.network.message.DirectMessages.DirectMessage

interface Neighbor {
    fun send(msg: DirectMessage)

    fun setOnReceive(cb: (DirectMessage) -> Unit)

    fun setOnDisconnect(cb: () -> Unit)
}