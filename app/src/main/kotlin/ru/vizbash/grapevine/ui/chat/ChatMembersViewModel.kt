package ru.vizbash.grapevine.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.network.NodeProvider
import ru.vizbash.grapevine.service.ChatService
import ru.vizbash.grapevine.service.profile.ProfileProvider
import javax.inject.Inject

@HiltViewModel
class ChatMembersViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val chatService: ChatService,
    private val nodeProvider: NodeProvider,
    profileProvider: ProfileProvider,
) : ViewModel() {
    private val chatId = savedState.get<Long>(ChatMembersActivity.EXTRA_CHAT_ID)!!
    val isAdmin = savedState.get<Boolean>(ChatMembersActivity.EXTRA_IS_ADMIN)!!

    val myId = profileProvider.profile.nodeId

    val chatMembers = chatService.getGroupChatMembers(chatId)

    private val chat = viewModelScope.async { chatService.getChat(chatId)!! }

    init {
        viewModelScope.launch {
            chatService.refreshChatInfo(chat.await())
        }
    }

    fun isNodeOnline(nodeId: Long) = nodeProvider.get(nodeId) != null

    fun invite(nodeId: Long) {
        viewModelScope.launch {
            chatService.resolveNode(nodeId)?.let {
                chatService.inviteToChat(chatId, it)
            }
        }
    }

    fun kick(nodeId: Long) {
        viewModelScope.launch {
            chatService.kickChatMember(chat.await(), nodeId)
        }
    }

    fun leaveChat(onLeaved: () -> Unit) {
        viewModelScope.launch {
            chatService.leaveChat(chat.await())
            onLeaved()
        }
    }
}