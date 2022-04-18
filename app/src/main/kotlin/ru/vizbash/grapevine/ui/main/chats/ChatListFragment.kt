package ru.vizbash.grapevine.ui.main.chats

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.R
import ru.vizbash.grapevine.databinding.FragmentChatListBinding
import ru.vizbash.grapevine.storage.chat.Chat
import ru.vizbash.grapevine.ui.chat.ChatActivity
import ru.vizbash.grapevine.ui.main.MainViewModel

class ChatListFragment : Fragment() {
    private var _ui: FragmentChatListBinding? = null
    private val ui get() = _ui!!

    private val activityModel: MainViewModel by activityViewModels()

    private lateinit var chatAdapter: ChatAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _ui = FragmentChatListBinding.inflate(inflater, container, false)

        chatAdapter = ChatAdapter(
            viewLifecycleOwner.lifecycleScope,
            activityModel.profile.value!!.nodeId,
        ) {
            val intent = Intent(requireContext(), ChatActivity::class.java).apply {
                putExtra(ChatActivity.EXTRA_CHAT_ID, it.id)
            }
            startActivity(intent)
        }

        val decoration = DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)

        ui.chatList.layoutManager = LinearLayoutManager(requireContext())
        ui.chatList.addItemDecoration(decoration)
        ui.chatList.adapter = chatAdapter

        registerForContextMenu(ui.chatList)

        ui.addChatButton.setOnClickListener {
            findNavController().navigate(R.id.action_fragment_chat_list_to_dialog_new_chat)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                activityModel.chatList.collect(::updateChatList)
            }
        }

        return ui.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _ui = null
    }

    private fun updateChatList(chats: List<Chat>) {
        val showNoChats = chats.isEmpty() && activityModel.searchQuery.value.isEmpty()
        ui.noChatsLayout.visibility = (if (showNoChats) View.VISIBLE else View.INVISIBLE)
        ui.chatList.visibility = if (chats.isEmpty()) View.INVISIBLE else View.VISIBLE

        val items = chats.map {
            ChatAdapter.ChatItem(
                it,
                activityModel.getLastMessage(it.id),
                if (!it.isGroup) activityModel.getOnlineFlow(it.id) else null,
            )
        }
        chatAdapter.submitList(items)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == R.id.item_delete) {
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.delete_chat))
                .setMessage(getString(R.string.delete_chat_alert))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete) { _, _ ->
                    activityModel.deleteChat(chatAdapter.currentMenuItem!!.chat)
                }
                .show()
            true
        } else {
            super.onContextItemSelected(item)
        }
    }
}