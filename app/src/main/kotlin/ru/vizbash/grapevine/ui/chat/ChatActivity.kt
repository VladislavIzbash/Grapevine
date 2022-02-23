package ru.vizbash.grapevine.ui.chat

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import ru.vizbash.grapevine.R
import ru.vizbash.grapevine.databinding.ActivityChatBinding

@AndroidEntryPoint
class ChatActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_CONTACT_ID = "contact_id"
    }

    private lateinit var ui: ActivityChatBinding
    private val model: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = ActivityChatBinding.inflate(layoutInflater)
        setContentView(ui.root)

        setSupportActionBar(ui.toolbar)

        supportActionBar!!.run {
            setDisplayShowTitleEnabled(false)
            setDisplayHomeAsUpEnabled(true)
        }

        ui.tvContactUsername.text = model.contact.username

        val photo = model.contact.photo
        if (photo != null) {
            ui.cardContactPhoto.visibility = View.VISIBLE
            ui.ivContactPhoto.setImageBitmap(photo)
        } else {
            ui.cardContactPhoto.visibility = View.GONE
        }
    }
}
