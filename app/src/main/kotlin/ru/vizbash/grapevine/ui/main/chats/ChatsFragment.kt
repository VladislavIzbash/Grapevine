package ru.vizbash.grapevine.ui.main.chats

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.R
import ru.vizbash.grapevine.databinding.FragmentChatsBinding
import ru.vizbash.grapevine.ui.chat.ChatActivity
import ru.vizbash.grapevine.ui.main.MainViewModel

class ChatsFragment : Fragment() {
    private lateinit var ui: FragmentChatsBinding
    private val model: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        ui = FragmentChatsBinding.inflate(inflater, container, false)

        val adapter = ChatAdapter {
            val intent = Intent(requireActivity(), ChatActivity::class.java).apply {
                putExtra(ChatActivity.EXTRA_CHAT_ID, it.id)
                putExtra(ChatActivity.EXTRA_CHAT_MODE, true)
            }
            startActivity(intent)
        }
        ui.rvChats.layoutManager = LinearLayoutManager(requireActivity())
        ui.rvChats.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                collectChats(adapter)
            }
        }

        ui.buttonAdd.setOnClickListener { onAddClicked() }

        return ui.root
    }

    private suspend fun collectChats(adapter: ChatAdapter) {
        model.service.chatList.collect {
            adapter.submitList(it)
        }
    }

    private fun onAddClicked() {
        val input = EditText(requireActivity()).apply {
            setHint(R.string.chat_name_hint)
        }

        AlertDialog.Builder(requireActivity())
            .setTitle(R.string.chat_name)
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.create) { _, _ ->
                if (input.text.isBlank()) {
                    Snackbar.make(ui.root, R.string.error_empty_chat_name, Snackbar.LENGTH_SHORT)
                        .show()
                    return@setPositiveButton
                } else {
                    model.createChat(input.text.toString())
                }
            }
            .show()
    }
}