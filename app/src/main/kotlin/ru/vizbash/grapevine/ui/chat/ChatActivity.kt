package ru.vizbash.grapevine.ui.chat

import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ChatActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_CONTACT_ID = "contact_id"
    }

    private val model: ChatViewModel by viewModels()
}