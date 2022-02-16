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
import java.security.Security
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkController @Inject constructor(
    private val router: Router,
    private val profileService: ProfileService,
) {
    open class GvException : Exception()
    class GvBadSignatureException : GvException()
    class GvCannotDecryptException : GvException()
    class GvInvalidResponseException : GvException()
    class GvTimeoutException : GvException()

    private class AcceptedMessage(val id: Long, val payload: EncryptedPayload, val sender: Node)

    companion object {
        private const val ASK_INTERVAL_MS = 30_000L
        private const val RECEIVE_TIMEOUT_MS = 5_000L
    }

    private var coroutineScope = CoroutineScope(Dispatchers.IO)
    private var running = false
    private val secretKeyCache = mutableMapOf<Node, SecretKey>()

    val nodes: StateFlow<Set<Node>> = callbackFlow {
        router.setOnNodesUpdated {
            trySend(router.nodes)
        }
        awaitClose {
            router.setOnNodesUpdated { }
        }
    }.stateIn(coroutineScope, SharingStarted.Eagerly, router.nodes)

    private val incomingMessages: Flow<AcceptedMessage> = callbackFlow {
        router.setOnMessageReceived { msg ->
            trySend(msg)
        }
        awaitClose {
            router.setOnNodesUpdated { }
        }
    }.acceptMessages().shareIn(coroutineScope, SharingStarted.Eagerly)

    val textMessages: Flow<TextMessage> = incomingMessages
        .filter { it.payload.hasText() }
        .map(this::handleTextMessage)
        .shareIn(coroutineScope, SharingStarted.Eagerly, replay = 5)

    fun start() {
        if (running) {
            return
        }

        Log.i(TAG, "Started controller")

        coroutineScope.launch {
            while (true) {
                delay(ASK_INTERVAL_MS)
                router.askForNodes()
            }
        }
        coroutineScope.launch {
            incomingMessages
                .filter { it.payload.hasPhotoReq() }
                .collect(this@NetworkController::handlePhotoRequest)
        }

        running = true
    }

    fun stop() {
        if (running) {
            running = false
            coroutineScope.cancel()
            Log.i(TAG, "Stopped controller")
        }
    }

    private fun getSecret(node: Node) = secretKeyCache.getOrPut(node) {
        generateSharedSecret(profileService.currentProfile.dhPrivateKey, node.dhPublicKey)
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private fun Flow<Router.ReceivedMessage>.acceptMessages(): Flow<AcceptedMessage> = transform { msg ->
        if (!verifyMessage(msg.payload, msg.sign, msg.sender.publicKey)) {
            Log.w(this@NetworkController.TAG, "${msg.sender} sent message with bad signature")

            val routed = RoutedResponse.newBuilder()
                .setRequestId(msg.id)
                .setError(Error.BAD_SIGNATURE)
                .build()
            val payload = EncryptedPayload.newBuilder().setResponse(routed).build()

            sendMessage(payload, msg.sender)
            return@transform
        }

        val payloadBytes = aesDecrypt(msg.payload, getSecret(msg.sender))
        if (payloadBytes == null) {
            Log.w(this@NetworkController.TAG, "Cannot decrypt message from ${msg.sender}")

            val routed = RoutedResponse.newBuilder()
                .setRequestId(msg.id)
                .setError(Error.CANNOT_DECRYPT)
                .build()
            val payload = EncryptedPayload.newBuilder().setResponse(routed).build()

            sendMessage(payload, msg.sender)
            return@transform
        }

        try {
            val payload = EncryptedPayload.parseFrom(payloadBytes)
            Log.d(this@NetworkController.TAG, "Accepted message ${msg.id} from ${msg.sender}")
            emit(AcceptedMessage(msg.id, payload, msg.sender))
        } catch (e: InvalidProtocolBufferException) {
            Log.w(this@NetworkController.TAG, "Cannot decode message from ${msg.sender}")
        }
    }

    private suspend fun handlePhotoRequest(req: AcceptedMessage) {
        val photoResp = PhotoResponse.newBuilder().run {
            val photo = profileService.currentProfile.entity.photo

            setHasPhoto(photo != null)
            if (photo != null) {
                val out = ByteArrayOutputStream()
                photo.compress(Bitmap.CompressFormat.PNG, 100, out)
                val bytes = out.toByteArray()
//                println("Sent photo: ${bytes.toHexString()}")
                setPhoto(ByteString.copyFrom(bytes))
            }
            build()
        }
        val resp = RoutedResponse.newBuilder()
            .setRequestId(req.id)
            .setError(Error.NO_ERROR)
            .setPhotoResp(photoResp)
            .build()

        val payload = EncryptedPayload.newBuilder()
            .setResponse(resp)
            .build()
        sendMessage(payload, req.sender)
    }

    private suspend fun handleTextMessage(req: AcceptedMessage): TextMessage {
        val resp = RoutedResponse.newBuilder()
            .setRequestId(req.id)
            .setError(Error.NO_ERROR)
            .build()
        val payload = EncryptedPayload.newBuilder()
            .setResponse(resp)
            .build()
        sendMessage(payload, req.sender)

        Log.d(TAG, "Received text message ${req.id} from ${req.sender}")

        return req.payload.text
    }

    private suspend fun sendMessage(
        payload: EncryptedPayload,
        dest: Node,
    ) = withContext(Dispatchers.Default) {
        val payloadEnc = aesEncrypt(payload.toByteArray(), getSecret(dest))
        val sign = signMessage(payloadEnc, profileService.currentProfile.privateKey)

        withContext(Dispatchers.IO) {
            router.sendMessage(payloadEnc, sign, dest)
        }
    }

    private suspend fun sendAndAwaitResponse(
        payload: EncryptedPayload,
        dest: Node,
    ) = withContext(Dispatchers.Default) {
        val id = sendMessage(payload, dest)

        try {
            withTimeout(RECEIVE_TIMEOUT_MS) {
                incomingMessages
                    .filter { it.payload.hasResponse() && it.payload.response.requestId == id }
                    .map { msg ->
                        val resp = msg.payload.response
                        when (resp.error) {
                            Error.BAD_SIGNATURE -> throw GvBadSignatureException()
                            Error.CANNOT_DECRYPT -> throw GvCannotDecryptException()
                            else -> resp
                        }
                    }
                    .first()
            }
        } catch (e: TimeoutCancellationException) {
            throw GvTimeoutException()
        }
    }

    suspend fun fetchNodePhoto(node: Node): Bitmap? {
        val photoReq = PhotoRequest.newBuilder().build()
        val payload = EncryptedPayload.newBuilder().setPhotoReq(photoReq).build()

        val resp = sendAndAwaitResponse(payload, node)
        if (!resp.hasPhotoResp()) {
            throw GvInvalidResponseException()
        }

        return if (resp.photoResp.hasPhoto) {
            val photo = resp.photoResp.toByteArray()
//            println("Received photo: ${photo.toHexString()}")
            BitmapFactory.decodeByteArray(photo, 6, photo.size - 6) // TODO ??
        } else {
            null
        }
    }

    suspend fun sendText(text: String, node: Node) {
        val textMsg = TextMessage
            .newBuilder()
            .setText(text)
            .setTimestamp(System.currentTimeMillis() / 1000L)
            .build()
        val payload = EncryptedPayload.newBuilder().setText(textMsg).build()

        sendAndAwaitResponse(payload, node)
    }
}

