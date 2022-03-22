package ru.vizbash.grapevine.network

import kotlinx.coroutines.flow.StateFlow
import ru.vizbash.grapevine.GvNodeNotAvailableException

interface NodeProvider {
    val availableNodes: StateFlow<List<Node>>

    fun get(id: Long): Node? = availableNodes.value.find { it.id == id }

    fun getOrThrow(id: Long) = get(id) ?: throw GvNodeNotAvailableException()
}