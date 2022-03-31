package ru.vizbash.grapevine.service

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    @ApplicationContext private val context: Context,
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

    suspend fun getChatById(chatId: Long) = chatDao.getById(chatId)

    private suspend fun getChatInfo(chatId: Long, senderId: Long): GroupChatDispatcher.ChatInfo? {
        val members = chatDao.getGroupChatMemberIds(chatId)

        if (senderId !in members) {
            return null
        }

        return chatDao.getById(chatId)?.let {
            GroupChatDispatcher.ChatInfo(
                name = it.name,
                ownerId = it.ownerId ?: return@let null,
                photo = it.photo,
                members = chatDao.getGroupChatMemberIds(chatId),
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

    suspend fun createDialogChat(knownNode: KnownNode) {
        chatDao.insert(Chat(
            id = knownNode.id,
            name = knownNode.username,
            photo = knownNode.photo,
            isGroup = false,
            ownerId = null,
        ))
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun createGroupChat(name: String, photoUri: Uri?) {
        val photo = photoUri?.let {
            withContext(Dispatchers.IO) {
                val input = context.contentResolver.openInputStream(it)
                BitmapFactory.decodeStream(input)
            }
        }

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

    suspend fun resolveNode(nodeId: Long): KnownNode? {
        nodeDao.getById(nodeId)?.let {
            return it
        }

        return nodeProvider.get(nodeId)?.let {
            rememberNode(it)
        }
    }

    suspend fun inviteToChat(chatId: Long, knownNode: KnownNode) {
        chatDispatcher.sendChatInvitation(chatId, nodeProvider.getOrThrow(knownNode.id))
        chatDao.insertChatMembers(listOf(GroupChatMember(chatId, knownNode.id)))
    }

    fun getGroupChatMembers(chatId: Long): Flow<List<KnownNode>> {
        return chatDao.observeChatMembers(chatId).map { it.members }
    }

    suspend fun deleteChat(chat: Chat) = chatDao.delete(chat)

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

    suspend fun refreshChatInfo(chatId: Long) {
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