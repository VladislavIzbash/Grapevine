package ru.vizbash.grapevine.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.network.NodeProvider
import ru.vizbash.grapevine.service.ChatService
import javax.inject.Inject

@HiltViewModel
class ChatMembersViewModel @Inject constructor(
    savedState: SavedStateHandle,
    chatService: ChatService,
    private val nodeProvider: NodeProvider,
) : ViewModel() {
    private val chatId = savedState.get<Long>(ChatMembersActivity.EXTRA_CHAT_ID)!!

    val chatMembers = chatService.getGroupChatMembers(chatId)

    init {
        viewModelScope.launch {
            chatService.refreshChatInfo(chatId)
        }
    }

    fun isNodeOnline(nodeId: Long) = nodeProvider.get(nodeId) != null
}