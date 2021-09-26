package ru.vizbash.grapevine.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import ru.vizbash.grapevine.R
import ru.vizbash.grapevine.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {
    private lateinit var ui: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = ActivityLoginBinding.inflate(layoutInflater)

        ui.loginButton.setOnClickListener(this::tryLogin)

        setContentView(ui.root)
    }

    private fun tryLogin(v: View) {
        if (validateFields()) {
            startActivity(Intent(this, MainActivity::class.java))
        }
    }

    private fun validateFields(): Boolean {
        var validated = true

        if (ui.loginUsername.text.isBlank()) {
            ui.loginUsername.error = getString(R.string.error_empty_field)
            validated = false
        }
        if (ui.loginPassword.text.isBlank()) {
            ui.loginPassword.error = getString(R.string.error_empty_field)
            validated = false
        }

        return validated
    }
}