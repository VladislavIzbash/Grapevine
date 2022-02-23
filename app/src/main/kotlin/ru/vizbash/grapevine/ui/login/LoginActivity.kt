package ru.vizbash.grapevine.ui.login

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.R
import ru.vizbash.grapevine.databinding.ActivityLoginBinding
import ru.vizbash.grapevine.storage.profile.ProfileEntity
import ru.vizbash.grapevine.ui.main.MainActivity
import ru.vizbash.grapevine.ui.newprofile.NewProfileActivity

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {
    private lateinit var ui: ActivityLoginBinding
    private val model: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = ActivityLoginBinding.inflate(layoutInflater)
        setSupportActionBar(ui.toolbar)
        setContentView(ui.root)

        ui.checkAutoLogin.isChecked = model.loginPrefs.autoLoginUsername != null

        ui.buttonNewIdentity.setOnClickListener {
            startActivity(Intent(this, NewProfileActivity::class.java))
        }
        ui.buttonLogin.setOnClickListener {
            val profile = model.profiles.value[ui.spinnerUsername.selectedItemPosition]
            model.login(profile, ui.editPassword.text.toString(), ui.checkAutoLogin.isChecked)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                lifecycleScope.launch { collectLoginState() }
                lifecycleScope.launch { collectProfileList() }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                model.profiles.collect { profiles ->
                    model.loginPrefs.lastUsername?.let { username ->
                        val index = profiles.indexOfFirst { it.username == username }
                        if (index != -1) {
                            ui.spinnerUsername.setSelection(index)
                        }
                    }
                }
            }
        }
    }

    private suspend fun collectLoginState() {
        model.loginState.collect { state ->
            when (state) {
                LoginViewModel.LoginState.LOGGED_IN -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
                LoginViewModel.LoginState.FAILED -> {
                    Snackbar.make(ui.root, R.string.error_invalid_password, Snackbar.LENGTH_LONG).apply {
                        setTextColor(getColor(R.color.error))
                        show()
                    }
                }
                else -> {}
            }
        }
    }

    private suspend fun collectProfileList() {
        model.profiles.collect { profiles ->
            val empty = profiles.isEmpty()

            ui.spinnerUsername.isEnabled = !empty
            ui.buttonLogin.isEnabled = !empty

            val options = if (!empty) {
                profiles.map(ProfileEntity::username)
            } else {
                listOf(getString(R.string.no_profiles))
            }

            ui.spinnerUsername.adapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                options,
            )
        }
    }
}