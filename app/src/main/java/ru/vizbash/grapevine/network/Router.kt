package ru.vizbash.grapevine.network

import android.util.Log
import dagger.hilt.android.scopes.ServiceScoped
import ru.vizbash.grapevine.AuthService
import ru.vizbash.grapevine.TAG
import ru.vizbash.grapevine.network.messages.direct.*
import ru.vizbash.grapevine.network.transport.Neighbor
import javax.inject.Inject

@ServiceScoped
class Router @Inject constructor(private val authService: AuthService) {
    private data class NodeRoute(val neighbor: Neighbor, val hops: Int)

    private val routingTable = mutableMapOf<Node, MutableSet<NodeRoute>>()

    private val myNode
        @Synchronized
        get() = Node(authService.currentIdent!!.base)

    @Volatile private var receiveCb: (RoutedMessage, Node) -> Unit = { _, _ -> }
    @Volatile private var nodesUpdatedCb: () -> Unit = {}

    val nodes: Collection<Node>
        @Synchronized
        get() = routingTable.keys

    fun addNeighbor(neighbor: Neighbor) {
        sendHello(neighbor)
        neighbor.setOnReceive { msg -> onMessageReceived(neighbor, msg) }
    }

    @Synchronized
    fun sendMessage(message: RoutedMessage, dest: Node) {
        if (!routingTable.contains(dest)) {
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
        Log.d(TAG, "Asking neighbors for nodes")

        routingTable.values.flatten().map(NodeRoute::neighbor).distinct().forEach { neighbor ->
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

    @Synchronized
    private fun onNeighborDisconnected(neighbor: Neighbor) {
        routingTable.values.forEach { route ->
            route.removeIf { it.neighbor == neighbor }
        }
        routingTable.values.removeIf(Set<NodeRoute>::isEmpty)
    }

    @Synchronized
    private fun onMessageReceived(neighbor: Neighbor, msg: DirectMessage) {
        when (msg.msgCase!!) {
            DirectMessage.MsgCase.HELLO -> handleHello(neighbor, msg.hello)
            DirectMessage.MsgCase.ASK_NODES_REQ -> handleNodesRequest(neighbor)
            DirectMessage.MsgCase.ASK_NODES_RESP -> handleNodesResponse(neighbor, msg.askNodesResp)
            DirectMessage.MsgCase.ROUTED -> routeIncomingMessage(msg)
            DirectMessage.MsgCase.MSG_NOT_SET -> {
                Log.w(TAG, "${neighbor.identify()} sent invalid message")
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
        if (!nodeRoutes.contains(nodeRoute)) {
            Log.d(TAG, "Discovered neighbor ${neighbor.identify()} with node: $node")
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
        for (nodeHops in askNodesResp.nodesList) {
            val node = Node(nodeHops.node)
            if (node == myNode) {
                continue
            }

            val nodeRoutes = routingTable.getOrPut(node) { mutableSetOf() }

            nodeRoutes.add(NodeRoute(neighbor, nodeHops.hops))
        }
        nodesUpdatedCb()
    }
}