package ru.vizbash.grapevine.ui.main

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.GvException
import ru.vizbash.grapevine.network.Node
import ru.vizbash.grapevine.network.NodeProvider
import ru.vizbash.grapevine.network.dispatch.PhotoDispatcher
import ru.vizbash.grapevine.service.ChatService
import ru.vizbash.grapevine.service.MessageService
import ru.vizbash.grapevine.service.profile.ProfileService
import ru.vizbash.grapevine.storage.chat.Chat
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val profileService: ProfileService,
    private val nodeProvider: NodeProvider,
    private val photoDispatcher: PhotoDispatcher,
    private val chatService: ChatService,
    private val messageService: MessageService,
) : ViewModel() {
    val profile get() = profileService.profile

    val searchQuery = MutableStateFlow("")

    val chatList = chatService.chats.combine(searchQuery) { chats, query ->
        chats.filter { it.name.contains(query, true) }
    }

    val nodeList = nodeProvider.availableNodes.combine(searchQuery) { nodes, query ->
        nodes.filter { it.username.contains(query, true) }
    }

    fun disableAutoLogin() = profileService.disableAutoLogin()

    fun fetchPhotoAsync(node: Node): Deferred<Bitmap?> {
        return viewModelScope.async {
            try {
                photoDispatcher.fetchPhoto(node)
            } catch (e: GvException) {
                null
            }
        }
    }

    fun getLastMessage(chatId: Long) = messageService.getLastMessage(chatId)

    suspend fun createDialogChat(node: Node) {
        val knownNode = chatService.rememberNode(node)
        if (chatService.getChat(knownNode.id) == null) {
            chatService.createDialogChat(knownNode)
        }
    }

    fun createGroupChat(name: String, photoUri: Uri?) {
        viewModelScope.launch {
            chatService.createGroupChat(name, photoUri)
        }
    }

    fun deleteChat(chat: Chat) {
        viewModelScope.launch {
            chatService.deleteChat(chat)
        }
    }

    fun getOnlineFlow(nodeId: Long) = nodeProvider.availableNodes.map {
        nodeProvider.get(nodeId) != null
    }
}