package ru.vizbash.grapevine.network.transport

import ru.vizbash.grapevine.network.SourceType
import ru.vizbash.grapevine.network.message.GrapevineDirect.DirectMessage

interface Neighbor {
    val sourceType: SourceType

    fun send(msg: DirectMessage)

    fun setOnReceive(cb: (DirectMessage) -> Unit)

    fun setOnDisconnect(cb: () -> Unit)
}