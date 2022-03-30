package ru.vizbash.grapevine.ui.chat

import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.service.ChatService
import ru.vizbash.grapevine.service.MessageService
import ru.vizbash.grapevine.service.profile.ProfileProvider
import ru.vizbash.grapevine.storage.message.Message
import ru.vizbash.grapevine.storage.message.MessageFile
import ru.vizbash.grapevine.storage.message.MessageWithOrig
import ru.vizbash.grapevine.storage.node.KnownNode
import java.util.*
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val profileProvider: ProfileProvider,
    private val messageService: MessageService,
    private val chatService: ChatService,
) : ViewModel() {
    private val chatId = savedState.get<Long>(ChatFragment.ARG_CHAT_ID)!!
    private val groupMode = savedState.get<Boolean>(ChatFragment.ARG_GROUP_MODE)!!

    val pagedMessages = Pager(PagingConfig(pageSize = 25, enablePlaceholders = false)) {
        messageService.getChatMessages(chatId)
    }.flow.cachedIn(viewModelScope)

    val profile get() = profileProvider.profile

    private val chatNodes = mutableMapOf<Long, Deferred<KnownNode?>>()

    var attachedMessage = MutableStateFlow<Message?>(null)
    var attachedFile = MutableStateFlow<MessageFile?>(null)

    init {
        chatNodes[profile.nodeId] = viewModelScope.async { profile.toKnownNode() }

        if (!groupMode) {
            chatNodes[chatId] = viewModelScope.async {
                chatService.resolveNode(chatId)
            }
        }
    }

    suspend fun getMessageSender(senderId: Long): KnownNode? {
        val node = chatNodes.getOrPut(senderId) {
            viewModelScope.async { chatService.resolveNode(senderId) }
        }
        return node.await()
    }

    fun markAsRead(message: Message) {
        viewModelScope.launch {
            messageService.markAsRead(message.id, message.senderId)
        }
    }

    fun sendMessage(text: String) {
        viewModelScope.launch {
            messageService.sendMessage(chatId, text, attachedMessage.value?.id, attachedFile.value)
            attachedMessage.value = null
            attachedFile.value = null
        }
    }

    fun getDownloadProgress(file: MessageFile) = messageService.downloadingFiles[file]

    fun startFileDownload(msg: Message) = messageService.startFileDownload(msg)
}