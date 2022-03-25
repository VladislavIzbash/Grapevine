package ru.vizbash.grapevine

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import ru.vizbash.grapevine.ui.newprofile.NewProfileActivity

class StartupActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        startActivity(Intent(this, NewProfileActivity::class.java))
    }
}