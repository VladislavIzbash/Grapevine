package ru.vizbash.grapevine.storage.profile

import java.security.PrivateKey

data class DecryptedProfile(
    val base: Profile,
    val privateKey: PrivateKey,
)