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
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.databinding.ActivityChatBinding
import ru.vizbash.grapevine.storage.messages.MessageWithOrig

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

        setupMessageList()

        ui.editMessage.addTextChangedListener(messageTextWatcher)

        ui.buttonSend.isEnabled = false
        ui.buttonSend.setOnClickListener {
            model.sendMessage(ui.editMessage.text.toString().trim())
            ui.editMessage.text.clear()
            model.forwardedMessage.value = null
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                collectForwardedMessage()
            }
        }

        ui.buttonForwardedRemove.setOnClickListener {
            model.forwardedMessage.value = null
        }
    }

    private suspend fun collectForwardedMessage() {
        model.forwardedMessage.collect { msg ->
            ui.layoutForwardedMessage.apply {
                if (msg != null) {
                    tvForwardedText.text = msg.text
                    tvForwardedTime.text = MessageAdapter.TIMESTAMP_FORMAT.format(msg.timestamp)

                    tvForwardedUsername.text = if (msg.senderId == model.currentProfile.nodeId) {
                        model.currentProfile.username
                    } else {
                        model.contact.username
                    }

                    ui.layoutForwardedFrame.visibility = View.VISIBLE
                } else {
                    ui.layoutForwardedFrame.visibility = View.GONE
                }
            }
        }
    }

    private fun setupMessageList() {
        val messageAdapter = MessageAdapter(model.currentProfile, model.contact)

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
