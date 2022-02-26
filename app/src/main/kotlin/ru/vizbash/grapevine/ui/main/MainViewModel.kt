package ru.vizbash.grapevine.ui.main

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.GVException
import ru.vizbash.grapevine.ProfileService
import ru.vizbash.grapevine.network.GrapevineNetwork
import ru.vizbash.grapevine.network.Node
import ru.vizbash.grapevine.storage.LoginPrefs
import ru.vizbash.grapevine.storage.contacts.ContactEntity
import ru.vizbash.grapevine.storage.messages.MessageEntity
import ru.vizbash.grapevine.storage.messages.MessageWithOrig
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val loginPrefs: LoginPrefs,
    private val grapevineNetwork: GrapevineNetwork,
    private val profileService: ProfileService,
) : ViewModel() {
    val currentProfile = profileService.profile

    val availableNodes = grapevineNetwork.availableNodes
    val contacts = profileService.contactList

    private val _networkError = MutableStateFlow<GVException?>(null)
    val networkError = _networkError.asStateFlow()

    fun disableAutologin() {
        loginPrefs.autoLoginUsername = null
        loginPrefs.autoLoginPassword = null
    }

    fun startGrapevineNetwork() {
        grapevineNetwork.start()
    }

    fun fetchPhotoAsync(node: Node): Deferred<Bitmap?> {
        return try {
            viewModelScope.async {
                grapevineNetwork.fetchNodePhoto(node)
            }
        } catch (e: GVException) {
            e.printStackTrace()
            CompletableDeferred(null)
        }
    }

    fun addToContacts(node: Node) {
        viewModelScope.launch {
            try {
                grapevineNetwork.sendContactInvitation(node)
                _networkError.value = null
                profileService.addContact(node, fetchPhotoAsync(node).await(), ContactEntity.State.OUTGOING)
            } catch (e: GVException) {
                _networkError.value = e
            }
        }
    }

    fun cancelContactInvitation(contact: ContactEntity) {
        viewModelScope.launch {
            profileService.deleteContact(contact)
        }
    }

    fun answerContactInvitation(contact: ContactEntity, accepted: Boolean) {
        viewModelScope.launch {
            try {
                availableNodes.value.find { it.id == contact.nodeId }?.let {
                    grapevineNetwork.sendContactInvitationAnswer(it, accepted)
                } // TODO: notify user node not found
                _networkError.value = null

                if (accepted) {
                    profileService.setContactState(contact, ContactEntity.State.ACCEPTED)
                } else {
                    profileService.deleteContact(contact)
                }

            } catch (e: GVException) {
                _networkError.value = e
            }
        }
    }

    fun getLastMessage(contact: ContactEntity): Flow<MessageEntity?> {
        return profileService.getLastMessage(contact)
    }
}
