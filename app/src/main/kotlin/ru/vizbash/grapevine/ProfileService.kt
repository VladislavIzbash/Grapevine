package ru.vizbash.grapevine

import android.content.Context
import android.graphics.Bitmap
import androidx.room.Room
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.vizbash.grapevine.network.Node
import ru.vizbash.grapevine.storage.UserDatabase
import ru.vizbash.grapevine.storage.contacts.ContactEntity
import ru.vizbash.grapevine.storage.profile.ProfileEntity
import ru.vizbash.grapevine.storage.profile.ProfileDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class ProfileService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profileDao: ProfileDao,
) : ProfileProvider {
    override lateinit var profile: Profile
        private set

    val profileList
        get() = profileDao.getAll()

    private lateinit var userDb: UserDatabase

    val contactList
        get() = userDb.contactDao().getAll()

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
        this@ProfileService.profile = Profile(profile, keyPair.private, dhKeyPair.public, dhKeyPair.private)

        loadUserDb()
    }

    suspend fun tryLogin(profile: ProfileEntity, password: String) = withContext(Dispatchers.Default) {
        val privKey = aesDecrypt(profile.privateKeyEnc, generatePasswordSecret(password))

        val dhKeyPair = createDhKeyPair()
        if (privKey != null) {
            this@ProfileService.profile = Profile(
                profile,
                decodeRsaPrivateKey(privKey),
                dhKeyPair.public,
                dhKeyPair.private,
            )
            loadUserDb()
            true
        } else {
            false
        }
    }

    private fun loadUserDb() {
        userDb = Room.databaseBuilder(
            context,
            UserDatabase::class.java,
            "profile_${profile.entity.nodeId}",
        ).build()
    }

    suspend fun addContact(node: Node, photo: Bitmap?, state: ContactEntity.State) {
        userDb.contactDao().insert(ContactEntity(
            node.id,
            node.username,
            node.publicKey,
            photo,
            state,
        ))
    }
}