package ru.vizbash.grapevine

import com.google.protobuf.MessageLite
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.vizbash.grapevine.network.Node
import ru.vizbash.grapevine.network.Router
import ru.vizbash.grapevine.network.transport.Neighbor
import java.lang.Exception
import java.security.KeyPairGenerator

@ExperimentalCoroutinesApi
class RouterTest {
    private class TestNeighbor(
        private val tx: SendChannel<MessageLite>,
        private val rx: ReceiveChannel<MessageLite>,
    ): Neighbor {
        override suspend fun send(msg: MessageLite) {
            try {
                println("sending using ${hashCode()}")
                tx.send(msg)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                println("sent using ${hashCode()}")
            }
        }

        override suspend fun receive(): MessageLite {
            println("recv")
            return rx.receive()
        }
    }

    private fun createTestNeighbors(): Pair<Neighbor, Neighbor> {
        val chan1 = Channel<MessageLite>()
        val chan2 = Channel<MessageLite>()

        return Pair(TestNeighbor(chan1, chan2), TestNeighbor(chan2, chan1))
    }

    @Test
    fun router_AcceptsNeighbors() = runBlocking {
        val keyGen = KeyPairGenerator.getInstance("RSA")
        keyGen.initialize(1024)

        val nodes = listOf(
            Node(1, "node1", keyGen.genKeyPair().public),
            Node(2, "node2", keyGen.genKeyPair().public),
            Node(3, "node3", keyGen.genKeyPair().public),
        )

        val discChans = List(3) { Channel<Neighbor>() }
        val routers = nodes.zip(discChans).map { Router(it.first, it.second) }

        val routerJobs = mutableListOf<Job>()
        for (router in routers) {
            routerJobs += launch { router.run() }
        }

        delay(100)

        val neighbors = List(3) { createTestNeighbors() }

        discChans[0].send(neighbors[0].first)
        discChans[1].send(neighbors[0].second)

        delay(200)

//        routerJobs.forEach {job -> job.cancelAndJoin() }

        assertEquals(listOf(nodes[1]), routers[0].nodes().toList())
//        assertEquals(listOf(nodes[0]), routers[1].nodes().toList())
//        assertTrue(routers[2].nodes().isEmpty())
    }
}