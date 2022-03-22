package ru.vizbash.grapevine.network.dispatch

import ru.vizbash.grapevine.network.Node
import ru.vizbash.grapevine.network.message.GrapevineRouted

data class AcceptedMessage(
    val id: Long,
    val payload: GrapevineRouted.RoutedPayload,
    val sender: Node,
)
