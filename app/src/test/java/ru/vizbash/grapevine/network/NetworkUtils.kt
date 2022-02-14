package ru.vizbash.grapevine.network

import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import ru.vizbash.grapevine.ProfileService
import ru.vizbash.grapevine.createDhKeyPair
import ru.vizbash.grapevine.createRsaKeyPair
import ru.vizbash.grapevine.storage.profile.ProfileEntity

fun mockProfileService(nodeId: Long): ProfileService {
    val keyPair = createRsaKeyPair()
    val dhKeyPair = createDhKeyPair()

    val profile = Profile(
        ProfileEntity(nodeId, "node$nodeId", keyPair.public, ByteArray(0), null),
        keyPair.private,
        dhKeyPair.public,
        dhKeyPair.private,
    )

    return mock {
        on { currentProfile } doReturn profile
    }
}

fun createRouter(profileService: ProfileService): Pair<Router, Node> {
    return Pair(Router(profileService), Node(profileService.currentProfile))
}

fun connect(router1: Router, router2: Router): Pair<TestNeighbor, TestNeighbor> {
    val neighbor1 = TestNeighbor()
    val neighbor2 = TestNeighbor()
    neighbor1.pair = neighbor2
    neighbor2.pair = neighbor1

    router1.addNeighbor(neighbor1)
    router2.addNeighbor(neighbor2)

    return Pair(neighbor1, neighbor2)
}