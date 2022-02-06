package ru.vizbash.grapevine.network

import dagger.hilt.android.scopes.ServiceScoped
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.shareIn
import javax.inject.Inject

@ServiceScoped
class NetworkController @Inject constructor(private val router: Router) {
    val nodeList: Flow<Collection<Node>> = callbackFlow {
        router.setOnNodesUpdated { trySend(router.nodes) }
        awaitClose { router.setOnNodesUpdated { } }
    }.shareIn(CoroutineScope(Dispatchers.Main), SharingStarted.WhileSubscribed())
}