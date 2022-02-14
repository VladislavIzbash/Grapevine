package ru.vizbash.grapevine.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.ProfileService
import ru.vizbash.grapevine.storage.LoginPrefs
import ru.vizbash.grapevine.storage.profile.ProfileEntity
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    val loginPrefs: LoginPrefs,
    private val profileService: ProfileService,
) : ViewModel() {
    enum class LoginState { NONE, LOADING, LOGGED_IN, FAILED }

    val profiles: StateFlow<List<ProfileEntity>> = profileService.profileList
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    private val _loginState = MutableStateFlow(LoginState.NONE)
    val loginState = _loginState.asStateFlow()

    fun login(profile: ProfileEntity, password: String, autologin: Boolean) {
        viewModelScope.launch {
            _loginState.value = LoginState.LOADING
            if (profileService.tryLogin(profile, password)) {
                loginPrefs.lastUsername = profile.username

                if (autologin) {
                    loginPrefs.autoLoginUsername = profile.username
                    loginPrefs.autoLoginPassword = password
                }

                _loginState.value = LoginState.LOGGED_IN
            } else {
                _loginState.value = LoginState.FAILED
            }
        }
    }
}