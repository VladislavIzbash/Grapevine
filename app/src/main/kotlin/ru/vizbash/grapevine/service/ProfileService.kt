package ru.vizbash.grapevine.service

import android.content.Context
import android.graphics.Bitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import ru.vizbash.grapevine.network.Node
import ru.vizbash.grapevine.storage.StoredProfile
import ru.vizbash.grapevine.storage.node.NodeDao
import ru.vizbash.grapevine.util.*
import java.io.IOException
import javax.crypto.interfaces.DHPrivateKey
import javax.crypto.interfaces.DHPublicKey
import javax.inject.Singleton
import kotlin.random.Random

@OptIn(ExperimentalSerializationApi::class)
@Singleton
class ProfileService(
    @ApplicationContext private val context: Context,
    private val nodeDao: NodeDao,
) : ProfileProvider, NodeVerifier {
    override lateinit var profile: Profile

    private var storedProfile: StoredProfile? = null

    val hasProfile get() = storedProfile != null

    val storedName get() = storedProfile!!.username

    init {
        try {
            context.openFileInput("profile").use {
                storedProfile = Cbor.decodeFromByteArray(it.readBytes())
            }
        } catch (e: IOException) {
        }
    }

    suspend fun tryLogin(password: String): Boolean = withContext(Dispatchers.Default) {
        val storedProfile = storedProfile ?: return@withContext false

        val privKey = aesDecrypt(storedProfile.encryptedPrivKey, generatePasswordSecret(password))
            ?: return@withContext false

        val sessionKeys = generateSessionKeys()

        profile = Profile(
            nodeId = storedProfile.nodeId,
            username = storedProfile.username,
            pubKey = storedProfile.pubKey,
            privKey = decodeRsaPrivateKey(privKey),
            sessionPubKey = sessionKeys.public as DHPublicKey,
            sessionPrivKey = sessionKeys.private as DHPrivateKey,
            photo = storedProfile.photo?.let { decodeBitmap(it) },
        )

        true
    }

    suspend fun loginWithNewProfile(username: String, password: String, photo: Bitmap?) {
        withContext(Dispatchers.Default) {
            val keyPair = generateRsaKeys()
            val sessionKeyPair = generateSessionKeys()

            profile = Profile(
                nodeId = Random.nextLong(),
                username = username,
                pubKey = keyPair.public,
                privKey = keyPair.private,
                sessionPubKey = sessionKeyPair.public as DHPublicKey,
                sessionPrivKey = sessionKeyPair.private as DHPrivateKey,
                photo = photo,
            )
            saveProfile(password)
        }
    }

    private fun saveProfile(password: String) {
        storedProfile = StoredProfile(
            nodeId = profile.nodeId,
            username = profile.username,
            pubKey = profile.pubKey,
            encryptedPrivKey = aesEncrypt(profile.privKey.encoded, generatePasswordSecret(password)),
            photo = profile.photo?.let { encodeBitmap(it) },
        )
    }

    override suspend fun checkNode(node: Node): Boolean {
        val knownNode = nodeDao.getById(node.id) ?: return true
        return knownNode.pubKey == node.pubKey
    }
}