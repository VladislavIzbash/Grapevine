package ru.vizbash.grapevine.network.dispatch

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.protobuf.ByteString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.GvInvalidResponseException
import ru.vizbash.grapevine.network.DispatcherCoroutineScope
import ru.vizbash.grapevine.network.Node
import ru.vizbash.grapevine.network.message.*
import ru.vizbash.grapevine.util.decodeBitmap
import ru.vizbash.grapevine.util.encodeBitmap
import ru.vizbash.grapevine.util.validateName
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupChatDispatcher @Inject constructor(
    private val network: GrapevineNetwork,
    @DispatcherCoroutineScope private val coroutineScope: CoroutineScope,
) {
    companion object {
        private const val TAG = "GroupChatDispatcher"
    }

    data class ChatInfo(
        val name: String,
        val ownerId: Long,
        val photo: Bitmap?,
        val members: List<Long>,
    )

    private var getChatInfo: (suspend (Long, Long) -> ChatInfo?)? = null

    val chatInvitations = network.acceptedMessages
        .filter { it.payload.hasChatInvitation() }
        .map(::handleChatInvitation)
        .shareIn(coroutineScope, SharingStarted.Eagerly, replay = 5)

    init {
        coroutineScope.launch {
            network.acceptedMessages
                .filter { it.payload.hasChatInfoReq() }
                .collect(::handleChatInfoRequest)
        }
    }

    fun setChatInfoProvider(getChatInfo: suspend (Long, Long) -> ChatInfo?) {
        this.getChatInfo = getChatInfo
    }

    private suspend fun handleChatInvitation(req: AcceptedMessage): Pair<Node, Long> {
        val chatId = req.payload.chatInvitation.chatId

        Log.d(TAG, "Received invitation to chat $chatId form ${req.sender}")

        network.sendEmptyResponse(req.id, req.sender)
        return Pair(req.sender, chatId)
    }

    private suspend fun handleChatInfoRequest(req: AcceptedMessage) {
        val chatId = req.payload.chatInfoReq.chatId

        Log.d(TAG, "Received chat $chatId info request from ${req.sender}")

        val chatInfo = getChatInfo?.invoke(chatId, req.sender.id)
        if (chatInfo == null) {
            network.sendErrorResponse(GrapevineRouted.Error.NOT_FOUND, req.id, req.sender)
            return
        }

        val resp = routedPayload {
            response = routedResponse {
                chatInfoResp = chatInfoResponse {
                    name = chatInfo.name
                    ownerId = chatInfo.ownerId
                    hasPhoto = chatInfo.photo != null
                    if (chatInfo.photo != null) {
                        photo = ByteString.copyFrom(encodeBitmap(chatInfo.photo))
                    }
                }
            }
        }
        network.send(resp, req.sender)
    }

    suspend fun sendChatInvitation(chatId: Long, node: Node) {
        Log.d(TAG, "Sending invitation to chat $chatId to $node")

        val req = routedPayload {
            chatInvitation = chatInvitation {
                this.chatId = chatId
            }
        }
        val reqId = network.send(req, node)
        network.receiveResponse(reqId)
    }

    suspend fun fetchChatInfo(chatId: Long, node: Node): ChatInfo {
        Log.d(TAG, "Fetching chat $chatId info from $node")

        val req = routedPayload {
            chatInfoReq = chatInfoRequest {
                this.chatId = chatId
            }
        }
        val reqId = network.send(req, node)
        val resp = network.receiveResponse(reqId)
        if (!resp.hasChatInfoResp()
            || !validateName(resp.chatInfoResp.name)
            || resp.chatInfoResp.membersCount > 1000) {

            throw GvInvalidResponseException()
        }

        return resp.chatInfoResp.run {
            val photoBitmap = if (hasPhoto) {
                decodeBitmap(photo.toByteArray())
            } else {
                null
            }
            ChatInfo(name, ownerId, photoBitmap, membersList)
        }
    }
}