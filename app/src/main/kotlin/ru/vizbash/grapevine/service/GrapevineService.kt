package ru.vizbash.grapevine.service

import android.graphics.Bitmap
import android.util.Log
import dagger.hilt.android.scopes.ServiceScoped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.network.GrapevineNetwork
import ru.vizbash.grapevine.network.Node
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

        coroutineScope.run {
            launch(Dispatchers.Default) { receiveInvitations() }
            launch(Dispatchers.Default) { receiveInvitationAnswers() }
            launch(Dispatchers.Default) { receiveTextMessages() }
            launch(Dispatchers.Default) { receiveReadConfirmations() }
        }
    }

    fun stop() {
        coroutineScope.cancel()
        grapevineNetwork.stop()
        Log.i(TAG, "Stopped")
    }

    val availableNodes = grapevineNetwork.availableNodes
    val currentProfile get() = profileService.profile.entity
    val contactList get() = profileService.contactList

    private val photoCache = mutableMapOf<Node, Bitmap?>()

    suspend fun fetchNodePhoto(node: Node) = photoCache.getOrPut(node) {
        grapevineNetwork.fetchNodePhoto(node)
    }

    suspend fun getContact(id: Long) = profileService.getContact(id)

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

        grapevineNetwork.availableNodes.value.find { it.id == contact.nodeId }?.let {
            grapevineNetwork.sendContactInvitationAnswer(it, true)
        }
    }

    suspend fun rejectContact(contact: ContactEntity) {
        profileService.deleteContact(contact)

        grapevineNetwork.availableNodes.value.find { it.id == contact.nodeId }?.let {
            grapevineNetwork.sendContactInvitationAnswer(it, false)
        }
    }

    private val _ingoingMessages = Channel<MessageEntity>()
    val ingoingMessages: ReceiveChannel<MessageEntity> = _ingoingMessages

    fun getLastMessage(contact: ContactEntity) = profileService.getLastMessage(contact)

    fun getContactMessages(contact: ContactEntity) = profileService.getContactMessages(contact)

    suspend fun sendMessage(
        contact: ContactEntity,
        text: String,
        forwardedMessage: MessageEntity? = null,
        attachment: MessageFile? = null,
    ): MessageEntity {
        val id = Random.nextLong()
        val sentMessage = profileService.addSentMessage(
            id,
            contact,
            text,
            forwardedMessage,
            attachment,
        )

        grapevineNetwork.availableNodes.value.find { it.id == contact.nodeId }?.let {
            try {
                grapevineNetwork.sendTextMessage(
                    id,
                    text,
                    it,
                    forwardedMessage?.id,
                    attachment,
                )
                profileService.setMessageState(id, MessageEntity.State.DELIVERED)
            } catch (e: GVException) {
                profileService.setMessageState(id, MessageEntity.State.DELIVERY_FAILED)
            }
        }

        return sentMessage
    }

    suspend fun markAsRead(msgId: Long, senderId: Long) {
        val dest = availableNodes.value.find { it.id == senderId } ?: return

        profileService.setMessageState(msgId, MessageEntity.State.READ)

        try {
            grapevineNetwork.sendReadConfirmation(msgId, dest)
        } catch (e: GVException) {
        }
    }

    private suspend fun receiveInvitations() {
        grapevineNetwork.contactInvitations.collect { node ->
            val photo = try {
                grapevineNetwork.fetchNodePhoto(node)
            } catch (e: GVException) {
                e.printStackTrace()
                null
            }

            profileService.addContact(node, photo, ContactEntity.State.INGOING)
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
                val entity = profileService.addReceivedMessage(it, msg)
                _ingoingMessages.send(entity)
            }
        }
    }

    private suspend fun receiveReadConfirmations() {
        grapevineNetwork.readConfirmations.collect { msgId ->
            profileService.setMessageState(msgId, MessageEntity.State.READ)
        }
    }
}