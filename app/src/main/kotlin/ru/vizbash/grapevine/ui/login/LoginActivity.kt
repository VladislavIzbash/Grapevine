package ru.vizbash.grapevine.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.R
import ru.vizbash.grapevine.databinding.ActivityLoginBinding
import ru.vizbash.grapevine.ui.main.MainActivity
import ru.vizbash.grapevine.ui.login.LoginViewModel.State.*

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {
    private lateinit var ui: ActivityLoginBinding
    private val model: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(ui.root)

        ui.welcomeText.text = getString(R.string.login_welcome, model.username)

        ui.loginButton.setOnClickListener {
            model.login(ui.passwordField.text.toString(), ui.autoLoginCheck.isChecked)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.state.collect(::applyState)
            }
        }
    }

    private fun applyState(state: LoginViewModel.State) = when (state) {
        is LoggingIn -> ui.loginProgress.visibility = View.VISIBLE
        is LoginError -> {
            ui.loginProgress.visibility = View.INVISIBLE
            Snackbar.make(ui.root, state.error, Snackbar.LENGTH_SHORT).show()
        }
        is LoggedIn -> {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
        else -> {}
    }
}