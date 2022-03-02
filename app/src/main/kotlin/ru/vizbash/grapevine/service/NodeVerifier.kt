package ru.vizbash.grapevine.service

import ru.vizbash.grapevine.network.Node

interface NodeVerifier {
    suspend fun checkNode(node: Node): Boolean
}
