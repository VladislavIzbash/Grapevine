package ru.vizbash.grapevine.ui.main

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.GVException
import ru.vizbash.grapevine.ProfileProvider
import ru.vizbash.grapevine.ProfileService
import ru.vizbash.grapevine.network.GrapevineNetwork
import ru.vizbash.grapevine.network.Node
import ru.vizbash.grapevine.storage.LoginPrefs
import ru.vizbash.grapevine.storage.contacts.ContactEntity
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val loginPrefs: LoginPrefs,
    private val grapevineNetwork: GrapevineNetwork,
    private val profileService: ProfileService,
) : ViewModel() {
    val currentProfile = profileService.profile

    private val photoCache = ConcurrentHashMap<Node, Bitmap?>()

    val availableNodes = grapevineNetwork.availableNodes
    val contacts = profileService.contactList

    private val _networkError = MutableStateFlow(Optional.empty<GVException>())
    val networkError = _networkError.asStateFlow()

    fun disableAutologin() {
        loginPrefs.autoLoginUsername = null
        loginPrefs.autoLoginPassword = null
    }

    fun startGrapevineNetwork() {
        grapevineNetwork.start()
    }

    suspend fun fetchPhoto(node: Node): Bitmap? = photoCache.getOrPut(node) {
        try {
            grapevineNetwork.fetchNodePhoto(node)
        } catch (e: GVException) {
            e.printStackTrace()
            null
        }
    }

    fun addToContacts(node: Node) {
        viewModelScope.launch {
            try {
                grapevineNetwork.sendAddContactRequest(node)
                _networkError.value = Optional.empty()
                profileService.addContact(node, fetchPhoto(node), ContactEntity.State.OUTGOING)
            } catch (e: GVException) {
                _networkError.value = Optional.of(e)
            }
        }
    }
}
