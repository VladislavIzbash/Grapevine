package ru.vizbash.grapevine.network

import com.google.protobuf.MessageLite
import ru.vizbash.grapevine.network.messages.Test
import ru.vizbash.grapevine.network.transport.DiscoveryService
import ru.vizbash.grapevine.network.transport.Neighbor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

class NetworkDispatcher {
    private class ReceivedMessage(val sender: Neighbor, val msg: MessageLite)

    private val messageQueue = LinkedBlockingQueue<ReceivedMessage>()
    private val neighbors = mutableListOf<Neighbor>()
    private var stop = AtomicBoolean(false)

    fun addDiscovery(vararg discovery: DiscoveryService) {
        for (svc in discovery) {
            svc.setOnDiscovered(this::onNeighborDiscovered)
        }
    }

    private fun handleMessages() {
        while (!stop.get()) {
            val msg = messageQueue.take()
            TODO()
        }
    }

    @Synchronized
    private fun onNeighborDiscovered(neighbor: Neighbor) {
        neighbor.setOnReceive { msg ->
            messageQueue.put(ReceivedMessage(neighbor, msg))
        }
        neighbors += neighbor
    }

    fun stop() {
        stop.set(false)
    }
}