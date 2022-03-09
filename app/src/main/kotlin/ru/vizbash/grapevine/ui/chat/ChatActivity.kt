package ru.vizbash.grapevine.ui.chat

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.R
import ru.vizbash.grapevine.databinding.ActivityChatBinding
import ru.vizbash.grapevine.service.ForegroundService
import ru.vizbash.grapevine.storage.messages.MessageFile
import ru.vizbash.grapevine.storage.messages.MessageWithOrig
import ru.vizbash.grapevine.util.toHumanSize

@AndroidEntryPoint
class ChatActivity : AppCompatActivity(), ServiceConnection {
    companion object {
        const val EXTRA_CHAT_ID = "contact_id"
    }

    private lateinit var ui: ActivityChatBinding
    private val model: ChatViewModel by viewModels()

    private var foregroundService: ForegroundService? = null

    private val messageTextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

        override fun afterTextChanged(s: Editable?) {
            ui.buttonSend.isEnabled = !s.isNullOrBlank()
        }
    }

    private val openFile = registerForActivityResult(GetContent()) { uri ->
        if (uri == null) {
            return@registerForActivityResult
        }

        val doc = DocumentFile.fromSingleUri(this, uri)!!
        model.attachedFile.value = MessageFile(
            uri,
            doc.name!!,
            doc.length().toInt(),
            false,
        )
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

        bindService(
            Intent(this, ForegroundService::class.java),
            this,
            BIND_AUTO_CREATE,
        )
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        val binder = (service as ForegroundService.ServiceBinder)
        model.service = binder.grapevineService
        foregroundService = binder.foregroundService

        ui.tvContactUsername.text = model.contact.username
        val photo = model.contact.photo
        if (photo != null) {
            ui.cardContactPhoto.visibility = View.VISIBLE
            ui.ivContactPhoto.setImageBitmap(photo)
        } else {
            ui.cardContactPhoto.visibility = View.GONE
        }
        setupMessageList()
        ui.editMessage.addTextChangedListener(messageTextWatcher)
        ui.buttonSend.isEnabled = false
        ui.buttonSend.setOnClickListener {
            model.sendMessage(ui.editMessage.text.toString().trim())
            ui.editMessage.text.clear()

            model.forwardedMessage.value = null
            model.attachedFile.value = null
        }
        ui.buttonAttachFile.setOnClickListener {
            openFile.launch("*/*")
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { collectForwardedMessage() }
                launch { collectAttachment() }
            }
        }
        ui.buttonForwardedRemove.setOnClickListener {
            model.forwardedMessage.value = null
        }
        ui.buttonAttachmentRemove.setOnClickListener {
            model.attachedFile.value = null
        }
        foregroundService?.suppressChatNotifications(model.contact.nodeId)
    }

    override fun onServiceDisconnected(name: ComponentName?) {}

    override fun onStart() {
        super.onStart()
        foregroundService?.suppressChatNotifications(model.contact.nodeId)
    }

    override fun onStop() {
        super.onStop()
        foregroundService?.enableChatNotifications(model.contact.nodeId)
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(this)
    }

    private suspend fun collectForwardedMessage() {
        model.forwardedMessage.collect { msg ->
            ui.layoutForwardedMessage.apply {
                if (msg != null) {
                    tvForwardedText.text = msg.text
                    tvForwardedTime.text = MessageAdapter.TIMESTAMP_FORMAT.format(msg.timestamp)

                    tvForwardedUsername.text =
                        if (msg.senderId == model.service.currentProfile.nodeId) {
                            model.service.currentProfile.username
                        } else {
                            model.contact.username
                        }

                    ui.cardForwardedMessage.visibility = View.VISIBLE
                } else {
                    ui.cardForwardedMessage.visibility = View.GONE
                }
            }
        }
    }

    private suspend fun collectAttachment() {
        model.attachedFile.collect { file ->
            ui.layoutFileAttachment.apply {
                if (file != null) {
                    tvFileName.text = file.name
                    tvFileSize.text =
                        file.size.toHumanSize(resources.getStringArray(R.array.size_units))

                    frameDownload.visibility = View.GONE
                    ui.cardFileAttachment.visibility = View.VISIBLE
                } else {
                    ui.cardFileAttachment.visibility = View.GONE
                }
            }
        }
    }

    private fun setupMessageList() {
        val messageAdapter =
            MessageAdapter(model.service.currentProfile, model.contact, model::markAsRead)

        ui.rvMessages.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = false
            reverseLayout = true
        }
        ui.rvMessages.adapter = messageAdapter

        val itemTouchCallback = MessageSwipeCallback { item ->
            model.forwardedMessage.value = item.msg
        }

        ItemTouchHelper(itemTouchCallback).attachToRecyclerView(ui.rvMessages)

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
    }

    private class MessageSwipeCallback(
        private val listener: (MessageWithOrig) -> Unit
    ) : ItemTouchHelper.Callback() {
        override fun getMovementFlags(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
        ) = makeMovementFlags(0, ItemTouchHelper.END)

        override fun isLongPressDragEnabled() = false

        override fun isItemViewSwipeEnabled() = true

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder,
        ): Boolean {
            return false
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            val position = viewHolder.bindingAdapterPosition
            viewHolder.bindingAdapter?.notifyItemChanged(position)

            val item = (viewHolder as MessageAdapter.MessageViewHolder).boundItem ?: return
            listener(item)
        }

        override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder) = 0.2F

        override fun getSwipeEscapeVelocity(defaultValue: Float) = defaultValue / 2

        override fun getAnimationDuration(
            recyclerView: RecyclerView,
            animationType: Int,
            animateDx: Float,
            animateDy: Float,
        ) = 50L
    }
}
