package ru.vizbash.grapevine.network

import com.google.protobuf.MessageLite
import ru.vizbash.grapevine.network.messages.direct.AskNodesRequest
import ru.vizbash.grapevine.network.messages.direct.AskNodesResponse
import ru.vizbash.grapevine.network.messages.direct.DirectMessage
import ru.vizbash.grapevine.network.messages.direct.HelloMessage
import ru.vizbash.grapevine.network.messages.routed.StatusResponse
import ru.vizbash.grapevine.network.transport.Neighbor
import java.util.*
import kotlin.collections.ArrayList

class Router(private val selfNode: Node) {
    private data class KnownNeighbor(val neighbor: Neighbor, val node: Node) {
        val nodes = mutableListOf<Node>()
    }

    private val neighbors: MutableList<KnownNeighbor> = Collections.synchronizedList(ArrayList())

    fun addNeighbor(neighbor: Neighbor) {
        val helloMessage = HelloMessage.newBuilder()
            .setNode(selfNode.toMessage())
            .build()
        neighbor.send(DirectMessage.newBuilder().setHello(helloMessage).build())
    }

    @Synchronized
    fun updateNodeList() {
//        for (neighbor in neighbors) {
//            neighbor.neighbor.send(AskNodesRequest.newBuilder().build())
//
//            val resp = neighbor.neighbor.receive()
//            if (resp is AskNodesResponse) {
//                neighbor.nodes.addAll(resp.nodesList.map(::Node))
//            }
//        }
    }

    @Synchronized
    fun sendMessage(msg: MessageLite): StatusResponse {
        TODO()
    }
}