package ru.vizbash.grapevine

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.vizbash.grapevine.network.Profile
import ru.vizbash.grapevine.storage.profile.ProfileEntity
import ru.vizbash.grapevine.storage.profile.ProfileDao
import java.security.Security
import javax.crypto.interfaces.DHPublicKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class ProfileService @Inject constructor(
    private val profileDao: ProfileDao,
) {
    lateinit var currentProfile: Profile
        private set

    val profileList
        get() = profileDao.getAll()

    suspend fun createProfileAndLogin(
        username: String,
        password: String,
        photo: Bitmap?,
    ) = withContext(Dispatchers.Default) {
        val keyPair = createRsaKeyPair()

        val profile = ProfileEntity(
            Random.nextLong(),
            username,
            keyPair.public,
            aesEncrypt(keyPair.private.encoded, generatePasswordSecret(password)),
            photo,
        )
        profileDao.insert(profile)

        val dhKeyPair = createDhKeyPair()
        currentProfile = Profile(profile, keyPair.private, dhKeyPair.public, dhKeyPair.private)
    }

    suspend fun tryLogin(profile: ProfileEntity, password: String) = withContext(Dispatchers.Default) {
        val privKey = aesDecrypt(profile.privateKeyEnc, generatePasswordSecret(password))

        val dhKeyPair = createDhKeyPair()
        if (privKey != null) {
            currentProfile = Profile(
                profile,
                decodeRsaPrivateKey(privKey),
                dhKeyPair.public,
                dhKeyPair.private,
            )
            true
        } else {
            false
        }
    }

}