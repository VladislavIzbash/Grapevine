package ru.vizbash.grapevine.service

import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.GvException
import ru.vizbash.grapevine.network.Node
import ru.vizbash.grapevine.network.NodeProvider
import ru.vizbash.grapevine.network.dispatch.GroupChatDispatcher
import ru.vizbash.grapevine.network.dispatch.PhotoDispatcher
import ru.vizbash.grapevine.service.profile.ProfileProvider
import ru.vizbash.grapevine.storage.chat.Chat
import ru.vizbash.grapevine.storage.chat.ChatDao
import ru.vizbash.grapevine.storage.chat.GroupChatMember
import ru.vizbash.grapevine.storage.node.KnownNode
import ru.vizbash.grapevine.storage.node.NodeDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class ChatService @Inject constructor(
    @ServiceCoroutineScope private val coroutineScope: CoroutineScope,
    private val chatDao: ChatDao,
    private val nodeDao: NodeDao,
    private val profileProvider: ProfileProvider,
    private val photoDispatcher: PhotoDispatcher,
    private val chatDispatcher: GroupChatDispatcher,
    private val nodeProvider: NodeProvider,
) {
    companion object {
        private const val TAG = "ChatService"
    }

    val chats = chatDao.observeAll()

    private val _ingoingChatInvitations = Channel<Chat>()
    val ingoingChatInvitations: ReceiveChannel<Chat> = _ingoingChatInvitations

    init {
        chatDispatcher.setChatInfoProvider(::getChatInfo)

        coroutineScope.launch {
            chatDispatcher.chatInvitations.collect { (node, chatId) ->
                receiveChatInvitation(node, chatId)
            }
        }
    }

    private suspend fun getChatInfo(chatId: Long, senderId: Long): GroupChatDispatcher.ChatInfo? {
        val members = chatDao.getGroupChatMembers(chatId)

        if (senderId !in members) {
            return null
        }

        return chatDao.getById(chatId)?.let {
            GroupChatDispatcher.ChatInfo(
                name = it.name,
                ownerId = it.ownerId ?: return@let null,
                photo = it.photo,
                members = chatDao.getGroupChatMembers(chatId),
            )
        }
    }

    suspend fun rememberNode(node: Node): KnownNode {
        val knownNode = KnownNode(
            id = node.id,
            username = node.username,
            pubKey = node.pubKey,
            photo = photoDispatcher.fetchPhoto(node),
        )
        nodeDao.insert(knownNode)
        return knownNode
    }

    suspend fun createDialog(knownNode: KnownNode) {
        chatDao.insert(Chat(
            id = knownNode.id,
            name = knownNode.username,
            photo = knownNode.photo,
            isGroup = false,
            ownerId = null,
        ))
    }

    suspend fun createGroupChat(name: String, photo: Bitmap?) {
        val chat = Chat(
            id = Random.nextLong(),
            name = name,
            photo = photo,
            isGroup = true,
            ownerId = profileProvider.profile.nodeId,
        )
        chatDao.insert(chat)
        chatDao.insertChatMembers(listOf(GroupChatMember(chat.id, chat.ownerId!!)))
    }

    suspend fun inviteToChat(chatId: Long, knownNode: KnownNode) {
        chatDispatcher.sendChatInvitation(chatId, nodeProvider.getOrThrow(knownNode.id))
        chatDao.insertChatMembers(listOf(GroupChatMember(chatId, knownNode.id)))
    }

    suspend fun deleteChat(chat: Chat) {
        chatDao.delete(chat)
    }

    private suspend fun receiveChatInvitation(node: Node, chatId: Long) {
        try {
            val chatInfo = chatDispatcher.fetchChatInfo(chatId, node)
            val chat = Chat(
                id = chatId,
                name = chatInfo.name,
                photo = chatInfo.photo,
                isGroup = true,
                ownerId = chatInfo.ownerId,
            )

            chatDao.insert(chat)
            chatDao.insertChatMembers(chatInfo.members.map { GroupChatMember(chatId, it) })

            _ingoingChatInvitations.send(chat)
        } catch (e: GvException) {
            Log.e(TAG, "Failed to add chat $chatId")
        }
    }

    private suspend fun refreshChatInfo(chatId: Long) {
        val chat = chatDao.getById(chatId) ?: return
        if (!chat.isGroup) {
            return
        }

        try {
            val chatInfo = chatDispatcher.fetchChatInfo(
                chatId,
                nodeProvider.getOrThrow(chat.ownerId!!),
            )
            chatDao.update(chat.copy(name = chatInfo.name, photo = chatInfo.photo))
            chatDao.insertChatMembers(chatInfo.members.map { GroupChatMember(chatId, it) })
        } catch (e: GvException) {
        }
    }
}