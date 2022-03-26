package ru.vizbash.grapevine.ui.main

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import ru.vizbash.grapevine.service.profile.ProfileProvider
import ru.vizbash.grapevine.service.profile.ProfileService
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val profileService: ProfileService
) : ViewModel() {
    val profile get() = profileService.profile

    fun disableAutoLogin() = profileService.disableAutoLogin()
}