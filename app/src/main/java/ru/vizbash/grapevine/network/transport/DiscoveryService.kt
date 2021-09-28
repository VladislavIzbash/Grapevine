package ru.vizbash.grapevine.network.transport

interface DiscoveryService {
    fun setOnDiscovered(onDiscovered: (Neighbor) -> Unit)

    fun start()

    fun stop()
}