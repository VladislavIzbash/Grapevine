package ru.vizbash.grapevine.network

import android.util.Log
import ru.vizbash.grapevine.AuthService
import ru.vizbash.grapevine.TAG
import ru.vizbash.grapevine.network.messages.direct.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Router @Inject constructor(private val authService: AuthService) {
    private data class NodeRoute(val neighbor: Neighbor, val hops: Int)

    private val routingTable = mutableMapOf<Node, MutableSet<NodeRoute>>()

    private val myNode
        @Synchronized
        get() = Node(authService.currentProfile!!.base)

    @Volatile private var receiveCb: (RoutedMessage, Node) -> Unit = { _, _ -> }
    @Volatile private var nodesUpdatedCb: () -> Unit = {}

    val nodes: Set<Node>
        @Synchronized
        get() = routingTable.map { (node, routes) ->
            node.apply { primarySource = routes.minByOrNull(NodeRoute::hops)!!.neighbor.sourceType }
        }.toSet()

    fun addNeighbor(neighbor: Neighbor) {
        sendHello(neighbor)
        neighbor.setOnReceive { msg -> onMessageReceived(neighbor, msg) }
    }

    @Synchronized
    fun sendMessage(message: RoutedMessage, dest: Node) {
        if (dest !in routingTable) {
            throw IllegalArgumentException("Dest node is unknown to router")
        }

        Log.d(TAG, "Sending message to $dest")

        val neighbor = routingTable[dest]!!.minByOrNull(NodeRoute::hops)!!.neighbor
        neighbor.send(DirectMessage.newBuilder().setRouted(message).build())
    }

    fun setOnMessageReceived(cb: (RoutedMessage, Node) -> Unit) {
        receiveCb = cb
    }

    fun setOnNodesUpdated(cb: () -> Unit) {
        nodesUpdatedCb = cb
    }

    @Synchronized
    fun askForNodes() {
        val neighbors = routingTable.values.flatten().map(NodeRoute::neighbor).distinct()

        Log.d(TAG, "Asking ${neighbors.size} neighbors for nodes")

        for (neighbor in neighbors) {
            val askNodesReq = AskNodesRequest.newBuilder().build()
            neighbor.send(DirectMessage.newBuilder().setAskNodesReq(askNodesReq).build())
        }
    }

    private fun sendHello(neighbor: Neighbor) {
        val msg = DirectMessage.newBuilder()
            .setHello(myNode.toMessage())
            .build()
        neighbor.send(msg)
    }

    private fun clearEmptyRoutes() {
        routingTable.values.removeIf(Set<NodeRoute>::isEmpty)
    }

    @Synchronized
    private fun onNeighborDisconnected(neighbor: Neighbor) {
        for (route in routingTable.values) {
            route.removeIf { it.neighbor == neighbor }
        }
        clearEmptyRoutes()
        nodesUpdatedCb()
    }

    @Synchronized
    private fun onMessageReceived(neighbor: Neighbor, msg: DirectMessage) {
        when (msg.msgCase!!) {
            DirectMessage.MsgCase.HELLO -> handleHello(neighbor, msg.hello)
            DirectMessage.MsgCase.ASK_NODES_REQ -> handleNodesRequest(neighbor)
            DirectMessage.MsgCase.ASK_NODES_RESP -> handleNodesResponse(neighbor, msg.askNodesResp)
            DirectMessage.MsgCase.ROUTED -> routeIncomingMessage(msg)
            DirectMessage.MsgCase.MSG_NOT_SET -> {
                Log.w(TAG, "$neighbor sent invalid message")
            }
        }
    }

    private fun routeIncomingMessage(msg: DirectMessage) {
        val routed = msg.routed

        if (routed.destId == myNode.id) {
            val sender = routingTable.keys.find { it.id == routed.srcId }
            if (sender == null) {
                Log.w(TAG, "Cannot receive message from unknown sender ${routed.srcId}")
            } else {
                Log.d(TAG, "Received message from node: $sender")
                receiveCb(routed, sender)
            }
            return
        }

        val destNode = routingTable.keys.find { it.id == routed.destId } ?: return
        val nextHop = routingTable[destNode]!!.minByOrNull(NodeRoute::hops)!!.neighbor
        nextHop.send(msg)
    }

    private fun handleHello(neighbor: Neighbor, nodeMsg: NodeMessage) {
        neighbor.setOnDisconnect { onNeighborDisconnected(neighbor) }

        val node = Node(nodeMsg)
        val nodeRoutes = routingTable.getOrPut(node) { mutableSetOf() }

        val nodeRoute = NodeRoute(neighbor, 0)
        if (nodeRoute !in nodeRoutes) {
            Log.d(TAG, "Discovered neighbor $neighbor with node: $node")
            nodeRoutes.add(nodeRoute)
            nodesUpdatedCb()
        }
    }

    private fun handleNodesRequest(neighbor: Neighbor) {
        val nodeRoutes = routingTable.map { (node, routes) ->
            NodeRouteMessage.newBuilder()
                .setNode(node.toMessage())
                .setHops(routes.minOf(NodeRoute::hops))
                .build()
        }

        val askNodesResp = AskNodesResponse.newBuilder()
            .addAllNodes(nodeRoutes)
            .build()
        neighbor.send(DirectMessage.newBuilder().setAskNodesResp(askNodesResp).build())
    }

    private fun handleNodesResponse(neighbor: Neighbor, askNodesResp: AskNodesResponse) {
        val receivedNodes = mutableListOf<Node>()

        for (nodeHops in askNodesResp.nodesList) {
            val node = Node(nodeHops.node)
            if (node == myNode) {
                continue
            }

            val nodeRoutes = routingTable.getOrPut(node) { mutableSetOf() }
            nodeRoutes.add(NodeRoute(neighbor, nodeHops.hops))

            receivedNodes.add(node)
        }

        val neighborNode = routingTable.filter { (_, routes) ->
            routes.contains(NodeRoute(neighbor, 0))
        }.firstNotNullOf { pair -> pair.key }

        receivedNodes.add(neighborNode)

        for ((node, route) in routingTable) {
            if (node !in receivedNodes) {
                route.removeIf { it.neighbor == neighbor }
            }
        }
        clearEmptyRoutes()

        nodesUpdatedCb()
    }
}