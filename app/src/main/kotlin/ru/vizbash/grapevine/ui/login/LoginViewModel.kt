package ru.vizbash.grapevine.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.R
import ru.vizbash.grapevine.service.profile.ProfileService
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val profileService: ProfileService,
) : ViewModel() {

    sealed class State {
        object LoggedOut: State()
        object LoggingIn: State()
        object LoggedIn : State()
        class LoginError(val error: Int) : State()
    }

    private val _state = MutableStateFlow<State>(State.LoggedOut)
    val state = _state.asStateFlow()

    val username get() = profileService.storedName!!

    fun login(password: String, autoLogin: Boolean) {
        viewModelScope.launch {
            _state.value = State.LoggingIn

            if (profileService.tryLogin(password, autoLogin)) {
                _state.value = State.LoggedIn
            } else {
                _state.value = State.LoginError(R.string.invalid_password)
            }
        }
    }
}