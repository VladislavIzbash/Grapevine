package ru.vizbash.grapevine

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.vizbash.grapevine.storage.profile.DecryptedProfile
import ru.vizbash.grapevine.storage.profile.Profile
import ru.vizbash.grapevine.storage.profile.ProfileDao
import java.security.KeyPairGenerator
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class AuthService @Inject constructor(
    private val profileDao: ProfileDao,
) {
    var currentIdent: DecryptedProfile? = null
        private set

    fun getProfileList() = profileDao.getAll()

    suspend fun createProfileAndLogin(
        username: String,
        password: String,
        photo: Bitmap?,
    ) {
        val keyGen = KeyPairGenerator.getInstance("RSA").apply {
            initialize(1024)
        }
        val keyPair = keyGen.genKeyPair()

        val profile = Profile(
            Random.nextLong(),
            username,
            keyPair.public,
            encryptPrivateKey(keyPair.private, password),
            photo,
        )
        profileDao.insert(profile)

        currentIdent = DecryptedProfile(profile, keyPair.private)
    }

    suspend fun tryLogin(profile: Profile, password: String) = withContext(Dispatchers.Default) {
        val privKey = decryptPrivateKey(profile.privateKeyEnc, password)
        if (privKey != null) {
            currentIdent = DecryptedProfile(profile, privKey)
            true
        } else {
            false
        }
    }

}