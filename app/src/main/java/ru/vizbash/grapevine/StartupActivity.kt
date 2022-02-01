package ru.vizbash.grapevine

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import ru.vizbash.grapevine.db.identity.Identity
import ru.vizbash.grapevine.ui.LoginActivity
import ru.vizbash.grapevine.ui.MainActivity
import ru.vizbash.grapevine.ui.NewIdentityActivity
import javax.inject.Inject

@AndroidEntryPoint
class StartupActivity : AppCompatActivity() {
    @Inject lateinit var authService: AuthService

    lateinit var idents: List<Identity>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        idents = authService.identityList()

        if (tryAutologin()) {
            startActivity(Intent(this, MainActivity::class.java))
        } else {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        finish()
    }

    private fun tryAutologin(): Boolean {
        val loginPrefs = getSharedPreferences(getString(R.string.login_prefs), MODE_PRIVATE)

        val username = loginPrefs.getString(getString(R.string.prefs_autologin_username), null)
            ?: return false
        val password = loginPrefs.getString(getString(R.string.prefs_autologin_password), null)
            ?: return false

        val ident = idents.find { it.username == username } ?: return false
        return authService.tryLogin(ident, password)
    }
}