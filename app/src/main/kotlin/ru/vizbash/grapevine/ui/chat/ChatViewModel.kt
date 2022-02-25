package ru.vizbash.grapevine.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import ru.vizbash.grapevine.GVException
import ru.vizbash.grapevine.ProfileService
import ru.vizbash.grapevine.network.GrapevineNetwork
import ru.vizbash.grapevine.storage.messages.MessageEntity
import javax.inject.Inject
import kotlin.random.Random

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val profileService: ProfileService,
    private val grapevineNetwork: GrapevineNetwork,
    savedState: SavedStateHandle,
) : ViewModel() {
    val contact = runBlocking {
        profileService.getContact(savedState.get<Long>(ChatActivity.EXTRA_CONTACT_ID)!!)!!
    }

    val pagedMessages = Pager(PagingConfig(pageSize = 25, enablePlaceholders = false)) {
        profileService.getContactMessages(contact)
    }.flow.cachedIn(viewModelScope)

    fun sendMessage(text: String) {
        viewModelScope.launch {
            val id = Random.nextLong()
            profileService.addSentMessage(contact, text, id)

            grapevineNetwork.availableNodes.value.find { it.id == contact.nodeId }?.let {
                try {
                    grapevineNetwork.sendTextMessage(text, it, id)
                    profileService.setMessageState(id, MessageEntity.State.DELIVERED)
                } catch (e: GVException) {
                    profileService.setMessageState(id, MessageEntity.State.DELIVERY_FAILED)
                }
            }
        }
    }
}