package ru.vizbash.grapevine.network.dispatch

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import ru.vizbash.grapevine.GvInvalidResponseException
import ru.vizbash.grapevine.network.DispatcherCoroutineScope
import ru.vizbash.grapevine.network.Node
import ru.vizbash.grapevine.network.message.*
import ru.vizbash.grapevine.storage.message.Message
import ru.vizbash.grapevine.storage.message.MessageFile
import ru.vizbash.grapevine.util.validateMessageText
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TextMessageDispatcher @Inject constructor(
    private val network: GrapevineNetwork,
    @DispatcherCoroutineScope private val coroutineScope: CoroutineScope,
) {
    companion object {
        private const val TAG = "TextMessageDispatcher"
    }

    val textMessages: Flow<Message> = network.acceptedMessages
        .filter { it.payload.hasText() }
        .map(::handleTextMessage)
        .shareIn(coroutineScope, SharingStarted.Eagerly, replay = 5)

    val readConfirmations: Flow<Pair<Long, Node>> = network.acceptedMessages
        .filter { it.payload.hasReadConfirmation() }
        .map(::handleReadConfirmation)
        .shareIn(coroutineScope, SharingStarted.Eagerly, replay = 5)

    private suspend fun handleTextMessage(req: AcceptedMessage): Message {
        Log.d(TAG, "Received text message from ${req.sender}")

        if (!validateMessageText(req.payload.text.text)
            || req.payload.text.fileName.length > 1000) {

            network.sendErrorResponse(RoutedMessages.Error.BAD_REQUEST, req.id, req.sender)
            throw GvInvalidResponseException()
        }

        network.sendEmptyResponse(req.id, req.sender)
        return req.payload.text.run {
            Message(
                id = msgId,
                chatId = chatId,
                timestamp = Date(timestamp * 1000),
                text = text,
                senderId = req.sender.id,
                state = Message.State.DELIVERED,
                origMsgId = if (originalMsgId == -1L) null else originalMsgId,
                file = if (hasFile) MessageFile(
                    uri = null,
                    name = fileName,
                    size = fileSize,
                    isDownloaded = false,
                ) else {
                    null
                },
            )
        }
    }

    private suspend fun handleReadConfirmation(req: AcceptedMessage): Pair<Long, Node> {
        network.sendEmptyResponse(req.id, req.sender)
        return Pair(req.payload.readConfirmation.msgId, req.sender)
    }

    suspend fun sendTextMessage(msg: Message, dest: Node) {
        Log.d(TAG, "Sending text message to $dest")

        val req = routedPayload {
            text = textMessage {
                chatId = msg.chatId
                msgId = msg.id
                text = msg.text
                originalMsgId = msg.origMsgId ?: -1
                timestamp = msg.timestamp.time / 1000

                hasFile = msg.file != null
                if (msg.file != null) {
                    fileName = msg.file.name
                    fileSize = msg.file.size
                }
            }
        }
        val reqId = network.send(req, dest)
        network.receiveResponse(reqId)
    }

    suspend fun sendReadConfirmation(msgId: Long, dest: Node) {
        val req = routedPayload {
            readConfirmation = readConfirmation {
                this.msgId = msgId
            }
        }
        val reqId = network.send(req, dest)
        network.receiveResponse(reqId)
    }
}