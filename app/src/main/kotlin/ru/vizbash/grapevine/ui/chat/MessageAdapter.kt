package ru.vizbash.grapevine.ui.chat

import android.annotation.SuppressLint
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.TextView
import androidx.annotation.CallSuper
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import ru.vizbash.grapevine.R
import ru.vizbash.grapevine.databinding.AttachmentFileBinding
import ru.vizbash.grapevine.databinding.AttachmentMessageBinding
import ru.vizbash.grapevine.databinding.ItemIngoingMessageBinding
import ru.vizbash.grapevine.databinding.ItemOutgoingMessageBinding
import ru.vizbash.grapevine.storage.message.Message
import ru.vizbash.grapevine.storage.message.MessageFile
import ru.vizbash.grapevine.storage.message.MessageWithOrig
import ru.vizbash.grapevine.storage.node.KnownNode
import ru.vizbash.grapevine.util.formatMessageTimestamp
import ru.vizbash.grapevine.util.toHumanSize

class MessageAdapter(
    private val myId: Long,
    private val maxWidth: Int,
    private val groupMode: Boolean,
    private val getMessageSender: suspend (Long) -> KnownNode?,
    private val getDownloadProgress: (Long) -> Flow<Float?>?,
    private val onMessagedRead: (Message) -> Unit,
    private val onFileActionClicked: (Message) -> Unit,
) : PagingDataAdapter<MessageWithOrig, MessageAdapter.ViewHolder>(MessageDiffCallback()) {

    class MessageDiffCallback : DiffUtil.ItemCallback<MessageWithOrig>() {
        override fun areItemsTheSame(
            oldItem: MessageWithOrig,
            newItem: MessageWithOrig,
        ) = oldItem.msg.id == newItem.msg.id

        override fun areContentsTheSame(
            oldItem: MessageWithOrig,
            newItem: MessageWithOrig
        ) = oldItem == newItem
    }

    companion object {
        private const val IS_INGOING = 1 shl 0
        private const val HAS_ORIG_MSG = 1 shl 1
        private const val HAS_FILE = 1 shl 2
    }

    private val paint = Paint()

    override fun getItemViewType(position: Int): Int {
        val item = requireNotNull(getItem(position))

        var type = 0
        if (item.msg.senderId != myId) {
            type = type or IS_INGOING
        }
        if (item.orig != null) {
            type = type or HAS_ORIG_MSG
        }
        if (item.msg.file != null) {
            type = type or HAS_FILE
        }

        return type
    }

    @SuppressLint("InflateParams")
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)

        val holder = if (viewType and IS_INGOING != 0) {
            val view = layoutInflater.inflate(R.layout.item_ingoing_message, parent, false)
            IngoingViewHolder(view)
        } else {
            val view = layoutInflater.inflate(R.layout.item_outgoing_message, parent, false)
            OutgoingViewHolder(view)
        }

        val bodyLayout = holder.itemView.findViewById<LinearLayout>(R.id.bodyLayout)

        val margin = (3 * parent.context.resources.displayMetrics.density).toInt()

        if (viewType and HAS_ORIG_MSG != 0) {
            val view = layoutInflater.inflate(R.layout.attachment_message, null).apply {
                id = R.id.attachment_message
                layoutParams = LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                    setMargins(0, margin, 0, margin)
                }
            }
            bodyLayout.addView(view)
        }
        if (viewType and HAS_FILE != 0) {
            val view = layoutInflater.inflate(R.layout.attachment_file, null).apply {
                id = R.id.attachment_file
                layoutParams = LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                    setMargins(0, margin, 0, margin)
                }
            }
            bodyLayout.addView(view)
        }

        return holder
    }

    private fun shouldExpand(textView: TextView, msg: Message): Boolean {
        val textWidth by lazy {
            paint.textSize = textView.textSize
            paint.typeface = textView.typeface
            paint.measureText(msg.text)
        }

        return msg.origMsgId != null || msg.file != null || textWidth > maxWidth
    }

    abstract class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var itemScope: CoroutineScope? = null
        var boundItem: Message? = null

        @CallSuper
        open fun bind(msg: Message) {
            itemScope = CoroutineScope(Dispatchers.Default)
            boundItem = msg
        }

        @CallSuper
        open fun unbind() {
            itemScope?.cancel()
            boundItem = null
        }
    }

    inner class OutgoingViewHolder(view: View) : ViewHolder(view) {
        private val ui = ItemOutgoingMessageBinding.bind(view)

        override fun bind(msg: Message) {
            super.bind(msg)

            ui.messageText.text = msg.text
            ui.timestamp.text = formatMessageTimestamp(msg.timestamp)

            val stateRes = when (msg.state) {
                Message.State.SENT -> R.drawable.ic_msg_time
                Message.State.DELIVERED -> R.drawable.ic_msg_check
                Message.State.READ -> R.drawable.ic_msg_double_check
                Message.State.DELIVERY_FAILED -> R.drawable.ic_msg_error
            }
            ui.stateImage.setImageResource(stateRes)

            ui.timestampLayout.orientation = if (shouldExpand(ui.messageText, msg)) {
                LinearLayout.VERTICAL
            } else {
                LinearLayout.HORIZONTAL
            }
        }
    }

    inner class IngoingViewHolder(view: View) : ViewHolder(view) {
        private val ui = ItemIngoingMessageBinding.bind(view)

        override fun bind(msg: Message) {
            super.bind(msg)

            ui.messageText.text = msg.text

            ui.username.visibility = if (groupMode) View.VISIBLE else View.GONE
            ui.photoCard.visibility = if (groupMode) View.VISIBLE else View.GONE

            if (groupMode) {
                ui.username.setText(R.string.unknown)
                itemScope!!.launch {
                    val sender = getMessageSender(msg.senderId)
                    ui.root.post {
                        ui.username.text = sender?.username
                            ?: itemView.context.getString(R.string.unknown)
                        val photo = sender?.photo
                        if (photo != null) {
                            ui.photo.setImageBitmap(photo)
                        } else {
                            ui.photo.setImageResource(R.drawable.avatar_placeholder)
                        }
                    }
                }
            }

            ui.timestamp.text = formatMessageTimestamp(msg.timestamp)

            ui.timestampLayout.orientation = if (shouldExpand(ui.messageText, msg)) {
                LinearLayout.VERTICAL
            } else {
                LinearLayout.HORIZONTAL
            }

            if (msg.state != Message.State.READ) {
                onMessagedRead(msg)
            }
        }
    }

    private fun bindOriginalMessage(view: View, msg: Message, coroutineScope: CoroutineScope) {
        val ui = AttachmentMessageBinding.bind(view)

        ui.text.text = msg.text
        ui.timestamp.text = formatMessageTimestamp(msg.timestamp)
        ui.username.setText(R.string.unknown)

        coroutineScope.launch {
            val sender = getMessageSender(msg.senderId)

            ui.username.post {
                ui.username.text = sender?.username ?: view.context.getString(R.string.unknown)
            }
        }
    }

    private fun bindFile(view: View, msg: Message, coroutineScope: CoroutineScope) {
        val ui = AttachmentFileBinding.bind(view)

        val file = msg.file!!

        ui.filename.text = file.name

        val sizeUnits = view.context.resources.getStringArray(R.array.size_units)
        ui.fileSize.text = file.size.toHumanSize(sizeUnits)

        ui.downloadLayout.setOnClickListener { onFileActionClicked(msg) }

        when (file.state) {
            MessageFile.State.NOT_DOWNLOADED, MessageFile.State.FAILED -> {
                ui.downloadStateImage.setImageResource(R.drawable.ic_download)
                ui.downloadProgress.progress = 1
                ui.downloadProgress.visibility = View.INVISIBLE
            }
            MessageFile.State.DOWNLOADING -> {
                ui.downloadStateImage.setImageResource(R.drawable.ic_close)
                ui.downloadProgress.progress = 1
                ui.downloadProgress.visibility = View.VISIBLE

                coroutineScope.launch {
                    getDownloadProgress(msg.id)?.collect { prog ->
                        when (prog) {
                            null -> return@collect
                            else -> ui.root.post {
                                ui.downloadProgress.progress = (prog * 100).toInt()
                            }
                        }
                    }
                }
            }
            MessageFile.State.DOWNLOADED -> {
                ui.downloadStateImage.setImageResource(R.drawable.ic_open_in_new)
                ui.downloadProgress.visibility = View.INVISIBLE
            }
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position) ?: return

        holder.unbind()
        holder.bind(item.msg)

        item.orig?.let {
            val view = holder.itemView.findViewById<View>(R.id.attachment_message)
            bindOriginalMessage(view, it, holder.itemScope!!)
        }
        item.msg.file?.let {
            val view = holder.itemView.findViewById<View>(R.id.attachment_file)
            bindFile(view, item.msg, holder.itemScope!!)
        }
    }

    override fun onViewDetachedFromWindow(holder: ViewHolder) {
        super.onViewDetachedFromWindow(holder)
        holder.unbind()
    }
}