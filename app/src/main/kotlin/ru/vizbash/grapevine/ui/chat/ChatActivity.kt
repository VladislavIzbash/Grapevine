package ru.vizbash.grapevine.ui.chat

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.fragment.app.add
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.R
import ru.vizbash.grapevine.databinding.ActivityChatBinding
import ru.vizbash.grapevine.service.ChatService
import ru.vizbash.grapevine.service.profile.ProfileProvider
import javax.inject.Inject

@AndroidEntryPoint
class ChatActivity : AppCompatActivity() {
    @Inject lateinit var chatService: ChatService
    @Inject lateinit var profileProvider: ProfileProvider

    companion object {
        const val EXTRA_CHAT_ID = "chat_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ui = ActivityChatBinding.inflate(layoutInflater)
        setContentView(ui.root)

        setSupportActionBar(ui.toolbar)
        supportActionBar!!.apply {
            setDisplayShowTitleEnabled(false)
            setDisplayHomeAsUpEnabled(true)
        }

        val chatId = intent.getLongExtra(EXTRA_CHAT_ID, -1)

        lifecycleScope.launch(Dispatchers.Main) {
            val chat = requireNotNull(chatService.getChat(chatId))

            val isAdmin = chat.ownerId == profileProvider.profile.nodeId

            ui.membersButton.setOnClickListener {
                val intent = Intent(this@ChatActivity, ChatMembersActivity::class.java).apply {
                    putExtra(ChatMembersActivity.EXTRA_CHAT_ID, chatId)
                    putExtra(ChatMembersActivity.EXTRA_IS_ADMIN, isAdmin)
                }
                startActivity(intent)
            }

            if (!chat.isGroup) {
                ui.membersButton.visibility = View.INVISIBLE
            }

            if (chat.photo != null) {
                ui.photoCard.visibility = View.VISIBLE
                ui.photo.setImageBitmap(chat.photo)
            } else {
                ui.photoCard.visibility = View.GONE
            }

            ui.chatName.text = chat.name

            val args = bundleOf(
                ChatFragment.ARG_CHAT_ID to chatId,
                ChatFragment.ARG_GROUP_MODE to chat.isGroup,
            )

            supportFragmentManager.commit {
                setReorderingAllowed(true)
                add<ChatFragment>(R.id.chat_fragment_container, null, args)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}