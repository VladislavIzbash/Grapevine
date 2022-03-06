package ru.vizbash.grapevine.service

import android.content.Context
import android.graphics.Bitmap
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
import ru.vizbash.grapevine.storage.messages.MessageFile
import ru.vizbash.grapevine.storage.messages.MessageWithOrig
import ru.vizbash.grapevine.storage.profile.ProfileDao
import ru.vizbash.grapevine.storage.profile.ProfileEntity
import ru.vizbash.grapevine.util.*
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue
import kotlin.random.Random

@Singleton
class ProfileService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profileDao: ProfileDao,
) : ProfileProvider, NodeVerifier {
    override lateinit var profile: Profile
        private set

    val profileList
        get() = profileDao.getAll()

    private lateinit var userDb: UserDatabase

    suspend fun createProfile(
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
    }

    suspend fun tryLogin(profile: ProfileEntity, password: String) = withContext(Dispatchers.Default) {
        val privKey = aesDecrypt(profile.privateKeyEnc, generatePasswordSecret(password))
            ?: return@withContext false

        val dhKeyPair = createDhKeyPair()
        this@ProfileService.profile = Profile(
            profile,
            decodeRsaPrivateKey(privKey),
            dhKeyPair.public,
            dhKeyPair.private,
        )
        loadUserDb()

        true
    }

    private fun loadUserDb() {
        userDb = Room.databaseBuilder(
            context,
            UserDatabase::class.java,
            "profile_${profile.entity.nodeId.absoluteValue}",
        ).build()
    }

    override suspend fun checkNode(node: Node): Boolean {
        val contact = userDb.contactDao().getById(node.id) ?: return true
        return contact.publicKey == node.publicKey
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

    fun getContactMessages(contact: ContactEntity): PagingSource<Int, MessageWithOrig> {
        return userDb.messageDao().getAllForChat(contact.nodeId)
    }

    suspend fun getContactFailedMessages(contact: ContactEntity): List<MessageEntity> {
        return userDb.messageDao().getAllForChatWithState(
            contact.nodeId,
            MessageEntity.State.DELIVERY_FAILED,
        )
    }

    suspend fun addReceivedMessage(contact: ContactEntity, message: TextMessage): MessageEntity {
        val entity = MessageEntity(
            id = message.msgId,
            timestamp = Date(message.timestamp * 1000),
            chatId = contact.nodeId,
            senderId = contact.nodeId,
            text = message.text,
            originalMessageId = if (message.originalMsgId == 0L) null else message.originalMsgId,
            state = MessageEntity.State.DELIVERED,
            file = if (message.hasFile) MessageFile(
                uri = null,
                name = message.fileName,
                size = message.fileSize,
                downloaded = false,
            ) else null,
        )

        userDb.messageDao().insert(entity)
        return entity
    }

    suspend fun addSentMessage(
        id: Long,
        contact: ContactEntity,
        text: String,
        orig: MessageEntity?,
        file: MessageFile?,
    ): MessageEntity {
        val entity = MessageEntity(
            id = id,
            timestamp = Calendar.getInstance().time,
            chatId = contact.nodeId,
            senderId = profile.entity.nodeId,
            text = text,
            originalMessageId = orig?.id,
            state = MessageEntity.State.SENT,
            file = file,
        )
        userDb.messageDao().insert(entity)
        return entity
    }

    suspend fun setMessageState(msgId: Long, state: MessageEntity.State) {
        userDb.messageDao().setState(msgId, state)
    }

    fun getLastMessage(contact: ContactEntity): Flow<MessageEntity?> {
        return userDb.messageDao().getLastMessage(contact.nodeId)
    }
}