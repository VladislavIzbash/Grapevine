package ru.vizbash.grapevine.network.dispatch

import android.graphics.Bitmap
import android.util.Log
import com.google.protobuf.ByteString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.GvInvalidResponseException
import ru.vizbash.grapevine.network.DispatcherCoroutineScope
import ru.vizbash.grapevine.network.Node
import ru.vizbash.grapevine.network.message.*
import ru.vizbash.grapevine.service.profile.ProfileProvider
import ru.vizbash.grapevine.util.decodeBitmap
import ru.vizbash.grapevine.util.encodeBitmap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhotoDispatcher @Inject constructor(
    private val network: NetworkController,
    @DispatcherCoroutineScope private val coroutineScope: CoroutineScope,
    private val profileProvider: ProfileProvider,
) {
    companion object {
        private const val TAG = "PhotoDispatcher"
    }

    private val photoCache = mutableMapOf<Long, Bitmap?>()

    init {
        coroutineScope.launch {
            network.acceptedMessages
                .filter { it.payload.hasPhotoReq() }
                .collect(this@PhotoDispatcher::handleRequest)
        }
    }

    private suspend fun handleRequest(msg: AcceptedMessage) {
        Log.d(TAG, "Received photo request from ${msg.sender}")

        val photo = profileProvider.profile.photo

        val photoResp = photoResponse {
            hasPhoto = photo != null

            if (photo != null) {
                this.photo = ByteString.copyFrom(encodeBitmap(photo))
            }
        }
        val resp = routedPayload {
            response = routedResponse {
                this.requestId = msg.id
                this.error = RoutedMessages.Error.NO_ERROR
                this.photoResp = photoResp
            }
        }
        network.send(resp, msg.sender)
    }

    suspend fun fetchPhoto(node: Node): Bitmap? = photoCache.getOrPut(node.id) {
        Log.d(TAG, "Sending photo request to $node")

        val req = routedPayload {
            photoReq = photoRequest {}
        }
        val reqId = network.send(req, node)
        val resp = network.receiveResponse(reqId)
        if (!resp.hasPhotoResp()) {
            throw GvInvalidResponseException()
        }

        return if (resp.photoResp.hasPhoto) {
            val photo = resp.photoResp.photo.toByteArray()
            decodeBitmap(photo)
        } else {
            null
        }
    }
}