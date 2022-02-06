package ru.vizbash.grapevine.ui.newprofile

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.AuthService
import ru.vizbash.grapevine.storage.LoginPrefs
import javax.inject.Inject

@HiltViewModel
class NewProfileModel @Inject constructor(
    private val loginPrefs: LoginPrefs,
    private val authService: AuthService,
) : ViewModel() {
    enum class CreationState { NONE, LOADING, CREATED }

    private val _creationState = MutableStateFlow(CreationState.NONE)
    val creationState = _creationState.asStateFlow()

    fun createProfileAndLogin(username: String, password: String, photo: Bitmap?) {
        _creationState.value = CreationState.LOADING

        viewModelScope.launch {
            loginPrefs.lastUsername = username

            authService.createProfileAndLogin(username, password, photo)

            _creationState.value = CreationState.CREATED
        }
    }
}