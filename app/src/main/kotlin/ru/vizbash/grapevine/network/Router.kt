package ru.vizbash.grapevine.network

import android.util.Log
import com.google.protobuf.ByteString
import ru.vizbash.grapevine.GVException
import ru.vizbash.grapevine.ProfileProvider
import ru.vizbash.grapevine.TAG
import ru.vizbash.grapevine.network.messages.direct.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class Router @Inject constructor(private val profileService: ProfileProvider) {
    private data class NodeRoute(val neighbor: Neighbor, val hops: Int)

    class ReceivedMessage(val id: Long, val payload: ByteArray, val sign: ByteArray, val sender: Node)

    class GVNodeOfflineException : GVException()

    companion object {
        private const val DEFAULT_TTL = 16
    }

    private val routingTable = mutableMapOf<Node, MutableSet<NodeRoute>>()

    private val myNode get() = Node(profileService.profile)

    @Volatile private var receiveCb: (ReceivedMessage) -> Unit = { _ -> }
    @Volatile private var nodesUpdatedCb: () -> Unit = {}

    val nodes: Set<Node>
        @Synchronized
        get() = routingTable.map { (node, routes) ->
            node.apply { primarySource = routes.minByOrNull(NodeRoute::hops)!!.neighbor.sourceType }
        }.toSet()

    fun addNeighbor(neighbor: Neighbor) {
        val hello = HelloRequest.newBuilder().setNode(myNode.toMessage()).build()
        val helloMsg = DirectMessage.newBuilder().setHelloReq(hello).build()
        neighbor.send(helloMsg)

        Log.d(TAG, "Sent hello to $neighbor")

        neighbor.setOnReceive { msg -> onMessageReceived(neighbor, msg) }
    }

    @Synchronized
    fun sendMessage(payload: ByteArray, sign: ByteArray, dest: Node): Long {
        val routes = routingTable[dest]
            ?: throw GVNodeOfflineException()

        val id = Random.nextLong()
        val neighbor = routes.minByOrNull(NodeRoute::hops)!!.neighbor

        Log.d(TAG, "Sending routed message to $dest")

        val msg = RoutedMessage.newBuilder()
            .setMsgId(id)
            .setSrcId(myNode.id)
            .setDestId(dest.id)
            .setPayload(ByteString.copyFrom(payload))
            .setTtl(DEFAULT_TTL)
            .setSign(ByteString.copyFrom(sign))
        neighbor.send(DirectMessage.newBuilder().setRouted(msg).build())

        return id
    }

    fun setOnMessageReceived(cb: (ReceivedMessage) -> Unit) {
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

        Log.d(TAG, "$neighbor disconnected, ${routingTable.values.size} nodes remaining")
    }

    @Synchronized
    private fun onMessageReceived(neighbor: Neighbor, msg: DirectMessage) {
        when (msg.msgCase!!) {
            DirectMessage.MsgCase.HELLO_REQ -> {
                val hello = HelloResponse.newBuilder().setNode(myNode.toMessage()).build()
                val helloMsg = DirectMessage.newBuilder().setHelloResp(hello).build()
                neighbor.send(helloMsg)
            }
            DirectMessage.MsgCase.HELLO_RESP -> handleHelloResponse(neighbor, msg.helloResp)
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
                receiveCb(ReceivedMessage(
                    routed.msgId,
                    routed.payload.toByteArray(),
                    routed.sign.toByteArray(),
                    sender,
                ))
                Log.d(TAG, "Received routed message from $sender")
            }
            return
        }

        val ttl = msg.routed.ttl - 1
        if (ttl <= 0) {
            return
        }

        val withTtl = msg.routed.toBuilder().setTtl(ttl).build()
        val outMsg = DirectMessage.newBuilder().setRouted(withTtl).build()

        val destNode = routingTable.keys.find { it.id == routed.destId } ?: return
        val nextHop = routingTable[destNode]!!.minByOrNull(NodeRoute::hops)!!.neighbor
        nextHop.send(outMsg)
    }

    private fun handleHelloResponse(neighbor: Neighbor, nodeMsg: HelloResponse) {
        neighbor.setOnDisconnect { onNeighborDisconnected(neighbor) }

        val node = Node(nodeMsg.node)
        val nodeRoutes = routingTable.getOrPut(node) { mutableSetOf() }

        val nodeRoute = NodeRoute(neighbor, 0)
        if (nodeRoute !in nodeRoutes) {
            Log.d(TAG, "Discovered neighbor $neighbor with node: $node")
            nodeRoutes.add(nodeRoute)
        }
        nodesUpdatedCb()
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