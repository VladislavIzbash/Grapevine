package ru.vizbash.grapevine.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import ru.vizbash.grapevine.AuthService
import ru.vizbash.grapevine.network.NetworkController
import ru.vizbash.grapevine.storage.LoginPrefs
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    authService: AuthService,
    private val loginPrefs: LoginPrefs,
    private val networkController: NetworkController,
) : ViewModel() {
    val currentProfile = authService.currentProfile!!

    fun disableAutologin() {
        loginPrefs.autoLoginUsername = null
        loginPrefs.autoLoginPassword = null
    }

    val nodeEntries = networkController.nodeList.map { nodes ->
        nodes.map { node -> NodeEntry(node, null) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())
}
