package ru.vizbash.grapevine

import com.google.protobuf.ByteString
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import ru.vizbash.grapevine.storage.profile.DecryptedProfile
import ru.vizbash.grapevine.storage.profile.Profile
import ru.vizbash.grapevine.network.Router
import ru.vizbash.grapevine.network.messages.direct.DirectMessage
import ru.vizbash.grapevine.network.transport.Neighbor
import java.security.KeyPairGenerator
import kotlin.random.Random
import org.junit.Assert.*
import ru.vizbash.grapevine.network.Node
import ru.vizbash.grapevine.network.messages.direct.RoutedMessage
import kotlin.math.absoluteValue

class RouterTest {
    private class TestNeighbor : Neighbor {
        lateinit var pair: TestNeighbor

        private val id = Random.nextInt().absoluteValue

        private var receiveCb: ((DirectMessage) -> Unit)? = null
        private var disconnectCb: () -> Unit = {}

        private var receiveBuffer = mutableListOf<DirectMessage>()

        override fun send(msg: DirectMessage) {
            pair.receive(msg)
        }

        override fun setOnReceive(cb: (DirectMessage) -> Unit) {
            receiveCb = cb

            receiveBuffer.forEach(receiveCb)
            receiveBuffer.clear()
        }

        override fun setOnDisconnect(cb: () -> Unit) {
            disconnectCb = cb
        }

        private fun receive(msg: DirectMessage) {
            if (receiveCb != null) {
                receiveCb!!(msg)
            } else {
                receiveBuffer.add(msg)
            }
        }

        override fun identify() = "test_node_$id"

        fun disconnect() {
            pair.disconnectCb()
        }

        override fun equals(other: Any?) = other is TestNeighbor
                && other.id == id

        override fun hashCode() = id
    }

    private val keyGen = KeyPairGenerator.getInstance("RSA").apply {
        initialize(1024)
    }

    private fun createRouter(nodeId: Long): Pair<Router, Node> {
        val keyPair = keyGen.genKeyPair()
        val ident = DecryptedProfile(
            Profile(nodeId, "node$nodeId", keyPair.public, ByteArray(0), null),
            keyPair.private,
        )

        val auth = mock<AuthService> {
            on { currentProfile } doReturn ident
        }
        return Pair(Router(auth), Node(ident.base))
    }

    private fun connect(router1: Router, router2: Router): Pair<TestNeighbor, TestNeighbor> {
        val neighbor1 = TestNeighbor()
        val neighbor2 = TestNeighbor()
        neighbor1.pair = neighbor2
        neighbor2.pair = neighbor1

        router1.addNeighbor(neighbor1)
        router2.addNeighbor(neighbor2)

        return Pair(neighbor1, neighbor2)
    }

    /**
     * Topology:
     * (1) -- (2)
     */
    @Test
    fun twoNodesCanReachEachOtherDirectly() {
        val (router1, node1) = createRouter(1)
        val (router2, node2) = createRouter(2)

        connect(router1, router2)

        assertEquals(setOf(node2), router1.nodes)
        assertEquals(setOf(node1), router2.nodes)
    }

    /**
     * Topology:
     *    (1)
     *   /   \
     *  (2)--(3)
     */
    @Test
    fun routersHandleDisconnectedNeighbor() {
        val (router1, node1) = createRouter(1)
        val (router2, node2) = createRouter(2)
        val (router3, node3) = createRouter(3)

        connect(router1, router2)
        val conn13 = connect(router1, router3)
        val conn23 = connect(router2, router3)

        assertEquals(setOf(node2, node3), router1.nodes)
        assertEquals(setOf(node1, node3), router2.nodes)
        assertEquals(setOf(node1, node2), router3.nodes)

        conn13.first.disconnect()
        conn13.second.disconnect()
        conn23.first.disconnect()
        conn23.second.disconnect()

        assertEquals(setOf(node2), router1.nodes)
        assertEquals(setOf(node1), router2.nodes)
        assertEquals(emptySet<Node>(), router3.nodes)
    }

    /**
     * Topology:
     * (1) -- (2) -- (3)
     */
    @Test
    fun nodesCanReachEachOtherIndirectly() {
        val (router1, node1) = createRouter(1)
        val (router2, node2) = createRouter(2)
        val (router3, node3) = createRouter(3)

        connect(router1, router2)
        connect(router2, router3)

        assertEquals(setOf(node2), router1.nodes)
        assertEquals(setOf(node1, node3), router2.nodes)
        assertEquals(setOf(node2), router3.nodes)

        router1.askForNodes()
        router3.askForNodes()

        assertEquals(setOf(node2, node3), router1.nodes)
        assertEquals(setOf(node1, node3), router2.nodes)
        assertEquals(setOf(node1, node2), router3.nodes)
    }

    /**
     * Topology:
     * (1) --> (2)
     */
    @Test
    fun routerDeliversMessageToDirectNode() {
        val (router1, node1) = createRouter(1)
        val (router2, node2) = createRouter(2)

        connect(router1, router2)

        val sentMessage12 = RoutedMessage.newBuilder()
            .setMsgId(1)
            .setSrcId(1)
            .setDestId(2)
            .setPayload(ByteString.EMPTY)
            .build()
        var receivedMessage12: RoutedMessage? = null
        var srcNode12: Node? = null

        router2.setOnMessageReceived { msg, node ->
            receivedMessage12 = msg
            srcNode12 = node
        }
        router1.sendMessage(sentMessage12, node2)

        assertEquals(node1, srcNode12)
        assertEquals(sentMessage12, receivedMessage12)
    }

    /**
     * Topology:
     * (1) -- (3) -- (4)
     *  \    /
     *   (2)
     */
    @Test
    fun routerDeliversMessageToIndirectNode() {
        val (router1, node1) = createRouter(1)
        val (router2, node2) = createRouter(2)
        val (router3, node3) = createRouter(3)
        val (router4, node4) = createRouter(4)

        connect(router1, router2)
        connect(router1, router3)
        connect(router2, router3)
        connect(router3, router4)

        val sentMessage14 = RoutedMessage.newBuilder()
            .setMsgId(1)
            .setSrcId(1)
            .setDestId(4)
            .setPayload(ByteString.EMPTY)
            .build()
        var receivedMessage14: RoutedMessage? = null

        router4.setOnMessageReceived { msg, _ ->
            receivedMessage14 = msg
        }

        assertThrows(IllegalArgumentException::class.java) {
            router1.sendMessage(sentMessage14, node4)
        }

        router1.askForNodes()
        router4.askForNodes()

        router1.sendMessage(sentMessage14, node4)
        assertEquals(sentMessage14, receivedMessage14)

        val sentMessage41 = RoutedMessage.newBuilder()
            .setMsgId(2)
            .setSrcId(4)
            .setDestId(1)
            .setPayload(ByteString.EMPTY)
            .build()
        var receivedMessage41: RoutedMessage? = null

        router1.setOnMessageReceived { msg, _ ->
            receivedMessage41 = msg
        }
        router4.sendMessage(sentMessage41, node1)

        assertEquals(sentMessage41, receivedMessage41)
    }
}