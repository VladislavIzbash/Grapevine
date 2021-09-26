package ru.vizbash.grapevine.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class StartupActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

//        startActivity(Intent(this, MainActivity::class.java))
        startActivity(Intent(this, LoginActivity::class.java))
        // TODO: decide based on login state

        finish()
    }
}