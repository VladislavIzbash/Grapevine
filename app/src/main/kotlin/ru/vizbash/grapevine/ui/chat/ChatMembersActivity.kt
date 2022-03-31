package ru.vizbash.grapevine.ui.chat

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.databinding.ActivityChatMembersBinding

@AndroidEntryPoint
class ChatMembersActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_CHAT_ID = "chat_id"
    }

    private val model: ChatMembersViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ui = ActivityChatMembersBinding.inflate(layoutInflater)
        setContentView(ui.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val memberAdapter = ChatMemberAdapter()

        ui.memberList.layoutManager = LinearLayoutManager(this)
        ui.memberList.adapter = memberAdapter

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.chatMembers.collect { nodes ->
                    val items = nodes.map {
                        ChatMemberAdapter.ChatMemberItem(
                            it,
                            model.isNodeOnline(it.id),
                        )
                    }
                    memberAdapter.submitList(items)
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}