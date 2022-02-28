package ru.vizbash.grapevine.network.bluetooth

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ServiceScoped
import kotlinx.coroutines.*
import ru.vizbash.grapevine.*
import ru.vizbash.grapevine.network.GrapevineNetwork
import ru.vizbash.grapevine.network.Node
import ru.vizbash.grapevine.network.Router
import ru.vizbash.grapevine.network.TestNeighbor
import ru.vizbash.grapevine.storage.profile.ProfileEntity
import java.util.concurrent.Executors
import javax.inject.Inject
import kotlin.random.Random

@ServiceScoped
class TestDiscovery @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userRouter: Router,
    private val userProfileProvider: ProfileProvider,
) {
    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    private var running = false
    
    private val profilePool by lazy {
        MutableList(12) { i ->
            val keyPair = createRsaKeyPair()
            val dhKeyPair = createDhKeyPair()

            val profile = Profile(
                ProfileEntity(
                    Random.nextLong(),
                    "Bot ${i + 1}",
                    keyPair.public,
                    ByteArray(0),
                    BitmapFactory.decodeResource(context.resources, R.mipmap.droid),
                ),
                keyPair.private,
                dhKeyPair.public,
                dhKeyPair.private,
            )
            TestProfileProvider(profile)
        }
    }

    fun start() {
        if (running) {
            return
        }

        Log.i(TAG, "Starting test discovery")

        running = true

        coroutineScope.launch(Executors.newSingleThreadExecutor().asCoroutineDispatcher()) {
            while (true) {
                delay(2_000)
                launch {
                    spawnBot()
                }
                delay(Random.nextLong(20_000, 30_000))
            }
        }
    }

    fun stop() {
        Log.i(TAG, "Stopping test discovery")

        running = false
        coroutineScope.coroutineContext.cancelChildren()
    }

    private suspend fun spawnBot() = coroutineScope {
        if (profilePool.isEmpty()) {
            return@coroutineScope
        }

        val profileProvider = profilePool.random()

        val botRouter = Router(profileProvider)

        val neighbor1 = TestNeighbor()
        val neighbor2 = TestNeighbor()

        neighbor1.pair = neighbor2
        neighbor2.pair = neighbor1

        val network = GrapevineNetwork(botRouter, profileProvider)
        network.start()

        botRouter.addNeighbor(neighbor2)
        userRouter.addNeighbor(neighbor1)

        launch {
            launch {
                delay(20_000)
                try {
                    network.sendContactInvitation(Node(userProfileProvider.profile))
                } catch (e: GVException) {
                }
            }

            launch {
                network.contactInvitations.collect { node ->
                    delay(4_000)
                    try {
                        network.sendContactInvitationAnswer(node, true)
                    } catch (e: GVException) {
                    }
                }
            }

            launch {
                try {
                    network.textMessages.collect { (msg, node) ->
                        delay(1_000)
                        network.sendReadConfirmation(msg.msgId, node)

                        delay(2_000)
                        if (Random.nextInt(0, 2) == 0) {
                            network.sendTextMessage(
                                Random.nextLong(),
                                "Тестируй в другую сторону",
                                node,
                                null,
                                null,
                            )
                        }
                    }
                } catch (e: GVException) {
                }
            }
        }

        delay(Random.nextLong(60_000, 120_000))

        network.stop()
        neighbor1.disconnect()
        neighbor2.disconnect()

        profilePool.add(profileProvider)

        cancel()
    }
}