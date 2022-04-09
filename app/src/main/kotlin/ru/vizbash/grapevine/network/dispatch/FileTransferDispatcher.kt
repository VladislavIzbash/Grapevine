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
import kotlinx.coroutines.withContext
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

    private var getNextChunk: (suspend (Long, Node, Boolean) -> ByteArray?)? = null

    init {
        coroutineScope.launch {
            network.acceptedMessages
                .filter { it.payload.hasDownloadReq() }
                .collect(::handleDownloadRequest)
        }
    }

    fun setFileChunkProvider(getNextChunk: suspend (Long, Node, Boolean) -> ByteArray?) {
        this.getNextChunk = getNextChunk
    }

    private suspend fun handleDownloadRequest(req: AcceptedMessage) {
        Log.d(TAG, "Received file download request from ${req.sender}")

        val msgId = req.payload.downloadReq.msgId
        val start = req.payload.downloadReq.start

        withContext(Dispatchers.IO) {
            val chunk = getNextChunk?.invoke(msgId, req.sender, start) ?: return@withContext

            Log.d(TAG, "Sending file chunk (${chunk.size} bytes) to ${req.sender}")

            val resp = routedPayload {
                response = routedResponse {
                    requestId = req.id
                    error = RoutedMessages.Error.NO_ERROR
                    fileChunkResp = fileChunkResponse {
                        this.chunk = ByteString.copyFrom(chunk)
                    }
                }
            }

            try {
                network.send(resp, req.sender)
            } catch (e: GvException) {
            }
        }
    }

    suspend fun downloadFile(msgId: Long, file: MessageFile, node: Node): Flow<ByteArray> {
        Log.d(TAG, "Sending file download request to $node")

        var req = routedPayload {
            downloadReq = fileDownloadRequest {
                this.msgId = msgId
                this.start = true
            }
        }
        var reqId = network.send(req, node)

        return flow {
            var receivedBytes = 0

            while (true) {
                val resp = network.receiveResponse(reqId)
                val chunk = if (resp.hasFileChunkResp()) {
                    resp.fileChunkResp.chunk
                } else {
                    throw GvInvalidResponseException()
                }

                receivedBytes += chunk.size()

                Log.d(TAG, "Received file chunk (${chunk.size()} bytes) from $node")

                emit(chunk.toByteArray())

                if (receivedBytes >= file.size) {
                    break
                }

                req = routedPayload {
                    downloadReq = fileDownloadRequest {
                        this.msgId = msgId
                    }
                }
                reqId = network.send(req, node)
            }
        }
    }
}