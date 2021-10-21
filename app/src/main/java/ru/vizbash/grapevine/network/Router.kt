package ru.vizbash.grapevine.network

import com.google.protobuf.MessageLite
import ru.vizbash.grapevine.network.messages.direct.AskNodesReq
import ru.vizbash.grapevine.network.messages.direct.AskNodesResp
import ru.vizbash.grapevine.network.messages.direct.HandshakeMessage
import ru.vizbash.grapevine.network.transport.DiscoveryService
import ru.vizbash.grapevine.network.transport.Neighbor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.measureTimeMillis

private const val TAG = "Router"
private const val POLL_DURATION_MS = 200L
private const val ASK_NODES_INTERVAL_MS = 5000L

class Router(private val selfNode: Node) : Runnable {
    private data class KnownNeighbor(val neighbor: Neighbor, val node: Node)
    private data class ReceivedMessage(val sender: Neighbor, val data: MessageLite)

    private val _neighbors = mutableMapOf<Long, KnownNeighbor>()
    private val _nodes = mutableMapOf<Long, Node>()
    private val _messageQueue = LinkedBlockingQueue<ReceivedMessage>(20)
    private val _stopped = AtomicBoolean(false)

    val nodes
        get(): Collection<Node> = _nodes.values

    fun addDiscovery(vararg discovery: DiscoveryService) {
        for (svc in discovery) {
            svc.setOnDiscovered(this::onDiscovered)
        }
    }

    private fun onDiscovered(neighbor: Neighbor) {
        val handshake = HandshakeMessage.newBuilder()
            .setNode(selfNode.toMessage())
            .build()

        neighbor.send(handshake)
        neighbor.setOnReceived { msg ->
            _messageQueue.put(ReceivedMessage(neighbor, msg))
        }
    }

    override fun run() {
        while (!_stopped.get()) {
            val elapsedMs = measureTimeMillis {
                val msg = _messageQueue.poll(POLL_DURATION_MS, TimeUnit.MILLISECONDS) ?: return

                when (msg.data) {
                    is HandshakeMessage -> acceptNeighbor(msg.sender, Node(msg.data.node))
                    is AskNodesReq -> {

                    }
                }
            }
            if (elapsedMs > ASK_NODES_INTERVAL_MS) {
                askNodes()
            }
        }
    }

    private fun askNodes() {
        synchronized(_neighbors) {
            for (neighbor in _neighbors.values) {
                neighbor.neighbor.send(AskNodesReq.newBuilder().build())
            }
        }
    }

    private fun acceptNeighbor(neighbor: Neighbor, node: Node) {
        synchronized(_neighbors) {
            _neighbors[node.id] = KnownNeighbor(neighbor, node)
        }
        synchronized(_nodes) {
            _nodes[node.id] = node
        }
    }

    fun stop() {
        _stopped.set(true)
    }
}