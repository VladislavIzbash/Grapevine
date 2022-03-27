package ru.vizbash.grapevine.network.dispatch

import android.util.Log
import com.google.protobuf.InvalidProtocolBufferException
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import ru.vizbash.grapevine.GvRejectedException
import ru.vizbash.grapevine.GvTimeoutException
import ru.vizbash.grapevine.network.DispatcherCoroutineScope
import ru.vizbash.grapevine.network.Node
import ru.vizbash.grapevine.network.NodeProvider
import ru.vizbash.grapevine.network.Router
import ru.vizbash.grapevine.network.message.RoutedMessages
import ru.vizbash.grapevine.network.message.routedPayload
import ru.vizbash.grapevine.network.message.routedResponse
import ru.vizbash.grapevine.service.NodeVerifier
import ru.vizbash.grapevine.service.profile.ProfileProvider
import ru.vizbash.grapevine.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkController @Inject constructor(
    private val router: Router,
    private val profileProvider: ProfileProvider,
    private val nodeVerifier: NodeVerifier,
    @DispatcherCoroutineScope private val coroutineScope: CoroutineScope,
) : NodeProvider {
    companion object {
        private const val TAG = "MessageDispatcher"

        private const val ASK_INTERVAL_MS = 30_000L
        private const val RECEIVE_TIMEOUT_MS = 2_000L
    }

    private val secretKeyCache = ConcurrentHashMap<Node, SecretKey>()

    private var started = false

    @OptIn(ExperimentalCoroutinesApi::class)
    override val availableNodes: StateFlow<List<Node>> = callbackFlow {
        router.setOnNodesChanged {
            trySend(router.nodes)
        }
        awaitClose {
            router.setOnNodesChanged { }
        }
    }.stateIn(coroutineScope, SharingStarted.Eagerly, router.nodes)

    @OptIn(ExperimentalCoroutinesApi::class)
    val acceptedMessages: SharedFlow<AcceptedMessage> = callbackFlow {
        router.setOnMessageReceived { msg ->
            trySend(msg)
        }
        awaitClose {
            router.setOnMessageReceived { }
        }
    }.acceptMessages().shareIn(coroutineScope, SharingStarted.Eagerly)

    private fun getSecret(node: Node) = secretKeyCache.getOrPut(node) {
        generateSharedSecret(profileProvider.profile.sessionPrivKey, node.sessionPubKey)
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private fun Flow<Router.ReceivedMessage>.acceptMessages(): Flow<AcceptedMessage> = transform { msg ->
        if (!nodeVerifier.checkNode(msg.sender)) {
            Log.w(TAG, "${msg.sender} is not who his claims to be")
            sendErrorResponse(RoutedMessages.Error.INVALID_IDENTITY, msg.id, msg.sender)
            return@transform
        }
        if (!validateName(msg.sender.username)) {
            Log.w(TAG, "${msg.sender} has invalid username: ${msg.sender.username}")
            sendErrorResponse(RoutedMessages.Error.BAD_REQUEST, msg.id, msg.sender)
            return@transform
        }
        if (!verifyMessage(msg.payload, msg.sign, msg.sender.pubKey)) {
            Log.w(TAG, "${msg.sender} sent message with bad signature")
            sendErrorResponse(RoutedMessages.Error.BAD_SIGNATURE, msg.id, msg.sender)
            return@transform
        }

        val payloadBytes = aesDecrypt(msg.payload, getSecret(msg.sender))
        if (payloadBytes == null) {
            Log.w(TAG, "Cannot decrypt message from ${msg.sender}")
            sendErrorResponse(RoutedMessages.Error.CANNOT_DECRYPT, msg.id, msg.sender)
            return@transform
        }

        try {
            val payload = RoutedMessages.RoutedPayload.parseFrom(payloadBytes)
            Log.d(TAG, "Accepted message ${msg.id} from ${msg.sender}")
            emit(AcceptedMessage(msg.id, payload, msg.sender))
        } catch (e: InvalidProtocolBufferException) {
            Log.w(TAG, "Cannot decode message from ${msg.sender}")
            sendErrorResponse(RoutedMessages.Error.BAD_REQUEST, msg.id, msg.sender)
        }
    }

    fun start() {
        if (started) {
            return
        }

        Log.i(TAG, "Starting")

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

        started = true
    }

    suspend fun receiveResponse(
        reqId: Long,
    ): RoutedMessages.RoutedResponse = withContext(Dispatchers.Default) {
        try {
            withTimeout(RECEIVE_TIMEOUT_MS) {
                acceptedMessages
                    .filter { it.payload.hasResponse() && it.payload.response.requestId == reqId }
                    .map { msg ->
                        val resp = msg.payload.response
                        if (resp.error == RoutedMessages.Error.NO_ERROR) {
                            resp
                        } else {
                            throw GvRejectedException()
                        }
                    }
                    .first()
            }
        } catch (e: TimeoutCancellationException) {
            throw GvTimeoutException()
        }
    }

    suspend fun send(
        payload: RoutedMessages.RoutedPayload,
        dest: Node,
    ) = withContext(Dispatchers.Default) {
        val payloadEnc = aesEncrypt(payload.toByteArray(), getSecret(dest))
        val sign = signMessage(payloadEnc, profileProvider.profile.privKey)

        withContext(Dispatchers.IO) {
            router.sendMessage(payloadEnc, sign, dest)
        }
    }

    suspend fun sendEmptyResponse(requestId: Long, dest: Node) {
        sendErrorResponse(RoutedMessages.Error.NO_ERROR, requestId, dest)
    }

    suspend fun sendErrorResponse(error: RoutedMessages.Error, requestId: Long, dest: Node) {
        val payload = routedPayload {
            response = routedResponse {
                this.requestId = requestId
                this.error = error
            }
        }
        send(payload, dest)
    }
}