package ru.vizbash.grapevine.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.protobuf.ByteString
import com.google.protobuf.InvalidProtocolBufferException
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import ru.vizbash.grapevine.*
import ru.vizbash.grapevine.network.messages.routed.*
import java.io.ByteArrayOutputStream
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GrapevineNetwork @Inject constructor(
    private val router: Router,
    private val profileProvider: ProfileProvider,
) {
    class GVBadSignatureException : GVException()
    class GVCannotDecryptException : GVException()
    class GVInvalidResponseException : GVException()
    class GVTimeoutException : GVException()

    private class AcceptedMessage(val id: Long, val payload: EncryptedPayload, val sender: Node)

    companion object {
        private const val ASK_INTERVAL_MS = 30_000L
        private const val RECEIVE_TIMEOUT_MS = 5_000L
    }

    private var coroutineScope = CoroutineScope(Dispatchers.IO)
    private var running = false

    private val secretKeyCache = mutableMapOf<Node, SecretKey>()
    private val photoCache = mutableMapOf<Node, Bitmap?>()

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

    fun start() {
        if (running) {
            return
        }

        Log.i(TAG, "Started message handler")

        coroutineScope.launch {
            while (true) {
                delay(ASK_INTERVAL_MS)
                router.askForNodes()
            }
        }
        coroutineScope.launch {
            acceptedMessages
                .filter { it.payload.hasPhotoReq() }
                .collect(this@GrapevineNetwork::handlePhotoRequest)
        }

        running = true
    }

    fun stop() {
        if (running) {
            running = false
            coroutineScope.coroutineContext.cancelChildren()
            Log.i(TAG, "Stopped message handler")
        }
    }

    private fun getSecret(node: Node) = secretKeyCache.getOrPut(node) {
        generateSharedSecret(profileProvider.profile.dhPrivateKey, node.dhPublicKey)
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private fun Flow<Router.ReceivedMessage>.acceptMessages(): Flow<AcceptedMessage> = transform { msg ->
        if (!verifyMessage(msg.payload, msg.sign, msg.sender.publicKey)) {
            Log.w(this@GrapevineNetwork.TAG, "${msg.sender} sent message with bad signature")

            val routed = RoutedResponse.newBuilder()
                .setRequestId(msg.id)
                .setError(Error.BAD_SIGNATURE)
                .build()
            val payload = EncryptedPayload.newBuilder().setResponse(routed).build()

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
            val payload = EncryptedPayload.newBuilder().setResponse(routed).build()

            send(payload, msg.sender)
            return@transform
        }

        try {
            val payload = EncryptedPayload.parseFrom(payloadBytes)
            Log.d(this@GrapevineNetwork.TAG, "Accepted message ${msg.id} from ${msg.sender}")
            emit(AcceptedMessage(msg.id, payload, msg.sender))
        } catch (e: InvalidProtocolBufferException) {
            Log.w(this@GrapevineNetwork.TAG, "Cannot decode message from ${msg.sender}")
        }
    }

    private suspend fun send(
        payload: EncryptedPayload,
        dest: Node,
    ) = withContext(Dispatchers.Default) {
        val payloadEnc = aesEncrypt(payload.toByteArray(), getSecret(dest))
        val sign = signMessage(payloadEnc, profileProvider.profile.privateKey)

        withContext(Dispatchers.IO) {
            router.sendMessage(payloadEnc, sign, dest)
        }
    }

    private suspend fun sendAndAwaitResponse(
        payload: EncryptedPayload,
        dest: Node,
    ): RoutedResponse = withContext(Dispatchers.Default) {
        val id = send(payload, dest)

        try {
            withTimeout(RECEIVE_TIMEOUT_MS) {
                acceptedMessages
                    .filter { it.payload.hasResponse() && it.payload.response.requestId == id }
                    .map { msg ->
                        val resp = msg.payload.response
                        when (resp.error) {
                            Error.BAD_SIGNATURE -> throw GVBadSignatureException()
                            Error.CANNOT_DECRYPT -> throw GVCannotDecryptException()
                            else -> resp
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
        val payload = EncryptedPayload.newBuilder()
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

        val payload = EncryptedPayload.newBuilder()
            .setResponse(resp)
            .build()
        send(payload, req.sender)
    }

    suspend fun fetchNodePhoto(node: Node) = photoCache.getOrPut(node) {
        val photoReq = PhotoRequest.newBuilder().build()
        val payload = EncryptedPayload.newBuilder().setPhotoReq(photoReq).build()

        val resp = sendAndAwaitResponse(payload, node)
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
        val payload = EncryptedPayload.newBuilder().setContactInvitation(req).build()
        sendAndAwaitResponse(payload, node)
    }

    private suspend fun handleContactInvitation(req: AcceptedMessage): Node {
        sendEmptyResponse(req)
        return req.sender
    }

    val contactInvitations: Flow<Node> = acceptedMessages
        .filter { it.payload.hasContactInvitation() }
        .map(this::handleContactInvitation)
        .shareIn(coroutineScope, SharingStarted.Eagerly)

    suspend fun sendContactInvitationAnswer(node: Node, accepted: Boolean) {
        val req = ContactInvitationAnswerMessage.newBuilder()
            .setAccepted(accepted)
            .build()
        val payload = EncryptedPayload.newBuilder().setContactInvitationAnswer(req).build()

        sendAndAwaitResponse(payload, node)
    }

    data class ContactInvitationAnswer(val node: Node, val accepted: Boolean)

    private suspend fun handleContactInvitationAnswer(req: AcceptedMessage): ContactInvitationAnswer {
        sendEmptyResponse(req)

        if (!req.payload.hasContactInvitationAnswer()) {
            throw GVInvalidResponseException()
        }

        return ContactInvitationAnswer(req.sender, req.payload.contactInvitationAnswer.accepted)
    }

    val contactInvitationAnswers: Flow<ContactInvitationAnswer> = acceptedMessages
        .filter { it.payload.hasContactInvitationAnswer() }
        .map(this::handleContactInvitationAnswer)
        .shareIn(coroutineScope, SharingStarted.Eagerly)


    //    suspend fun sendText(text: String, node: Node) {
//        val textMsg = TextMessage
//            .newBuilder()
//            .setText(text)
//            .setTimestamp(System.currentTimeMillis() / 1000L)
//            .build()
//        val payload = EncryptedPayload.newBuilder().setText(textMsg).build()
//
//        sendAndAwaitResponse(payload, node)
//    }

//    val textMessages: Flow<TextMessage> = incomingMessages
//        .filter { it.payload.hasText() }
//        .map(this::handleTextMessage)
//        .shareIn(coroutineScope, SharingStarted.Eagerly, replay = 5)

//    private suspend fun handleTextMessage(req: MessageHandler.AcceptedMessage): TextMessage {
//        val resp = RoutedResponse.newBuilder()
//            .setRequestId(req.id)
//            .setError(Error.NO_ERROR)
//            .build()
//        val payload = EncryptedPayload.newBuilder()
//            .setResponse(resp)
//            .build()
//        sendMessage(payload, req.sender)
//
//        Log.d(TAG, "Received text message ${req.id} from ${req.sender}")
//
//        return req.payload.text
//    }
}

