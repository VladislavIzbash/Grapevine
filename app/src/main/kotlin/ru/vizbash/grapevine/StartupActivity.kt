package ru.vizbash.grapevine

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import ru.vizbash.grapevine.storage.LoginPrefs
import ru.vizbash.grapevine.storage.profile.ProfileEntity
import ru.vizbash.grapevine.ui.login.LoginActivity
import ru.vizbash.grapevine.ui.main.MainActivity
import javax.inject.Inject

@AndroidEntryPoint
class StartupActivity : AppCompatActivity() {
    @Inject lateinit var profileService: IProfileService
    @Inject lateinit var loginPrefs: LoginPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        runBlocking {
            if (tryAutologin(profileService.profileList.first())) {
                startActivity(Intent(this@StartupActivity, MainActivity::class.java))
            } else {
                startActivity(Intent(this@StartupActivity, LoginActivity::class.java))
            }

            finish()
        }
    }

    private suspend fun tryAutologin(profiles: List<ProfileEntity>): Boolean {
        val username = loginPrefs.autoLoginUsername ?: return false
        val password = loginPrefs.autoLoginPassword ?: return false

        val profile = profiles.find { it.username == username } ?: return false
        return profileService.tryLogin(profile, password)
    }
}