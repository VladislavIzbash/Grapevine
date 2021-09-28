package ru.vizbash.grapevine.network.transport

import com.google.protobuf.MessageLite

interface Neighbor {
    fun sendMessage(msg: MessageLite)

    fun setOnReceive(onReceive: (MessageLite) -> Unit)
}