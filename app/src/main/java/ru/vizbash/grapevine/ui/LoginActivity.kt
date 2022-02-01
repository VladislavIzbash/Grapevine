package ru.vizbash.grapevine.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import ru.vizbash.grapevine.AuthService
import ru.vizbash.grapevine.R
import ru.vizbash.grapevine.databinding.ActivityLoginBinding
import ru.vizbash.grapevine.db.identity.Identity
import javax.inject.Inject

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {
    private lateinit var ui: ActivityLoginBinding

    @Inject lateinit var authService: AuthService

    private lateinit var idents: List<Identity>

    private val loginPrefs by lazy {
        getSharedPreferences(getString(R.string.login_prefs), MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = ActivityLoginBinding.inflate(layoutInflater)

        populateIdents()

        ui.buttonLogin.setOnClickListener(this::onLoginClicked)

        val isAutoLogin = loginPrefs.contains(getString(R.string.prefs_autologin_username))
        ui.checkAutoLogin.isChecked = isAutoLogin

        ui.buttonNewIdentity.setOnClickListener {
            startActivity(Intent(this, NewIdentityActivity::class.java))
        }

        setContentView(ui.root)
    }

    override fun onResume() {
        super.onResume()

        populateIdents()

        val lastUsername = loginPrefs.getString(getString(R.string.prefs_last_username), null)
        if (lastUsername != null) {
            val index = idents.indexOfFirst { it.username == lastUsername }
            ui.spinnerUsername.setSelection(index)
        }
    }

    private fun populateIdents() {
        idents = authService.identityList()

        val empty = idents.isEmpty()

        ui.spinnerUsername.isEnabled = !empty
        ui.buttonLogin.isEnabled = !empty
        ui.spinnerUsername.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            if (!empty) idents.map(Identity::username::get) else listOf("Нет личностей"),
        )
    }

    private fun onLoginClicked(v: View) {
        val ident = idents[ui.spinnerUsername.selectedItemPosition]
        val password = ui.editPassword.text.toString()

        if (!authService.tryLogin(ident, password)) {
            ui.labelLoginError.visibility = View.VISIBLE
            return
        }

        ui.labelLoginError.visibility = View.INVISIBLE

        with(loginPrefs.edit()) {
            putString(getString(R.string.prefs_last_username), ident.username)

            if (ui.checkAutoLogin.isChecked) {
                putString(getString(R.string.prefs_autologin_username), ident.username)
                putString(getString(R.string.prefs_autologin_password), password)
            } else {
                remove(getString(R.string.prefs_autologin_username))
                remove(getString(R.string.prefs_autologin_password))
            }

            apply()
        }

        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}