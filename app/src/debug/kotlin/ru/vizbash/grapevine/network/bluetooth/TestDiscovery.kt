package ru.vizbash.grapevine.network.bluetooth

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ServiceScoped
import kotlinx.coroutines.*
import ru.vizbash.grapevine.*
import ru.vizbash.grapevine.network.GrapevineNetwork
import ru.vizbash.grapevine.network.Router
import ru.vizbash.grapevine.network.TestNeighbor
import ru.vizbash.grapevine.storage.profile.ProfileEntity
import java.util.concurrent.Executors
import javax.inject.Inject
import kotlin.random.Random

@ServiceScoped
class TestDiscovery @Inject constructor(
    @ApplicationContext private val context: Context,
    private val router: Router,
) {
    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    private var running = false
    
    private val invisibleBots by lazy {
        MutableList(15) { i ->
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

        val visibleBots = mutableListOf<Pair<TestNeighbor, TestProfileProvider>>()

        coroutineScope.launch(Executors.newSingleThreadExecutor().asCoroutineDispatcher()) {
            launch {
                while (true) {
                    try {
                        delay(Random.nextLong(2_000, 8_000))

                        if (visibleBots.size > 12) {
                            continue
                        }

                        val bot = invisibleBots.random()
                        val neighbor = addNeighbor(bot)

                        invisibleBots.remove(bot)
                        visibleBots.add(Pair(neighbor, bot))
                        Log.i(this@TestDiscovery.TAG, "Added ${bot.profile.entity.username}")
                    } catch (e: NoSuchElementException) {
                    }
                }
            }
            launch {
                while (true) {
                    try {
                        delay(Random.nextLong(10_000, 20_000))

                        val visBot = visibleBots.random()
                        visBot.first.disconnect()

                        visibleBots.remove(visBot)
                        invisibleBots.add(visBot.second)
                        Log.i(this@TestDiscovery.TAG, "Removed ${visBot.second.profile.entity.username}")
                    } catch (e: NoSuchElementException) {
                    }
                }
            }
        }
    }

    fun stop() {
        Log.i(TAG, "Stopping test discovery")

        running = false
        coroutineScope.coroutineContext.cancelChildren()
    }

    private fun addNeighbor(profileService: ProfileProvider): TestNeighbor {
        val testRouter = Router(profileService)

        val neighbor1 = TestNeighbor()
        val neighbor2 = TestNeighbor()

        neighbor1.pair = neighbor2
        neighbor2.pair = neighbor1

        val controller = GrapevineNetwork(testRouter, profileService)

        testRouter.addNeighbor(neighbor2)
        router.addNeighbor(neighbor1)

        controller.start()

        return neighbor1
    }
}