package ru.vizbash.grapevine.network

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import ru.vizbash.grapevine.TAG
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkController @Inject constructor(private val router: Router) {
    companion object {
        private const val ASK_INTERVAL_MS = 30000L
    }

    private var coroutineScope = CoroutineScope(Dispatchers.Default)

    private var running = false

    val nodeList: Flow<Collection<Node>> = callbackFlow {
        router.setOnNodesUpdated {
            trySend(router.nodes)
        }
        awaitClose {
            router.setOnNodesUpdated { }
        }
    }.shareIn(coroutineScope, SharingStarted.WhileSubscribed())

    fun start() {
        if (running) {
            return
        }

        Log.i(TAG, "Started controller")

        coroutineScope.launch {
            while (true) {
                delay(ASK_INTERVAL_MS)
                router.askForNodes()
            }
        }
        running = true
    }

    fun stop() {
        if (running) {
            running = false
            coroutineScope.cancel()
            Log.i(TAG, "Stopped controller")
        }
    }
}