package ru.vizbash.grapevine

import android.graphics.Bitmap
import ru.vizbash.grapevine.db.identity.DecryptedIdentity
import ru.vizbash.grapevine.db.identity.Identity
import ru.vizbash.grapevine.db.identity.IdentityDao
import java.lang.IllegalStateException
import java.security.KeyPairGenerator
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class AuthService @Inject constructor(
    private val identityDao: IdentityDao,
) {
    var currentIdent: DecryptedIdentity? = null
        private set

    fun identityList() = identityDao.getAll()

    fun newIdentity(username: String, password: String, photo: Bitmap?) {
        val keyGen = KeyPairGenerator.getInstance("RSA").apply {
            initialize(1024)
        }
        val keyPair = keyGen.genKeyPair()

        identityDao.insert(Identity(
            Random.nextLong(),
            username,
            keyPair.public,
            encryptPrivateKey(keyPair.private, password),
            photo,
        ))
    }

    fun tryLogin(identity: Identity, password: String): Boolean {
        val privKey = decryptPrivateKey(identity.privateKeyEnc, password)
        if (privKey != null) {
            currentIdent = DecryptedIdentity(identity, privKey)
            return true
        } else {
            return false
        }
    }
}