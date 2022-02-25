package ru.vizbash.grapevine.ui.chat

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.databinding.ActivityChatBinding

@AndroidEntryPoint
class ChatActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_CONTACT_ID = "contact_id"
    }

    private lateinit var ui: ActivityChatBinding
    private val model: ChatViewModel by viewModels()

    private val messageTextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

        override fun afterTextChanged(s: Editable?) {
            ui.buttonSend.isEnabled = !s.isNullOrBlank()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = ActivityChatBinding.inflate(layoutInflater)
        setContentView(ui.root)

        setSupportActionBar(ui.toolbar)

        supportActionBar?.apply {
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

        val messageAdapter = MessageAdapter()

        ui.rvMessages.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = false
            reverseLayout = true
        }
        ui.rvMessages.adapter = messageAdapter

        messageAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                ui.rvMessages.scrollToPosition(0)
            }

            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                ui.rvMessages.scrollToPosition(0)
            }
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.pagedMessages.collectLatest {
                    messageAdapter.submitData(it)
                }
            }
        }

        ui.editMessage.addTextChangedListener(messageTextWatcher)

        ui.buttonSend.isEnabled = false
        ui.buttonSend.setOnClickListener {
            model.sendMessage(ui.editMessage.text.toString().trim())
            ui.editMessage.text.clear()
        }
    }
}
