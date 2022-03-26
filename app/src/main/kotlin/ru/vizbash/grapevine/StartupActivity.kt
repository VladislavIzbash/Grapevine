package ru.vizbash.grapevine

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import ru.vizbash.grapevine.service.profile.ProfileService
import ru.vizbash.grapevine.ui.login.LoginActivity
import ru.vizbash.grapevine.ui.main.MainActivity
import ru.vizbash.grapevine.ui.newprofile.NewProfileActivity
import javax.inject.Inject

@AndroidEntryPoint
class StartupActivity : AppCompatActivity() {
    @Inject lateinit var profileService: ProfileService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (profileService.hasProfile) {
            runBlocking {
                if (profileService.tryAutoLogin()) {
                    startActivity(Intent(this@StartupActivity, MainActivity::class.java))
                } else {
                    startActivity(Intent(this@StartupActivity, LoginActivity::class.java))
                }
            }
        } else {
            startActivity(Intent(this, NewProfileActivity::class.java))
        }
    }
}