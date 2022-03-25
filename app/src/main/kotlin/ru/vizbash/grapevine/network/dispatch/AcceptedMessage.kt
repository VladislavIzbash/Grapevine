package ru.vizbash.grapevine.network.dispatch

import ru.vizbash.grapevine.network.Node
import ru.vizbash.grapevine.network.message.RoutedMessages

data class AcceptedMessage(
    val id: Long,
    val payload: RoutedMessages.RoutedPayload,
    val sender: Node,
)
