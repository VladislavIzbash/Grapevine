package ru.vizbash.grapevine.service

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import ru.vizbash.grapevine.GvException
import ru.vizbash.grapevine.network.NodeProvider
import ru.vizbash.grapevine.network.dispatch.FileTransferDispatcher
import ru.vizbash.grapevine.network.dispatch.TextMessageDispatcher
import ru.vizbash.grapevine.service.profile.ProfileProvider
import ru.vizbash.grapevine.storage.chat.ChatDao
import ru.vizbash.grapevine.storage.message.Message
import ru.vizbash.grapevine.storage.message.MessageDao
import ru.vizbash.grapevine.storage.message.MessageFile
import ru.vizbash.grapevine.storage.node.NodeDao
import java.io.*
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.Exception
import kotlin.random.Random

@Singleton
class MessageService @Inject constructor(
    @ServiceCoroutineScope private val coroutineScope: CoroutineScope,
    private val profileProvider: ProfileProvider,
    private val textDispatcher: TextMessageDispatcher,
    private val fileDispatcher: FileTransferDispatcher,
    private val nodeDao: NodeDao,
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val nodeProvider: NodeProvider,
) {
    companion object {
        private const val TAG = "MessageService"

        private const val REDELIVER_INTERVAL_MS = 5000L
    }

    private val _ingoingMessages = Channel<Message>()
    val ingoingMessages: ReceiveChannel<Message> = _ingoingMessages

    private val streamedFiles = mutableMapOf<Long, InputStream>()

    init {
        fileDispatcher.setFileChunkProvider(::getNextFileChunk)

        coroutineScope.launch {
            textDispatcher.textMessages.collect {
                messageDao.insert(it)
                _ingoingMessages.send(it)
            }
        }
        coroutineScope.launch {
            while (true) {
                delay(REDELIVER_INTERVAL_MS)
                redeliverMessages()
            }
        }
    }

    suspend fun sendMessage(
        chatId: Long,
        text: String,
        origId: Long? = null,
        file: MessageFile? = null,
    ) {
        val msg = Message(
            id = Random.nextLong(),
            timestamp = Date(),
            chatId = chatId,
            senderId = profileProvider.profile.nodeId,
            text = text,
            origMsgId = origId,
            file = file,
            state = Message.State.SENT,
        )
        messageDao.insert(msg)
        fileDispatcher
        val knownNode = nodeDao.getById(chatId)
        if (knownNode != null) {
            sendToDialogChat(msg, knownNode.id)
        } else {
            sendToGroupChat(msg, chatId)
        }
    }

    private suspend fun sendToDialogChat(msg: Message, nodeId: Long) {
        try {
            textDispatcher.sendTextMessage(msg, nodeProvider.getOrThrow(nodeId))
            messageDao.changeState(msg.id, Message.State.DELIVERED)
        } catch (e: GvException) {
            messageDao.changeState(msg.id, Message.State.DELIVERY_FAILED)
        }
    }

    private suspend fun sendToGroupChat(msg: Message, chatId: Long) {
        var state: Message.State? = null

        for (memberId in chatDao.getGroupChatMembers(chatId)) {
            try {
                textDispatcher.sendTextMessage(msg, nodeProvider.getOrThrow(memberId))
                state = Message.State.DELIVERED
            } catch (e: GvException) {
                if (state == null) {
                    state = Message.State.DELIVERY_FAILED
                }
            }
        }

        messageDao.changeState(msg.id, state ?: Message.State.DELIVERY_FAILED)
    }

    private suspend fun redeliverMessages() {
        val messages = messageDao.getAllWithState(Message.State.DELIVERY_FAILED, 5)

        if (messages.size > 0) {
            Log.d(TAG, "Redelivering ${messages.size} messages")
        }

        for (msg in messages) {
            sendMessage(msg.chatId, msg.text, msg.origMsgId, msg.file)
        }
    }

    suspend fun markAsRead(msgId: Long, senderId: Long) {
        messageDao.changeState(msgId, Message.State.READ)

        try {
            textDispatcher.sendReadConfirmation(msgId, nodeProvider.getOrThrow(senderId))
        } catch (e: GvException) {
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun downloadFile(
        msg: Message,
        saveLocation: Uri,
    ): Flow<Float> = withContext(Dispatchers.IO) {
        return@withContext flow {
            try {
                val output = File(saveLocation.path!!, msg.file!!.name)
                    .outputStream()
                    .buffered()

                var received = 0

                output.use {
                    val sender = nodeProvider.getOrThrow(msg.senderId)
                    fileDispatcher.downloadFile(msg.id, msg.file, sender).collect { chunk ->
                        while (received < msg.file.size) {
                            output.write(chunk)
                            received += chunk.size
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is GvException) {
                    throw e
                }
            }
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun getNextFileChunk(msgId: Long, size: Int): ByteArray? {
        try {
            val stream = streamedFiles.getOrPut(msgId) {
                val msg = messageDao.getById(msgId) ?: return null
                val fileInfo = msg.file ?: return null
                val uri = fileInfo.uri ?: return null

                File(uri.path!!).inputStream().buffered()
            }

            val buffer = ByteArray(size)
            val read = stream.read(buffer)
            if (read < 0) {
                stream.close()
                streamedFiles.remove(msgId)
                return null
            }

            return buffer
        } catch (e: IOException) {
            e.printStackTrace()
            return null
        }
    }
}