package ru.vizbash.grapevine.ui.main

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.network.Node
import ru.vizbash.grapevine.service.GrapevineService
import ru.vizbash.grapevine.storage.contacts.ContactEntity
import ru.vizbash.grapevine.util.GVException

class MainViewModel : ViewModel() {
    lateinit var service: GrapevineService

    private val _networkError = MutableStateFlow<GVException?>(null)
    val networkError = _networkError.asStateFlow()

    fun fetchPhotoAsync(node: Node): Deferred<Bitmap?> {
        return try {
            viewModelScope.async {
                service.fetchNodePhoto(node)
            }
        } catch (e: GVException) {
            e.printStackTrace()
            viewModelScope.async { null }
        }
    }

    private fun launchCatching(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                _networkError.value = null
            } catch (e: GVException) {
                _networkError.value = e
            }
        }
    }

    fun addToContacts(node: Node) = launchCatching {
        service.sendContactInvitation(node)
    }

    fun cancelContactInvitation(contact: ContactEntity) = launchCatching {
        service.cancelContactInvitation(contact)
    }

    fun acceptContact(contact: ContactEntity) = launchCatching {
        service.acceptContact(contact)
    }

    fun rejectContact(contact: ContactEntity) = launchCatching {
        service.rejectContact(contact)
    }

    fun createChat(name: String) = launchCatching {
        service.createChat(name)
    }
}
