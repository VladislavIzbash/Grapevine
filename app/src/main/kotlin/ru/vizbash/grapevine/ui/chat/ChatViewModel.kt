package ru.vizbash.grapevine.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import ru.vizbash.grapevine.service.GrapevineService
import ru.vizbash.grapevine.storage.messages.MessageEntity
import ru.vizbash.grapevine.storage.messages.MessageFile
import ru.vizbash.grapevine.storage.messages.MessageWithUsername
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(savedState: SavedStateHandle) : ViewModel() {
    val chatId = savedState.get<Long>(ChatActivity.EXTRA_CHAT_ID)!!
    val chatMode = savedState.get<Boolean>(ChatActivity.EXTRA_CHAT_MODE) ?: false

    lateinit var service: GrapevineService

    private val contact by lazy {
        runBlocking { service.getContact(chatId)!! }
    }

    private val chat by lazy {
        runBlocking { service.getChat(chatId)!! }
    }

    val chatName get() = if (chatMode) chat.name else contact.username

    val photo get() = if (chatMode) null else contact.photo

    val pagedMessages = Pager(PagingConfig(pageSize = 25, enablePlaceholders = false)) {
        service.getChatMessages(chatId)
    }.flow.cachedIn(viewModelScope)

    val forwardedMessage = MutableStateFlow<MessageWithUsername?>(null)

    val attachedFile = MutableStateFlow<MessageFile?>(null)

    fun sendMessage(text: String) {
        viewModelScope.launch {
            service.sendMessage(contact, text, forwardedMessage.value?.msg, attachedFile.value)
        }
    }

    fun markAsRead(msg: MessageEntity) {
        if (msg.state == MessageEntity.State.READ) {
            return
        }

        viewModelScope.launch {
            service.markAsRead(msg.id, msg.senderId)
        }
    }

}