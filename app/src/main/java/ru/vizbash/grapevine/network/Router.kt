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

private const val POLL_DURATION_MS = 200L
private const val ASK_NODES_INTERVAL_MS = 5000L

class Router(private val selfNode: Node) : Runnable {
    private data class KnownNeighbor(val neighbor: Neighbor, val node: Node)
    private data class ReceivedMessage(val sender: Neighbor, val data: MessageLite)

    private val neighbors = mutableMapOf<Long, KnownNeighbor>()
    private val _nodes = mutableMapOf<Long, Node>()
    private val messageQueue = LinkedBlockingQueue<ReceivedMessage>(20)
    private val stopped = AtomicBoolean(false)

    val nodes
        @Synchronized
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
            messageQueue.put(ReceivedMessage(neighbor, msg))
        }
    }

    override fun run() {
        var elapsedMs = 0L
        while (!stopped.get()) {
            elapsedMs += measureTimeMillis {
                val msg = messageQueue.poll(POLL_DURATION_MS, TimeUnit.MILLISECONDS) ?: return

                when (msg.data) {
                    is HandshakeMessage -> acceptNeighbor(msg.sender, Node(msg.data.node))
                    is AskNodesReq -> sendKnownNodes(msg.sender)
                    is AskNodesResp -> appendKnownNodes(msg.data.nodesList.map(::Node))
                }
            }
            if (elapsedMs > ASK_NODES_INTERVAL_MS) {
                askNodes()
                elapsedMs = 0
            }
        }
    }

    private fun askNodes() {
        for (neighbor in neighbors.values) {
            neighbor.neighbor.send(AskNodesReq.newBuilder().build())
        }
    }

    private fun sendKnownNodes(neighbor: Neighbor) {
        val resp = synchronized(this) {
            AskNodesResp.newBuilder()
                .addAllNodes(_nodes.values.map(Node::toMessage))
                .build()
        }
        neighbor.send(resp)
    }

    @Synchronized
    private fun appendKnownNodes(newNodes: List<Node>) {
        for (node in newNodes) {
            _nodes[node.id] = node
        }
    }

    @Synchronized
    private fun acceptNeighbor(neighbor: Neighbor, node: Node) {
        neighbors[node.id] = KnownNeighbor(neighbor, node)
        _nodes[node.id] = node
    }

    fun stop() {
        stopped.set(true)
    }
}