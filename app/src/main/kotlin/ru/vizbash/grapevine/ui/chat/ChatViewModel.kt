package ru.vizbash.grapevine.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
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
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(savedState: SavedStateHandle) : ViewModel() {
    private val contactId = savedState.get<Long>(ChatActivity.EXTRA_CHAT_ID)!!

    lateinit var service: GrapevineService

    val contact by lazy {
        runBlocking {
            service.getContact(contactId)!!
        }
    }

    val pagedMessages = Pager(PagingConfig(pageSize = 25, enablePlaceholders = false)) {
        service.getContactMessages(contact)
    }.flow.cachedIn(viewModelScope)

    val forwardedMessage = MutableStateFlow<MessageEntity?>(null)

    val attachedFile = MutableStateFlow<MessageFile?>(null)

    fun sendMessage(text: String) {
        viewModelScope.launch {
            service.sendMessage(contact, text, forwardedMessage.value, attachedFile.value)
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