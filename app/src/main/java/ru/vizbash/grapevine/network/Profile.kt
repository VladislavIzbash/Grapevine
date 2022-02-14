package ru.vizbash.grapevine.network

import ru.vizbash.grapevine.storage.profile.ProfileEntity
import java.security.PrivateKey
import java.security.PublicKey

data class Profile(
    val entity: ProfileEntity,
    val privateKey: PrivateKey,
    val dhPublicKey: PublicKey,
    val dhPrivateKey: PrivateKey,
)