package ru.vizbash.grapevine.ui.chat

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.core.widget.addTextChangedListener
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.vizbash.grapevine.R
import ru.vizbash.grapevine.databinding.FragmentChatBinding
import ru.vizbash.grapevine.service.foreground.ForegroundService
import ru.vizbash.grapevine.storage.message.Message
import ru.vizbash.grapevine.storage.message.MessageFile
import ru.vizbash.grapevine.util.formatMessageTimestamp
import ru.vizbash.grapevine.util.toHumanSize

@AndroidEntryPoint
class ChatFragment : Fragment() {
    companion object {
        const val ARG_CHAT_ID = "chat_id"
        const val ARG_GROUP_MODE = "group_mode"
    }

    private var _ui: FragmentChatBinding? = null
    private val ui get() = _ui!!

    private val model: ChatViewModel by viewModels()

    private val pickFile = registerForActivityResult(OpenDocument()) { uri ->
        if (uri == null) {
            return@registerForActivityResult
        }

        val doc = DocumentFile.fromSingleUri(requireContext(), uri)!!
        model.attachedFile.value = MessageFile(
            uri,
            doc.name!!,
            doc.length().toInt(),
            false,
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _ui = FragmentChatBinding.inflate(inflater, container, false)

        setupMessageList()

        setupAttachments()

        ui.attachFileButton.setOnClickListener {
            pickFile.launch(arrayOf("*/*"))
        }

        ui.sendButton.setOnClickListener {
            model.sendMessage(ui.messageTextField.text.toString())
            ui.messageTextField.text.clear()

            model.attachedFile.value?.let {
                requireContext().contentResolver.takePersistableUriPermission(
                    it.uri!!,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }

        ui.sendButton.isEnabled = false

        return ui.root
    }

    override fun onStart() {
        super.onStart()

        val intent = Intent(requireContext(), ForegroundService::class.java).apply {
            action = ForegroundService.ACTION_MUTE_CHAT
            putExtra(ForegroundService.EXTRA_CHAT_ID, model.chatId)
        }
        requireContext().startService(intent)

        lifecycleScope.launch(Dispatchers.Main) {
            if (!model.isMember()) {
                ui.messageTextField.isEnabled = false
                ui.messageTextField.setText(R.string.not_in_chat)
                ui.sendButton.isEnabled = false
            } else {
                ui.messageTextField.addTextChangedListener {
                    ui.sendButton.isEnabled = it?.isNotBlank() ?: false
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()

        val intent = Intent(requireContext(), ForegroundService::class.java).apply {
            action = ForegroundService.ACTION_UNMUTE_CHAT
            putExtra(ForegroundService.EXTRA_CHAT_ID, model.chatId)
        }
        requireContext().startService(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _ui = null
    }

    private fun setupMessageList() {
        val endMargin = resources.getDimensionPixelSize(R.dimen.message_end_margin)

        val messageAdapter = MessageAdapter(
            model.profile.nodeId,
            requireActivity().window.decorView.width - endMargin,
            requireArguments().getBoolean(ARG_GROUP_MODE),
            model::getMessageSender,
            model::getDownloadProgress,
            model::markAsRead,
            model::startFileDownload,
        )

        ui.messageList.layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = false
            reverseLayout = true
        }
        ui.messageList.adapter = messageAdapter

        val swipeCallback = MessageSwipeCallback { model.attachedMessage.value = it }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(ui.messageList)

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
    }

    private fun setupAttachments() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { model.attachedMessage.collect(::updateAttachedMessage) }
                launch { model.attachedFile.collect(::updateAttachedFile) }
            }
        }

        ui.removeMessageButton.setOnClickListener {
            model.attachedMessage.value = null
        }
        ui.removeFileButton.setOnClickListener {
            model.attachedFile.value = null
        }
    }

    private fun updateAttachedFile(file: MessageFile?) {
        ui.attachedFileLayout.visibility = if (file != null) {
            View.VISIBLE
        } else {
            View.GONE
        }

        file?.let {
            ui.attachedFile.filename.text = it.name
            val sizeUnits = resources.getStringArray(R.array.size_units)
            ui.attachedFile.fileSize.text = it.size.toHumanSize(sizeUnits)
            ui.attachedFile.downloadLayout.visibility = View.INVISIBLE
        }
    }

    private suspend fun updateAttachedMessage(message: Message?) {
        ui.attachedMessageLayout.visibility = if (message != null) {
            View.VISIBLE
        } else {
            View.GONE
        }

        message?.let {
            ui.attachedMessage.text.text = it.text
            ui.attachedMessage.timestamp.text = formatMessageTimestamp(it.timestamp)

            val sender = model.getMessageSender(it.senderId)

            withContext(Dispatchers.Main) {
                ui.attachedMessage.username.text = sender?.username
                    ?: getString(R.string.unknown)
            }
        }
    }
}