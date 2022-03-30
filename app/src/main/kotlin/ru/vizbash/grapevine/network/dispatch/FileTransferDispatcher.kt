package ru.vizbash.grapevine.network.dispatch

import android.util.Log
import com.google.protobuf.ByteString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.GvException
import ru.vizbash.grapevine.GvInvalidResponseException
import ru.vizbash.grapevine.network.DispatcherCoroutineScope
import ru.vizbash.grapevine.network.Node
import ru.vizbash.grapevine.network.message.*
import ru.vizbash.grapevine.storage.message.MessageFile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileTransferDispatcher @Inject constructor(
    private val network: NetworkController,
    @DispatcherCoroutineScope private val coroutineScope: CoroutineScope,
) {
    companion object {
        private const val TAG = "FileTransferDispatcher"
    }

    private var getNextChunk: (suspend (Long, Node) -> ByteArray?)? = null

    init {
        coroutineScope.launch {
            network.acceptedMessages
                .filter { it.payload.hasDownloadReq() }
                .collect(::handleDownloadRequest)
        }
    }

    fun setFileChunkProvider(getNextChunk: suspend (Long, Node) -> ByteArray?) {
        this.getNextChunk = getNextChunk
    }

    private suspend fun handleDownloadRequest(req: AcceptedMessage) {
        Log.d(TAG, "Received file download request from ${req.sender}")

        val msgId = req.payload.downloadReq.msgId

        coroutineScope.launch(Dispatchers.IO) {
            var chunkNum = 0

            try {
                while (true) {
                    val chunk = getNextChunk?.invoke(msgId, req.sender) ?: break

                    Log.d(
                        TAG,
                        "Sending file chunk #$chunkNum (${chunk.size} bytes) to ${req.sender}"
                    )

                    val resp = routedPayload {
                        response = routedResponse {
                            requestId = req.id
                            error = RoutedMessages.Error.NO_ERROR
                            fileChunkResp = fileChunkResponse {
                                this.chunkNum = chunkNum
                                this.chunk = ByteString.copyFrom(chunk)
                            }
                        }
                    }
                    network.send(resp, req.sender)

                    chunkNum++
                }
            } catch (e: GvException) {
            }
        }
    }

    suspend fun downloadFile(msgId: Long, file: MessageFile, node: Node): Flow<ByteArray> {
        Log.d(TAG, "Sending file download request to $node")

        val req = routedPayload {
            downloadReq = fileDownloadRequest {
                this.msgId = msgId
            }
        }
        val reqId = network.send(req, node)

        return flow {
            var prevNum = -1
            var receivedBytes = 0

            while (receivedBytes <= file.size) {
                val resp = network.receiveResponse(reqId)
                val chunkResp = if (resp.hasFileChunkResp()) {
                    resp.fileChunkResp
                } else {
                    throw GvInvalidResponseException()
                }

                if (chunkResp.chunkNum != prevNum + 1) {
                    throw GvInvalidResponseException()
                }

                emit(chunkResp.chunk.toByteArray())

                receivedBytes += chunkResp.chunk.size()
                prevNum++
            }
        }
    }
}