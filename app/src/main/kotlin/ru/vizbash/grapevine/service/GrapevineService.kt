package ru.vizbash.grapevine.service

import android.graphics.Bitmap
import android.util.Log
import dagger.hilt.android.scopes.ServiceScoped
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.first
import ru.vizbash.grapevine.network.GrapevineNetwork
import ru.vizbash.grapevine.network.Node
import ru.vizbash.grapevine.storage.chats.ChatEntity
import ru.vizbash.grapevine.storage.contacts.ContactEntity
import ru.vizbash.grapevine.storage.messages.MessageEntity
import ru.vizbash.grapevine.storage.messages.MessageFile
import ru.vizbash.grapevine.util.GVException
import ru.vizbash.grapevine.util.TAG
import javax.inject.Inject
import kotlin.random.Random

@ServiceScoped
class GrapevineService @Inject constructor(
    private val profileService: ProfileService,
    private val grapevineNetwork: GrapevineNetwork,
) {
    private var started = false
    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    fun start() {
        check(!started)

        started = true

        grapevineNetwork.start()

        Log.i(TAG, "Started")

        listOf(
            this::redeliverFailedMessages,
            this::receiveContactInvitations,
            this::receiveChatInvitations,
            this::receiveInvitationAnswers,
            this::receiveTextMessages,
            this::receiveReadConfirmations,
        ).forEach {
            coroutineScope.launch(Dispatchers.Default) { it() }
        }
    }

    fun stop() {
        coroutineScope.cancel()
        grapevineNetwork.stop()
        Log.i(TAG, "Stopped")
    }

    val availableNodes = grapevineNetwork.availableNodes

    val currentProfile get() = profileService.profile.entity

    private val photoCache = mutableMapOf<Node, Bitmap?>()

    suspend fun fetchNodePhoto(node: Node) = photoCache.getOrPut(node) {
        grapevineNetwork.fetchNodePhoto(node)
    }

    val contactList get() = profileService.contactList

    suspend fun getContact(id: Long) = profileService.getContact(id)

    suspend fun loadPhoto(id: Long) = profileService.loadPhoto(id)

    suspend fun sendContactInvitation(node: Node) {
        grapevineNetwork.sendContactInvitation(node)
        profileService.addContact(
            node,
            grapevineNetwork.fetchNodePhoto(node),
            ContactEntity.State.OUTGOING,
        )
    }

    suspend fun cancelContactInvitation(contact: ContactEntity) {
        profileService.deleteContact(contact)
    }

    suspend fun acceptContact(contact: ContactEntity) {
        profileService.setContactState(contact, ContactEntity.State.ACCEPTED)

        grapevineNetwork.lookupNode(contact.nodeId)?.let {
            grapevineNetwork.sendContactInvitationAnswer(it, true)
        }
    }

    suspend fun rejectContact(contact: ContactEntity) {
        profileService.deleteContact(contact)

        grapevineNetwork.lookupNode(contact.nodeId)?.let {
            grapevineNetwork.sendContactInvitationAnswer(it, false)
        }
    }

    val chatList get() = profileService.chatList

    suspend fun getChat(id: Long) = profileService.getChat(id)

    suspend fun createChat(name: String) {
        val id = Random.nextLong()
        profileService.addChat(id, name, currentProfile.nodeId)
        profileService.addChatMember(id, currentProfile.nodeId)
    }

    suspend fun inviteToChat(chat: ChatEntity, contact: ContactEntity) {
        profileService.addChatMember(chat.id, contact.nodeId)

        grapevineNetwork.lookupNode(contact.nodeId)?.let {
            grapevineNetwork.sendChatInvitation(it, chat.id, chat.name)
        }
    }

    private val _ingoingMessages = Channel<MessageEntity>()
    val ingoingMessages: ReceiveChannel<MessageEntity> = _ingoingMessages

    fun getLastMessage(contact: ContactEntity) = profileService.getLastMessage(contact)

    fun getChatMessages(chatId: Long) = profileService.getChatMessages(chatId)

    suspend fun sendMessage(
        contact: ContactEntity,
        text: String,
        forwardedMessage: MessageEntity? = null,
        attachment: MessageFile? = null,
    ): MessageEntity {
        val id = Random.nextLong()
        val sentMessage = profileService.addSentMessage(
            id,
            contact.nodeId,
            text,
            forwardedMessage,
            attachment,
        )

        val node = grapevineNetwork.lookupNode(contact.nodeId)
        if (node != null) {
            try {
                grapevineNetwork.sendTextMessage(
                    id,
                    text,
                    node,
                    forwardedMessage?.id,
                    attachment,
                )
                profileService.setMessageState(id, MessageEntity.State.DELIVERED)
            } catch (e: GVException) {
                profileService.setMessageState(id, MessageEntity.State.DELIVERY_FAILED)
            }
        } else {
            profileService.setMessageState(id, MessageEntity.State.DELIVERY_FAILED)
        }

        return sentMessage
    }

    suspend fun sendMessage(
        chat: ChatEntity,
        text: String,
        forwardedMessage: MessageEntity? = null,
        attachment: MessageFile? = null,
    ): MessageEntity {
        val id = Random.nextLong()
        val sentMessage = profileService.addSentMessage(
            id,
            chat.id,
            text,
            forwardedMessage,
            attachment,
        )

        var successful = false

        for (memberId in profileService.getChatMembers(chat.id)) {
            val node = grapevineNetwork.lookupNode(memberId)
            if (node != null) {
                try {
                    grapevineNetwork.sendTextMessage(
                        id,
                        text,
                        node,
                        forwardedMessage?.id,
                        attachment,
                        chat.id,
                    )
                    successful = true
                    profileService.setMessageState(id, MessageEntity.State.DELIVERED)
                } catch (e: GVException) {
                    if (!successful) {
                        profileService.setMessageState(id, MessageEntity.State.DELIVERY_FAILED)
                    }
                }
            }
        }

        return sentMessage
    }

    suspend fun markAsRead(msgId: Long, senderId: Long) {
        val dest = grapevineNetwork.lookupNode(senderId) ?: return

        profileService.setMessageState(msgId, MessageEntity.State.READ)

        try {
            grapevineNetwork.sendReadConfirmation(msgId, dest)
        } catch (e: GVException) {
        }
    }

    private val _ingoingContactInvitations = Channel<ContactEntity>()
    val ingoingContactInvitations: ReceiveChannel<ContactEntity> = _ingoingContactInvitations

    private suspend fun receiveContactInvitations() {
        grapevineNetwork.contactInvitations.collect { node ->
            val photo = try {
                fetchNodePhoto(node)
            } catch (e: GVException) {
                e.printStackTrace()
                null
            }

            val contact = profileService.addContact(node, photo, ContactEntity.State.INGOING)
            _ingoingContactInvitations.send(contact)
        }
    }

    private val _ingoingChatInvitations = Channel<ChatEntity>()
    val ingoingChatInvitations: ReceiveChannel<ChatEntity> = _ingoingChatInvitations

    private suspend fun receiveChatInvitations() {
        grapevineNetwork.chatInvitations.collect {
            val chat = profileService.addChat(it.chatId, it.name, it.node.id)
            _ingoingChatInvitations.send(chat)
        }
    }

    private suspend fun receiveInvitationAnswers() {
        grapevineNetwork.contactInvitationAnswers.collect { (node, accepted) ->
            profileService.getContact(node.id)?.let {
                if (accepted) {
                    profileService.setContactState(it, ContactEntity.State.ACCEPTED)
                } else {
                    profileService.deleteContact(it) // TODO: notify user
                }
            }
        }
    }

    private suspend fun receiveTextMessages() {
        grapevineNetwork.textMessages.collect { (msg, node) ->
            profileService.getContact(node.id)?.let {
                val entity = profileService.addReceivedMessage(node.id, msg)
                _ingoingMessages.send(entity)
            }
        }
    }

    private suspend fun receiveReadConfirmations() {
        grapevineNetwork.readConfirmations.collect { msgId ->
            profileService.setMessageState(msgId, MessageEntity.State.READ)
        }
    }

    private suspend fun redeliverFailedMessages() {
        while (true) {
            delay(5000)

            for (contact in profileService.contactList.first()) {
                for (msg in profileService.getContactFailedMessages(contact).take(5)) {
                    grapevineNetwork.lookupNode(contact.nodeId)?.let {
                        try {
                            grapevineNetwork.sendTextMessage(
                                msg.id,
                                msg.text,
                                it,
                                msg.originalMessageId,
                                msg.file,
                            )
                            profileService.setMessageState(msg.id, MessageEntity.State.DELIVERED)
                        } catch (e: GVException) {
                        }
                    }
                }
            }
        }
    }
}