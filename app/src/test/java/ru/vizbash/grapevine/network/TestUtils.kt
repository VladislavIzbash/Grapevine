package ru.vizbash.grapevine.network

import ru.vizbash.grapevine.*
import ru.vizbash.grapevine.storage.profile.ProfileEntity

fun createProfileService(nodeId: Long): IProfileService {
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
    return TestProfileService(profile)
}

fun createRouter(profileService: IProfileService): Pair<Router, Node> {
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