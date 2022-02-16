package ru.vizbash.grapevine.network

import ru.vizbash.grapevine.network.Neighbor
import ru.vizbash.grapevine.network.SourceType
import ru.vizbash.grapevine.network.messages.direct.DirectMessage
import kotlin.math.absoluteValue
import kotlin.random.Random

class TestNeighbor : Neighbor {
    lateinit var pair: TestNeighbor

    private val id = Random.nextInt().absoluteValue

    private var receiveCb: ((DirectMessage) -> Unit)? = null
    private var disconnectCb: () -> Unit = {}

    private var receiveBuffer = mutableListOf<DirectMessage>()

    override val sourceType = SourceType.BLUETOOTH

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

    fun disconnect() {
        pair.disconnectCb()
    }

    override fun equals(other: Any?) = other is TestNeighbor
            && other.id == id

    override fun hashCode() = id

    override fun toString() = "test_node_$id"
}