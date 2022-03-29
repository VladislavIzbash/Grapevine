package ru.vizbash.grapevine.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.databinding.FragmentChatBinding

@AndroidEntryPoint
class ChatFragment : Fragment() {
    companion object {
        const val ARG_CHAT_ID = "chat_id"
    }

    private var _ui: FragmentChatBinding? = null
    private val ui get() = _ui!!

    private val model: ChatViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _ui = FragmentChatBinding.inflate(inflater, container, false)

        val messageAdapter = MessageAdapter(
            lifecycleScope,
            model.myId,
            requireActivity().window.decorView.width,
            true,
            { null },
            {},
        )

        ui.messageList.layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = false
            reverseLayout = true
        }
        ui.messageList.adapter = messageAdapter

        messageAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                ui.messageList.scrollToPosition(0)
            }

            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                ui.messageList.scrollToPosition(0)
            }
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.pagedMessages.collectLatest {
                    messageAdapter.submitData(it)
                }
            }
        }

        return ui.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _ui = null
    }
}