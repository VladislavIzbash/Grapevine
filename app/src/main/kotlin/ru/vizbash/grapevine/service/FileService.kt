package ru.vizbash.grapevine.service

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.GrapevineApp
import ru.vizbash.grapevine.network.Node
import ru.vizbash.grapevine.network.NodeProvider
import ru.vizbash.grapevine.network.dispatch.FileTransferDispatcher
import ru.vizbash.grapevine.storage.message.Message
import ru.vizbash.grapevine.storage.message.MessageDao
import ru.vizbash.grapevine.storage.message.MessageFile
import java.io.File
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileService @Inject constructor(
    @ApplicationContext private val context: Context,
    @ServiceCoroutineScope private val coroutineScope: CoroutineScope,
    private val fileDispatcher: FileTransferDispatcher,
    private val messageDao: MessageDao,
    private val nodeProvider: NodeProvider,
) {
    companion object {
        private const val TAG = "FileService"

        private const val FILE_CHUNK_SIZE = 200_000
    }

    private val streamedFiles = mutableMapOf<Pair<Long, Long>, InputStream>()

    private val downloadJobs = mutableMapOf<Long, Job>()

    private val _downloadingFiles = mutableMapOf<Long, MutableStateFlow<Float?>>()
    val downloadingFiles: Map<Long, StateFlow<Float?>> = _downloadingFiles

    fun start() {
        fileDispatcher.setFileChunkProvider(::getNextFileChunk)
    }

    fun startFileDownload(msg: Message){
        cancelFileDownload(msg.id)
        downloadJobs[msg.id] = coroutineScope.launch(Dispatchers.IO) {
            downloadFile(msg)
        }
    }

    fun cancelFileDownload(msgId: Long) {
        downloadJobs[msgId]?.cancel()
        coroutineScope.launch {
            messageDao.setFileState(msgId, MessageFile.State.NOT_DOWNLOADED)
        }
    }

    fun cancelAllDownloads() {
        downloadJobs.keys.forEach(::cancelFileDownload)
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun downloadFile(msg: Message) {
        val progress = _downloadingFiles.getOrPut(msg.id) { MutableStateFlow(0F) }

        val outFile = File(GrapevineApp.downloadsDir, msg.file!!.name)
        val outStream = outFile.outputStream().buffered()

        var received = 0

        try {
            outStream.use {
                val sender = nodeProvider.getOrThrow(msg.senderId)

                messageDao.setFileState(msg.id, MessageFile.State.DOWNLOADING)
                fileDispatcher.downloadFile(msg.id, msg.file, sender).collect { chunk ->
                    outStream.write(chunk)
                    received += chunk.size

                    if (received < msg.file.size) {
                        progress.value = received.toFloat() / msg.file.size.toFloat()
                        Log.d(TAG, "Downloading ${msg.file.name} ${progress.value!! * 100}%")
                    } else {
                        Log.d(TAG, "Downloaded file ${msg.file.name}")
                        progress.value = 1F

                        val uri = FileProvider.getUriForFile(
                            context,
                            "ru.vizbash.grapevine.downloads",
                            outFile,
                        )
                        messageDao.setFileUri(msg.id, uri)
                        messageDao.setFileState(msg.id, MessageFile.State.DOWNLOADED)
                        return@collect
                    }
                }
            }
        } catch (e: Exception) {
            if (e is IOException) {
                e.printStackTrace()
            }
            messageDao.setFileState(msg.id, MessageFile.State.FAILED)
            progress.value = null
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun getNextFileChunk(msgId: Long, node: Node, start: Boolean): ByteArray? {
        try {
            if (start) {
                streamedFiles[Pair(msgId, node.id)]?.close()

                val uri = messageDao.getById(msgId)?.file?.uri ?: return null
                val stream = context.contentResolver.openInputStream(uri)!!.buffered()
                streamedFiles[Pair(msgId, node.id)] = stream
            }

            val stream = streamedFiles[Pair(msgId, node.id)] ?: return null

            val buffer = ByteArray(FILE_CHUNK_SIZE)
            val read = stream.read(buffer)
            if (read < 0) {
                stream.close()
                streamedFiles.remove(Pair(msgId, node.id))
                return null
            }

            return buffer.sliceArray(0 until read)
        } catch (e: IOException) {
            e.printStackTrace()
            return null
        }
    }
}