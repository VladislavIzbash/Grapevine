package ru.vizbash.grapevine.network

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import ru.vizbash.grapevine.ProfileService

@ExperimentalCoroutinesApi
class NetworkControllerTest {
    private lateinit var profiles: List<ProfileService>
    private lateinit var routers: List<Pair<Router, Node>>
    private lateinit var controllers: List<NetworkController>

    @Before
    fun setup() {
        profiles = List(3) { i -> mockProfileService(i.toLong() + 1) }
        routers = profiles.map { createRouter(it) }

        connect(routers[0].first, routers[1].first)
        connect(routers[1].first, routers[2].first)

        controllers = profiles.zip(routers).map { (profile, router) ->
            NetworkController(router.first, profile).apply { start() }
        }

        routers.forEach { it.first.askForNodes() }
    }

    @After
    fun teardown() {
        controllers.forEach(NetworkController::stop)
    }

    @Test(timeout = 1000)
    fun onlineNodesReturnsCorrectSet() = runBlocking {
        assertEquals(
            setOf(routers[1].second, routers[2].second),
            controllers[0].onlineNodes.value,
        )
        assertEquals(
            setOf(routers[0].second, routers[2].second),
            controllers[1].onlineNodes.value,
        )
        assertEquals(
            setOf(routers[0].second, routers[1].second),
            controllers[2].onlineNodes.value,
        )
    }

    @Test(timeout = 1000)
    fun controllersTransferTextMessage() = runBlocking {
        controllers[0].sendText("Hello", routers[2].second)

        val msg = controllers[2].textMessages.first()
        assertEquals("Hello", msg.text)
    }
}