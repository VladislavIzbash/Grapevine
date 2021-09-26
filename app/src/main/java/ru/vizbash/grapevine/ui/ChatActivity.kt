package ru.vizbash.grapevine.ui

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NavUtils
import androidx.recyclerview.widget.LinearLayoutManager
import ru.vizbash.grapevine.databinding.ActivityChatBinding
import java.util.*
import kotlin.random.Random

class ChatActivity : AppCompatActivity() {
    private lateinit var ui: ActivityChatBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = ActivityChatBinding.inflate(layoutInflater)

        ui.messageList.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }

        val date = Date()
        val messages = mutableListOf<MessageItem>()
        for (i in 0..20) {
            messages += MessageItem("Сообщение $i", date, Random.nextBoolean())
        }

        ui.messageList.adapter = MessageAdapter(messages)

        supportActionBar?.title = "Михаил Тимофеев"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setContentView(ui.root)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}