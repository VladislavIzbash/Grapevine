package ru.vizbash.grapevine.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.runBlocking
import ru.vizbash.grapevine.ProfileService
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val profileService: ProfileService,
    savedState: SavedStateHandle,
) : ViewModel() {
    val contact = runBlocking {
        profileService.getContact(savedState.get<Long>(ChatActivity.EXTRA_CONTACT_ID)!!)
    }

    val pagedMessages = profileService
        .getContactMessages(contact, 10)
        .cachedIn(viewModelScope)


}