package ru.vizbash.grapevine

import com.google.protobuf.MessageLite
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert
import org.junit.Test
import ru.vizbash.grapevine.network.Node
import ru.vizbash.grapevine.network.Router
import ru.vizbash.grapevine.network.transport.DiscoveryService
import ru.vizbash.grapevine.network.transport.Neighbor
import java.security.KeyPairGenerator
import java.util.*
import org.junit.Assert.*
import kotlin.collections.ArrayDeque

class RouterTest {
    class TestDiscovery(private val neighbors: List<Neighbor>) : DiscoveryService {
        override fun setOnDiscovered(onDiscovered: (Neighbor) -> Unit) {
            neighbors.forEach(onDiscovered)
        }
    }

    class TestNeighbor(
        private val tx: LinkedList<MessageLite>,
        private val rx: LinkedList<MessageLite>,
    ) : Neighbor {
        private var onReceived: (MessageLite) -> Unit = {}

        override fun send(msg: MessageLite) {
            tx.add(msg)
        }

        override fun setOnReceived(onReceived: (MessageLite) -> Unit) {
            this.onReceived = onReceived
        }

        fun notifyRouter() {
            while (!rx.isEmpty()) {
                onReceived(rx.remove())
            }
        }
    }

    private fun createNeighborPair(): Pair<TestNeighbor, TestNeighbor> {
        val q1 = LinkedList<MessageLite>()
        val q2 = LinkedList<MessageLite>()

        return Pair(TestNeighbor(q1, q2), TestNeighbor(q2, q1))
    }


    @Test
    fun router_AcceptsNeighbors() {
        val keyGen = KeyPairGenerator.getInstance("RSA")
        keyGen.initialize(1024)

        val nodes = listOf(
            Node(1, "node1", keyGen.genKeyPair().public),
            Node(2, "node2", keyGen.genKeyPair().public),
            Node(3, "node3", keyGen.genKeyPair().public),
        )

        val routers = nodes.map(::Router).toList()

        val neighbors = List(3) { createNeighborPair() }

        val discoveries = listOf(
            TestDiscovery(listOf(neighbors[0].first)),
            TestDiscovery(listOf(neighbors[0].second)),
            TestDiscovery(listOf()),
        )

        val threads = mutableListOf<Thread>()
        routers.forEachIndexed { i, router ->
            router.addDiscovery(discoveries[i])
            threads.add(Thread(router))
            threads[i].start()
        }

        //Thread.sleep(100)
        for (pair in neighbors) {
            pair.first.notifyRouter()
            pair.second.notifyRouter()
        }

        //Thread.sleep(100)
        routers.forEachIndexed { i, router ->
            router.stop()
            threads[i].join()
        }

        assertEquals(listOf(nodes[1]), routers[0].nodes.toList())
        assertEquals(listOf(nodes[0]), routers[1].nodes.toList())
        assertTrue(routers[2].nodes.isEmpty())
    }
}