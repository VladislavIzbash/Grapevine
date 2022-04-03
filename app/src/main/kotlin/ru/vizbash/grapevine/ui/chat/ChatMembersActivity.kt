package ru.vizbash.grapevine.ui.chat

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.databinding.ActivityChatMembersBinding

@AndroidEntryPoint
class ChatMembersActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_CHAT_ID = "chat_id"
        const val EXTRA_IS_ADMIN = "is_admin"
    }

    private val model: ChatMembersViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ui = ActivityChatMembersBinding.inflate(layoutInflater)
        setContentView(ui.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        supportFragmentManager.setFragmentResultListener(
            InviteNodeDialog.KEY_NODE_ID,
            this,
        ) { _, res ->
            model.invite(res.getLong(InviteNodeDialog.KEY_NODE_ID))
        }

        ui.addMemberButton.visibility = if (model.isAdmin) View.VISIBLE else View.GONE
        ui.addMemberButton.setOnClickListener {
            InviteNodeDialog().show(supportFragmentManager, "InviteDialog")
        }

        ui.leaveButton.setOnClickListener {
            model.leaveChat {
                finish()
            }
        }

        ui.leaveButton.visibility = if (model.isAdmin) View.GONE else View.VISIBLE

        val memberAdapter = ChatMemberAdapter(model.myId, model.isAdmin) {
            model.kick(it.id)
        }

        ui.memberList.apply {
            layoutManager = LinearLayoutManager(this@ChatMembersActivity)
            adapter = memberAdapter
            addItemDecoration(DividerItemDecoration(
                this@ChatMembersActivity,
                DividerItemDecoration.VERTICAL),
            )
        }

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