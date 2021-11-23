package ru.vizbash.grapevine.network

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.vizbash.grapevine.network.messages.direct.AskNodesReq
import ru.vizbash.grapevine.network.messages.direct.AskNodesResp
import ru.vizbash.grapevine.network.messages.direct.HandshakeMessage
import ru.vizbash.grapevine.network.transport.Neighbor

private const val ASK_NODES_INTERVAL_MS = 5000L

class Router(
    private val selfNode: Node,
    private val discoveries: ReceiveChannel<Neighbor>,
) {
    private data class KnownNeighbor(val neighbor: Neighbor, val node: Node)

    private val neighbors = mutableListOf<KnownNeighbor>()
    private val nodes = mutableMapOf<Long, Node>()
    private val nodesMutex = Mutex()

    suspend fun run() = coroutineScope {
//        launch {
//            while (true) {
//                delay(ASK_NODES_INTERVAL_MS)
//                askNodes()
//            }
//        }

        for (neighbor in discoveries) {
            launch { neighborHandler(neighbor) }
        }
    }

    suspend fun nodes() = nodesMutex.withLock { nodes.values }

    private suspend fun neighborHandler(neighbor: Neighbor) {
        val handshake = HandshakeMessage.newBuilder()
            .setNode(selfNode.toMessage())
            .build()

        neighbor.send(handshake)

        if (!acceptNeighbor(neighbor)) {
            return
        }

        val msg = neighbor.receive()
        when (msg) {
            is AskNodesReq -> {
                val resp = AskNodesResp.newBuilder()
                    .addAllNodes(nodes.values.map(Node::toMessage))
                    .build()
                neighbor.send(resp)
            }
            is AskNodesResp -> nodesMutex.withLock {
                for (nodeMsg in msg.nodesList) {
                    nodes[nodeMsg.userId] = Node(nodeMsg)
                }
            }
        }
    }

    private suspend fun acceptNeighbor(neighbor: Neighbor): Boolean {
        val msg = neighbor.receive()
        if (msg !is HandshakeMessage) {
            return false
        }

        val node = Node(msg.node)

        nodesMutex.withLock {
            neighbors.add(KnownNeighbor(neighbor, node))
            nodes[node.id] = node
        }

        return true
    }

    private suspend fun askNodes() {
        nodesMutex.withLock {
            for (neighbor in neighbors) {
                neighbor.neighbor.send(AskNodesReq.newBuilder().build())
            }
        }
    }
}