package ru.vizbash.grapevine.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.protobuf.ByteString
import com.google.protobuf.InvalidProtocolBufferException
import dagger.hilt.android.scopes.ServiceScoped
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import ru.vizbash.grapevine.network.messages.routed.*
import ru.vizbash.grapevine.service.NodeVerifier
import ru.vizbash.grapevine.service.ProfileProvider
import ru.vizbash.grapevine.storage.messages.MessageFile
import ru.vizbash.grapevine.util.*
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.SecretKey
import javax.inject.Inject
import kotlin.math.ceil

@ServiceScoped
class GrapevineNetwork @Inject constructor(
    private val router: Router,
    private val profileProvider: ProfileProvider,
    private val nodeVerifier: NodeVerifier,
) {
    class GVBadSignatureException : GVException()
    class GVCannotDecryptException : GVException()
    class GVInvalidResponseException : GVException()
    class GVTimeoutException : GVException()

    private class AcceptedMessage(val id: Long, val payload: RoutedPayload, val sender: Node)

    companion object {
        private const val ASK_INTERVAL_MS = 30_000L
        private const val RECEIVE_TIMEOUT_MS = 2_000L
        private const val FILE_CHUNK_SIZE = 1024
    }

    private var coroutineScope = CoroutineScope(Dispatchers.IO)
    private var started = false

    private val secretKeyCache = ConcurrentHashMap<Node, SecretKey>()

    val availableNodes: StateFlow<List<Node>> = callbackFlow {
        router.setOnNodesUpdated {
            trySend(router.nodes)
        }
        awaitClose {
            router.setOnNodesUpdated { }
        }
    }.stateIn(coroutineScope, SharingStarted.Eagerly, router.nodes)

    private val acceptedMessages: Flow<AcceptedMessage> = callbackFlow {
        router.setOnMessageReceived { msg ->
            trySend(msg)
        }
        awaitClose {
            router.setOnNodesUpdated { }
        }
    }.acceptMessages().shareIn(coroutineScope, SharingStarted.Eagerly)

    fun lookupNode(id: Long) = availableNodes.value.find { it.id == id }

    fun start() {
        check(!started)

        Log.i(TAG, "Started")

        coroutineScope.launch(Dispatchers.Default) {
            availableNodes.collect {
                secretKeyCache.clear()
            }
        }

        coroutineScope.launch(Dispatchers.IO) {
            while (true) {
                delay(ASK_INTERVAL_MS)
                router.askForNodes()
            }
        }
        coroutineScope.launch(Dispatchers.Default) {
            acceptedMessages
                .filter { it.payload.hasPhotoReq() }
                .collect(this@GrapevineNetwork::handlePhotoRequest)
        }

        started = true
    }

    fun stop() {
        started = false
        coroutineScope.cancel()
        Log.i(TAG, "Stopped")
    }

    private fun getSecret(node: Node) = secretKeyCache.getOrPut(node) {
        generateSharedSecret(profileProvider.profile.dhPrivateKey, node.dhPublicKey)
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private fun Flow<Router.ReceivedMessage>.acceptMessages(): Flow<AcceptedMessage> = transform { msg ->
        if (!nodeVerifier.checkNode(msg.sender)) {
            Log.w(this@GrapevineNetwork.TAG, "${msg.sender} is not who his claims to be")

            val routed = RoutedResponse.newBuilder()
                .setRequestId(msg.id)
                .setError(Error.INVALID_IDENTITY)
                .build()
            val payload = RoutedPayload.newBuilder().setResponse(routed).build()

            send(payload, msg.sender)
            return@transform
        }

        if (!verifyMessage(msg.payload, msg.sign, msg.sender.publicKey)) {
            Log.w(this@GrapevineNetwork.TAG, "${msg.sender} sent message with bad signature")

            val routed = RoutedResponse.newBuilder()
                .setRequestId(msg.id)
                .setError(Error.BAD_SIGNATURE)
                .build()
            val payload = RoutedPayload.newBuilder().setResponse(routed).build()

            send(payload, msg.sender)
            return@transform
        }

        val payloadBytes = aesDecrypt(msg.payload, getSecret(msg.sender))
        if (payloadBytes == null) {
            Log.w(this@GrapevineNetwork.TAG, "Cannot decrypt message from ${msg.sender}")

            val routed = RoutedResponse.newBuilder()
                .setRequestId(msg.id)
                .setError(Error.CANNOT_DECRYPT)
                .build()
            val payload = RoutedPayload.newBuilder().setResponse(routed).build()

            send(payload, msg.sender)
            return@transform
        }

        try {
            val payload = RoutedPayload.parseFrom(payloadBytes)
            Log.d(this@GrapevineNetwork.TAG, "Accepted message ${msg.id} from ${msg.sender}")
            emit(AcceptedMessage(msg.id, payload, msg.sender))
        } catch (e: InvalidProtocolBufferException) {
            Log.w(this@GrapevineNetwork.TAG, "Cannot decode message from ${msg.sender}")
        }
    }

    private suspend fun send(
        payload: RoutedPayload,
        dest: Node,
    ) = withContext(Dispatchers.Default) {
        val payloadEnc = aesEncrypt(payload.toByteArray(), getSecret(dest))
        val sign = signMessage(payloadEnc, profileProvider.profile.privateKey)

        withContext(Dispatchers.IO) {
            router.sendMessage(payloadEnc, sign, dest)
        }
    }

    private suspend fun awaitResponse(
        reqId: Long,
    ): RoutedResponse = withContext(Dispatchers.Default) {
        try {
            withTimeout(RECEIVE_TIMEOUT_MS) {
                acceptedMessages
                    .filter { it.payload.hasResponse() && it.payload.response.requestId == reqId }
                    .map { msg ->
                        val resp = msg.payload.response
                        when (resp.error) {
                            Error.NO_ERROR -> resp
                            Error.BAD_SIGNATURE -> throw GVBadSignatureException()
                            Error.CANNOT_DECRYPT -> throw GVCannotDecryptException()
                            else -> throw GVException()
                        }
                    }
                    .first()
            }
        } catch (e: TimeoutCancellationException) {
            throw GVTimeoutException()
        }
    }

    private suspend fun sendEmptyResponse(req: AcceptedMessage) {
        val resp = RoutedResponse.newBuilder()
            .setRequestId(req.id)
            .setError(Error.NO_ERROR)
            .build()
        val payload = RoutedPayload.newBuilder()
            .setResponse(resp)
            .build()
        send(payload, req.sender)
    }

    private suspend fun handlePhotoRequest(req: AcceptedMessage) {
        val photo = profileProvider.profile.entity.photo

        val photoResp = PhotoResponse.newBuilder()
        if (photo != null) {
            photoResp.hasPhoto = true

            val photoOut = ByteArrayOutputStream()
            photo.compress(Bitmap.CompressFormat.PNG, 100, photoOut)
            photoResp.photo = ByteString.copyFrom(photoOut.toByteArray())
        } else {
            photoResp.hasPhoto = false
        }

        val resp = RoutedResponse.newBuilder()
            .setRequestId(req.id)
            .setError(Error.NO_ERROR)
            .setPhotoResp(photoResp)
            .build()

        val payload = RoutedPayload.newBuilder()
            .setResponse(resp)
            .build()
        send(payload, req.sender)
    }

    suspend fun fetchNodePhoto(node: Node): Bitmap?  {
        val photoReq = PhotoRequest.newBuilder().build()
        val payload = RoutedPayload.newBuilder().setPhotoReq(photoReq).build()

        val reqId = send(payload, node)
        val resp = awaitResponse(reqId)
        if (!resp.hasPhotoResp()) {
            throw GVInvalidResponseException()
        }

        return if (resp.photoResp.hasPhoto) {
            val photo = resp.photoResp.photo.toByteArray()
            BitmapFactory.decodeByteArray(photo, 0, photo.size)
        } else {
            null
        }
    }

    suspend fun sendContactInvitation(node: Node) {
        val req = ContactInvitationMessage.newBuilder().build()
        val payload = RoutedPayload.newBuilder().setContactInvitation(req).build()

        val reqId = send(payload, node)
        awaitResponse(reqId)
    }

    val contactInvitations: Flow<Node> = acceptedMessages
        .filter { it.payload.hasContactInvitation() }
        .map(::handleContactInvitation)
        .shareIn(coroutineScope, SharingStarted.Eagerly)

    private suspend fun handleContactInvitation(req: AcceptedMessage): Node {
        sendEmptyResponse(req)
        return req.sender
    }

    data class ContactInvitationAnswer(val node: Node, val accepted: Boolean)

    val contactInvitationAnswers: Flow<ContactInvitationAnswer> = acceptedMessages
        .filter { it.payload.hasContactInvitationAnswer() }
        .map(::handleContactInvitationAnswer)
        .shareIn(coroutineScope, SharingStarted.Eagerly)

    private suspend fun handleContactInvitationAnswer(req: AcceptedMessage): ContactInvitationAnswer {
        sendEmptyResponse(req)
        return ContactInvitationAnswer(req.sender, req.payload.contactInvitationAnswer.accepted)
    }

    suspend fun sendContactInvitationAnswer(node: Node, accepted: Boolean) {
        val req = ContactInvitationAnswerMessage.newBuilder()
            .setAccepted(accepted)
            .build()
        val payload = RoutedPayload.newBuilder().setContactInvitationAnswer(req).build()

        val reqId = send(payload, node)
        awaitResponse(reqId)
    }

    data class ChatInvitation(
        val node: Node,
        val chatId: Long,
        val name: String,
    )

    val chatInvitations: Flow<ChatInvitation> = acceptedMessages
        .filter { it.payload.hasChatInvitation() }
        .map(::handleChatInvitation)
        .shareIn(coroutineScope, SharingStarted.Eagerly)

    private suspend fun handleChatInvitation(req: AcceptedMessage): ChatInvitation {
        val invitation = req.payload.chatInvitation

        sendEmptyResponse(req)
        return ChatInvitation(req.sender, invitation.chatId, invitation.name)
    }

    suspend fun sendChatInvitation(node: Node, chatId: Long, name: String) {
        val req = ChatInvitationMessage.newBuilder()
            .setChatId(chatId)
            .setName(name)
            .build()
        val payload = RoutedPayload.newBuilder().setChatInvitation(req).build()

        val reqId = send(payload, node)
        awaitResponse(reqId)
    }

    val textMessages: Flow<Pair<TextMessage, Node>> = acceptedMessages
        .filter { it.payload.hasText() }
        .map(::handleTextMessage)
        .shareIn(coroutineScope, SharingStarted.Eagerly, replay = 5)

    private suspend fun handleTextMessage(req: AcceptedMessage): Pair<TextMessage, Node> {
        sendEmptyResponse(req)
        return Pair(req.payload.text, req.sender)
    }

    suspend fun sendTextMessage(
        msgId: Long,
        text: String,
        dest: Node,
        origId: Long?,
        file: MessageFile?,
        chatId: Long? = null
    ) {
        val req = TextMessage.newBuilder()
            .setMsgId(msgId)
            .setText(text)
            .setTimestamp(System.currentTimeMillis() / 1000)
            .setOriginalMsgId(origId ?: 0)
            .setHasFile(file != null)
            .setChatId(chatId ?: 0)

        if (file != null) {
            req.fileName = file.name
            req.fileSize = file.size
        }

        val payload = RoutedPayload.newBuilder().setText(req.build()).build()

        val reqId = send(payload, dest)
        awaitResponse(reqId)
    }

    val readConfirmations: Flow<Long> = acceptedMessages
        .filter { it.payload.hasReadConfirmation() }
        .map(::handleReadConfirmation)
        .shareIn(coroutineScope, SharingStarted.Eagerly)

    private suspend fun handleReadConfirmation(req: AcceptedMessage): Long {
        sendEmptyResponse(req)
        return req.payload.readConfirmation.msgId
    }

    suspend fun sendReadConfirmation(msgId: Long, dest: Node) {
        val req = ReadConfirmationMessage.newBuilder().setMsgId(msgId).build()
        val payload = RoutedPayload.newBuilder().setReadConfirmation(req).build()

        val reqId = send(payload, dest)
        awaitResponse(reqId)
    }

    suspend fun startFileSharing(
        msgId: Long,
        file: MessageFile,
        getNextChunk: suspend (Int) -> ByteArray,
    ) {
        val downloadRequests = acceptedMessages
            .filter { it.payload.hasDownloadReq() }
            .filter { it.payload.downloadReq.msgId == msgId }

        downloadRequests.collect { req ->
            val totalChunks = ceil(file.size.toFloat() / FILE_CHUNK_SIZE).toInt()
            for (chunkNum in 0 until totalChunks) {
                val chunkResp = FileChunkResponse.newBuilder()
                    .setMsgId(msgId)
                    .setTotalChunks(totalChunks)
                    .setChunkNum(chunkNum)
                    .setChunk(ByteString.copyFrom(getNextChunk(FILE_CHUNK_SIZE)))
                    .build()

                val resp = RoutedResponse.newBuilder()
                    .setRequestId(req.id)
                    .setError(Error.NO_ERROR)
                    .setFileChunkResp(chunkResp)
                    .build()

                val payload = RoutedPayload.newBuilder().setResponse(resp).build()
                send(payload, req.sender)
            }
        }
    }

    suspend fun downloadFile(
        msgId: Long,
        sender: Node,
    ): Pair<Flow<ByteArray>, Int> {
        val req = FileDownloadRequest.newBuilder().setMsgId(msgId).build()
        val payload = RoutedPayload.newBuilder().setDownloadReq(req).build()

        val reqId = send(payload, sender)
        awaitResponse(reqId)

        val firstResp = awaitResponse(reqId)
        val firstChunkResp = if (firstResp.hasFileChunkResp()) {
            firstResp.fileChunkResp
        } else {
            throw GVInvalidResponseException()
        }

        val chunkFlow = flow {
            emit(firstChunkResp.chunk.toByteArray())

            var prevNum = 0

            repeat(firstChunkResp.totalChunks - 1) {
                val resp = awaitResponse(reqId)
                val chunkResp = if (resp.hasFileChunkResp()) {
                    resp.fileChunkResp
                } else {
                    throw GVInvalidResponseException()
                }

                if (chunkResp.chunkNum != prevNum + 1)  {
                    throw GVInvalidResponseException()
                }

                emit(chunkResp.chunk.toByteArray())

                prevNum = chunkResp.chunkNum
            }
        }

        return Pair(chunkFlow, firstChunkResp.totalChunks)
    }
}

