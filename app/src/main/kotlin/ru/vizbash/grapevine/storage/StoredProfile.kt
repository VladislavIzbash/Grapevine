package ru.vizbash.grapevine.storage

import kotlinx.serialization.Serializable
import java.security.PublicKey

@Serializable
data class StoredProfile(
    val nodeId: Long,
    val username: String,
    val pubKey: PublicKey,
    val encryptedPrivKey: ByteArray,
    val photo: ByteArray?,
)
