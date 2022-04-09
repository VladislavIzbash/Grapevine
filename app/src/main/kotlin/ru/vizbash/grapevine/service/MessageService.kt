package ru.vizbash.grapevine.service

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.GvException
import ru.vizbash.grapevine.network.Node
import ru.vizbash.grapevine.network.NodeProvider
import ru.vizbash.grapevine.network.dispatch.TextMessageDispatcher
import ru.vizbash.grapevine.network.message.RoutedMessages
import ru.vizbash.grapevine.service.profile.ProfileProvider
import ru.vizbash.grapevine.storage.chat.ChatDao
import ru.vizbash.grapevine.storage.message.Message
import ru.vizbash.grapevine.storage.message.MessageDao
import ru.vizbash.grapevine.storage.message.MessageFile
import ru.vizbash.grapevine.storage.node.NodeDao
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class MessageService @Inject constructor(
    @ServiceCoroutineScope private val coroutineScope: CoroutineScope,
    private val profileProvider: ProfileProvider,
    private val textDispatcher: TextMessageDispatcher,
    private val nodeProvider: NodeProvider,
    private val nodeDao: NodeDao,
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val chatService: ChatService,
) {
    companion object {
        private const val TAG = "MessageService"

        private const val REDELIVER_INTERVAL_MS = 15_000L
    }

    private val _ingoingMessages = Channel<Message>()
    val ingoingMessages: ReceiveChannel<Message> = _ingoingMessages

    fun start() {
        coroutineScope.launch {
            textDispatcher.textMessages.collect { (msg, sender) -> receiveMessage(msg, sender) }
        }
        coroutineScope.launch {
            textDispatcher.readConfirmations.collect { (msgId, _) ->
                messageDao.setState(msgId, Message.State.READ)
            }
        }
        coroutineScope.launch {
            while (true) {
                delay(REDELIVER_INTERVAL_MS)
                redeliverMessages()
            }
        }
    }

    fun getLastMessage(chatId: Long) = messageDao.observeLastMessage(chatId)

    fun getChatMessages(chatId: Long) = messageDao.pageMessagesFromChat(chatId)

    private suspend fun receiveMessage(msg: RoutedMessages.TextMessage, sender: Node) {
        val isGroupChat = msg.chatId != profileProvider.profile.nodeId

        when {
            msg.chatId == 0L -> {}
            isGroupChat && chatService.getChat(msg.chatId) == null -> return
            isGroupChat && !chatService.isMemberOfChat(msg.chatId, sender.id) -> {
                Log.w(TAG, "Non member $sender attempted to send message to chat ${msg.chatId}")
                return
            }
            isGroupChat && !chatService.isMemberOfChat(msg.chatId, profileProvider.profile.nodeId) -> {
                return
            }
            !isGroupChat && chatService.getChat(sender.id) == null -> {
                chatService.createDialogChat(chatService.rememberNode(sender))
            }
        }

        val message = Message(
            id = msg.msgId,
            chatId = if (isGroupChat) msg.chatId else sender.id,
            timestamp = Date(msg.timestamp * 1000),
            text = msg.text,
            senderId = sender.id,
            state = Message.State.DELIVERED,
            origMsgId = if (msg.originalMsgId == -1L) null else msg.originalMsgId,
            file = if (msg.hasFile) MessageFile(
                uri = null,
                name = msg.fileName,
                size = msg.fileSize,
                state = MessageFile.State.NOT_DOWNLOADED,
            ) else {
                null
            },
            fullyDelivered = true,
        )

        messageDao.insert(message)
        chatDao.setUpdateTime(message.chatId, Date())
        _ingoingMessages.trySend(message)
    }

    suspend fun sendMessage(
        chatId: Long,
        text: String,
        origId: Long? = null,
        file: MessageFile? = null,
    ): Message {
        val msg = Message(
            id = Random.nextLong(),
            timestamp = Date(),
            chatId = chatId,
            senderId = profileProvider.profile.nodeId,
            text = text,
            origMsgId = origId,
            file = file,
            state = Message.State.SENT,
            fullyDelivered = false,
        )
        messageDao.insert(msg)
        chatDao.setUpdateTime(chatId, Date())

        if (chatId == 0L) {
            sendBroadcast(msg)
            return msg
        }

        val knownNode = nodeDao.getById(chatId)
        if (knownNode != null) {
            sendToDialogChat(msg, knownNode.id)
        } else {
            sendToGroupChat(msg, chatId)
        }

        return msg
    }

    private suspend fun sendBroadcast(msg: Message) {
        for (node in nodeProvider.availableNodes.value) {
            textDispatcher.sendTextMessage(msg, node)
        }
        messageDao.setState(msg.id, Message.State.DELIVERED)
        messageDao.setFullyDelivered(msg.id)
    }

    private suspend fun sendToDialogChat(msg: Message, nodeId: Long) {
        try {
            textDispatcher.sendTextMessage(msg, nodeProvider.getOrThrow(nodeId))
            messageDao.setState(msg.id, Message.State.DELIVERED)
            messageDao.setFullyDelivered(msg.id)
        } catch (e: GvException) {
            messageDao.setState(msg.id, Message.State.DELIVERY_FAILED)
        }
    }

    private suspend fun sendToGroupChat(msg: Message, chatId: Long) {
        val members = chatDao.getGroupChatMemberIds(chatId)

        var state: Message.State? = if (members.isEmpty()) null else Message.State.DELIVERED
        var fullyDelivered = true

        for (memberId in members) {
            if (memberId == profileProvider.profile.nodeId) {
                continue
            }

            try {
                textDispatcher.sendTextMessage(msg, nodeProvider.getOrThrow(memberId))
                state = Message.State.DELIVERED
            } catch (e: GvException) {
                if (state == null) {
                    state = Message.State.DELIVERY_FAILED
                }
                fullyDelivered = false
            }
        }

        messageDao.setState(msg.id, state ?: Message.State.DELIVERY_FAILED)
        if (fullyDelivered) {
            messageDao.setFullyDelivered(msg.id)
        }
    }

    suspend fun getUnread(chatId: Long)
        = messageDao.getFromChatWithState(chatId, Message.State.DELIVERED)

    private suspend fun redeliverMessages() {
        val messages = messageDao.getUndeliveredLimit(5)

        if (messages.isNotEmpty()) {
            Log.d(TAG, "Redelivering ${messages.size} messages")
        }

        for (msg in messages) {
            val knownNode = nodeDao.getById(msg.chatId)
            if (knownNode != null) {
                sendToDialogChat(msg, knownNode.id)
            } else {
                sendToGroupChat(msg, msg.chatId)
            }
        }
    }

    suspend fun markAsRead(msg: Message) {
        messageDao.setState(msg.id, Message.State.READ)

        try {
            textDispatcher.sendReadConfirmation(msg.id, nodeProvider.getOrThrow(msg.senderId))
        } catch (e: GvException) {
        }
    }
}