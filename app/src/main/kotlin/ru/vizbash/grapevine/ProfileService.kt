package ru.vizbash.grapevine

import android.content.Context
import android.graphics.Bitmap
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.room.Room
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import ru.vizbash.grapevine.network.Node
import ru.vizbash.grapevine.network.messages.routed.TextMessage
import ru.vizbash.grapevine.storage.UserDatabase
import ru.vizbash.grapevine.storage.contacts.ContactEntity
import ru.vizbash.grapevine.storage.messages.MessageEntity
import ru.vizbash.grapevine.storage.profile.ProfileEntity
import ru.vizbash.grapevine.storage.profile.ProfileDao
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue
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
            "profile_${profile.entity.nodeId.absoluteValue}",
        ).build()
    }

    val contactList get() = userDb.contactDao().getAll()

    suspend fun getContact(id: Long) = userDb.contactDao().getById(id)

    suspend fun addContact(node: Node, photo: Bitmap?, state: ContactEntity.State) {
        userDb.contactDao().insert(ContactEntity(
            node.id,
            node.username,
            node.publicKey,
            photo,
            state,
        ))
    }

    suspend fun deleteContact(contact: ContactEntity) {
        userDb.contactDao().delete(contact)
    }

    suspend fun setContactState(contact: ContactEntity, state: ContactEntity.State) {
        userDb.contactDao().update(contact.copy(state = state))
    }

    fun getContactMessages(contact: ContactEntity): PagingSource<Int, MessageEntity> {
        return userDb.messageDao().getAllForChat(contact.nodeId)
    }

    suspend fun addReceivedMessage(contact: ContactEntity, message: TextMessage) {
        userDb.messageDao().insert(MessageEntity(
            id = message.msgId,
            timestamp = Date(message.timestamp * 1000),
            chatId = contact.nodeId,
            isIngoing = true,
            text = message.text,
            originalMessageId = null,
            state = MessageEntity.State.DELIVERED,
            hasFile = false,
            filePath = null,
        ))
    }

    suspend fun addSentMessage(contact: ContactEntity, text: String, id: Long) {
        userDb.messageDao().insert(MessageEntity(
            id = id,
            timestamp = Calendar.getInstance().time,
            chatId = contact.nodeId,
            isIngoing = false,
            text = text,
            originalMessageId = null,
            state = MessageEntity.State.SENT,
            hasFile = false,
            filePath = null,
        ))
    }

    suspend fun setMessageState(msgId: Long, state: MessageEntity.State) {
        userDb.messageDao().setState(msgId, state)
    }
}