package ru.vizbash.grapevine

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.storage.LoginPrefs
import ru.vizbash.grapevine.storage.profile.Profile
import ru.vizbash.grapevine.ui.login.LoginActivity
import ru.vizbash.grapevine.ui.MainActivity
import javax.inject.Inject

@AndroidEntryPoint
class StartupActivity : AppCompatActivity() {
    @Inject lateinit var authService: AuthService
    @Inject lateinit var loginPrefs: LoginPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            if (tryAutologin(authService.getProfileList().first())) {
                startActivity(Intent(this@StartupActivity, MainActivity::class.java))
            } else {
                startActivity(Intent(this@StartupActivity, LoginActivity::class.java))
            }

            finish()
        }
    }

    override fun onStart() {
        super.onStart()
        setVisible(true)
    }

    private suspend fun tryAutologin(profiles: List<Profile>): Boolean {
        val username = loginPrefs.autoLoginUsername ?: return false
        val password = loginPrefs.autoLoginPassword ?: return false

        val profile = profiles.find { it.username == username } ?: return false
        return authService.tryLogin(profile, password)
    }
}