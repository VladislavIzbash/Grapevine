package ru.vizbash.grapevine.network

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class RouterTest {
    /**
     * Topology:
     * (1) -- (2)
     */
    @Test
    fun twoNodesCanReachEachOtherDirectly() {
        val (router1, node1) = createRouter(mockProfileService(1))
        val (router2, node2) = createRouter(mockProfileService(2))

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
    fun routersHandleDisconnectionDirectly() {
        val (router1, node1) = createRouter(mockProfileService(1))
        val (router2, node2) = createRouter(mockProfileService(2))
        val (router3, node3) = createRouter(mockProfileService(3))

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
    fun routersHandleDisconnectionIndirectly() {
        val (router1, node1) = createRouter(mockProfileService(1))
        val (router2, node2) = createRouter(mockProfileService(2))
        val (router3, _) = createRouter(mockProfileService(3))

        connect(router1, router2)
        val conn23 = connect(router2, router3)

        router1.askForNodes()
        router3.askForNodes()

        conn23.first.disconnect()
        conn23.second.disconnect()

        router1.askForNodes()

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
        val (router1, node1) = createRouter(mockProfileService(1))
        val (router2, node2) = createRouter(mockProfileService(2))
        val (router3, node3) = createRouter(mockProfileService(3))

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
        val (router1, node1) = createRouter(mockProfileService(1))
        val (router2, node2) = createRouter(mockProfileService(2))

        connect(router1, router2)

        var receivedMessage12: Router.ReceivedMessage? = null

        router2.setOnMessageReceived { msg ->
            receivedMessage12 = msg
        }
        val payload12 = byteArrayOf(1)
        router1.sendMessage(payload12, byteArrayOf(1), node2)

        assertArrayEquals(payload12, receivedMessage12?.payload)
        assertEquals(node1, receivedMessage12?.sender)
    }

    /**
     * Topology:
     * (1) -- (3) -- (4)
     *  \    /
     *   (2)
     */
    @Test
    fun routerDeliversMessageToIndirectNode() {
        val (router1, node1) = createRouter(mockProfileService(1))
        val (router2, _) = createRouter(mockProfileService(2))
        val (router3, _) = createRouter(mockProfileService(3))
        val (router4, node4) = createRouter(mockProfileService(4))

        connect(router1, router2)
        connect(router1, router3)
        connect(router2, router3)
        connect(router3, router4)

        var receivedMessage14: Router.ReceivedMessage? = null
        router4.setOnMessageReceived { msg ->
            receivedMessage14 = msg
        }

        assertThrows(IllegalArgumentException::class.java) {
            router1.sendMessage(byteArrayOf(1), byteArrayOf(2), node4)
        }

        router1.askForNodes()
        router4.askForNodes()

        router1.sendMessage(byteArrayOf(1), byteArrayOf(2), node4)
        assertNotNull(receivedMessage14)

        var receivedMessage41: Router.ReceivedMessage? = null
        router1.setOnMessageReceived { msg ->
            receivedMessage41 = msg
        }
        router4.sendMessage(byteArrayOf(1), byteArrayOf(2), node1)

        assertNotNull(receivedMessage41)
    }

    /**
     * Topology:
     * (1) -- (2) -- (3)
     */
    @Test
    fun routersTransferMultipartMessages() {
        val (router1, node1) = createRouter(mockProfileService(1))
        val (router2, node2) = createRouter(mockProfileService(2))
        val (router3, node3) = createRouter(mockProfileService(3))

        connect(router1, router2)
        connect(router2, router3)

        router1.askForNodes()
        router3.askForNodes()

        var receivedMessage13: Router.ReceivedMessage? = null
        router3.setOnMessageReceived { msg ->
            receivedMessage13 = msg
        }

        val payload13 = Random.nextBytes(26024)
        router1.sendMessage(payload13, byteArrayOf(1), node3)

        assertArrayEquals(payload13, receivedMessage13?.payload)
    }
}