package ru.vizbash.grapevine.network.transport

import com.google.protobuf.MessageLite

interface Neighbor {
    suspend fun send(msg: MessageLite)

    suspend fun receive(): MessageLite
}