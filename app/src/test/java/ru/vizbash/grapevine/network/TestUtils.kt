package ru.vizbash.grapevine.network

import ru.vizbash.grapevine.*
import ru.vizbash.grapevine.service.Profile
import ru.vizbash.grapevine.service.ProfileProvider
import ru.vizbash.grapevine.storage.profile.ProfileEntity
import ru.vizbash.grapevine.util.createDhKeyPair
import ru.vizbash.grapevine.util.createRsaKeyPair

fun createProfileService(nodeId: Long): ProfileProvider {
    val keyPair = createRsaKeyPair()
    val dhKeyPair = createDhKeyPair()

    val profile = Profile(
        ProfileEntity(
            nodeId,
            "node$nodeId",
            keyPair.public,
            ByteArray(0),
            null,
        ),
        keyPair.private,
        dhKeyPair.public,
        dhKeyPair.private,
    )
    return TestProfileProvider(profile)
}

fun createRouter(profileService: ProfileProvider): Pair<Router, Node> {
    return Pair(Router(profileService), Node(profileService.profile))
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