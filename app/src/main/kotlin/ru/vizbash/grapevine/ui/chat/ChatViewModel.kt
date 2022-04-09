package ru.vizbash.grapevine.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.service.ChatService
import ru.vizbash.grapevine.service.FileService
import ru.vizbash.grapevine.service.MessageService
import ru.vizbash.grapevine.service.profile.ProfileProvider
import ru.vizbash.grapevine.storage.message.Message
import ru.vizbash.grapevine.storage.message.MessageFile
import ru.vizbash.grapevine.storage.node.KnownNode
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val profileProvider: ProfileProvider,
    private val messageService: MessageService,
    private val fileService: FileService,
    private val chatService: ChatService,
) : ViewModel() {
    val chatId = savedState.get<Long>(ChatFragment.ARG_CHAT_ID) ?: 0
    val groupMode = savedState.get<Boolean>(ChatFragment.ARG_GROUP_MODE) ?: true

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
            messageService.markAsRead(message)
        }
    }

    fun sendMessage(text: String) {
        viewModelScope.launch {
            messageService.sendMessage(chatId, text, attachedMessage.value?.id, attachedFile.value)
            attachedMessage.value = null
            attachedFile.value = null
        }
    }

    suspend fun isMember() = chatService.isMemberOfChat(chatId, profile.nodeId)

    fun getDownloadProgress(msgId: Long) = fileService.downloadingFiles[msgId]

    fun startFileDownload(msg: Message) = fileService.startFileDownload(msg)

    fun cancelFileDownload(msg: Message) = fileService.cancelFileDownload(msg.id)
}