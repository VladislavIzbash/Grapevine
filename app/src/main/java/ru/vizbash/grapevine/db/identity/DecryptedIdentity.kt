package ru.vizbash.grapevine.db.identity

import java.security.PrivateKey

data class DecryptedIdentity(
    val base: Identity,
    val privateKey: PrivateKey,
)