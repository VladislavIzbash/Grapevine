package ru.vizbash.grapevine.network.transport

import com.google.protobuf.MessageLite

interface Neighbor {
    fun send(msg: MessageLite)

    fun setOnReceived(onReceived: (MessageLite) -> Unit)
}