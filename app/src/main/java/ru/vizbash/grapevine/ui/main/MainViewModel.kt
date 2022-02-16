package ru.vizbash.grapevine.ui.main

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import ru.vizbash.grapevine.ProfileService
import ru.vizbash.grapevine.network.NetworkController
import ru.vizbash.grapevine.network.Node
import ru.vizbash.grapevine.storage.LoginPrefs
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val loginPrefs: LoginPrefs,
    private val networkController: NetworkController,
    profileService: ProfileService,
) : ViewModel() {
    val currentProfile = profileService.currentProfile

    private val photoCache = ConcurrentHashMap<Node, Bitmap?>()

    val nodeEntries = networkController.nodes.map { nodes ->
        nodes.map { node -> NodeEntry(node) { fetchPhoto(node) } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    fun disableAutologin() {
        loginPrefs.autoLoginUsername = null
        loginPrefs.autoLoginPassword = null
    }

    private suspend fun fetchPhoto(node: Node): Bitmap? = photoCache.getOrPut(node) {
        networkController.fetchNodePhoto(node)
    }
}
