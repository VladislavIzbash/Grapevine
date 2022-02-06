package ru.vizbash.grapevine.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import ru.vizbash.grapevine.AuthService
import ru.vizbash.grapevine.storage.LoginPrefs
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    authService: AuthService,
    private val loginPrefs: LoginPrefs,
) : ViewModel() {
    val currentProfile = authService.currentProfile!!

    fun disableAutologin() {
        loginPrefs.autoLoginUsername = null
        loginPrefs.autoLoginPassword = null
    }
}