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
import androidx.appcompat.content.res.AppCompatResources
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
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
    private val coroutineScope: CoroutineScope,
    private val myId: Long,
    private val maxWidth: Int,
    private val groupMode: Boolean,
    private val getMessageSender: suspend (Message) -> KnownNode?,
    private val onMessagedRead: (Message) -> Unit,
) : PagingDataAdapter<MessageWithOrig, RecyclerView.ViewHolder>(MessageDiffCallback()) {

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
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
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
        val margin = textView.context.resources.getDimensionPixelSize(R.dimen.message_end_margin)

        paint.textSize = textView.textSize
        paint.typeface = textView.typeface

        return msg.origMsgId != null
                || msg.file != null
                || paint.measureText(msg.text) > maxWidth - margin
    }

    inner class OutgoingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val ui = ItemOutgoingMessageBinding.bind(view)

        fun bind(msg: Message) {
            ui.messageText.text = msg.text
            ui.timestamp.text = formatMessageTimestamp(msg.timestamp)

            val stateRes = when (msg.state) {
                Message.State.SENT -> R.drawable.ic_msg_time
                Message.State.DELIVERED -> R.drawable.ic_msg_check
                Message.State.READ -> R.drawable.ic_msg_double_check
                Message.State.DELIVERY_FAILED -> R.drawable.ic_msg_error
            }
            val drawable = AppCompatResources.getDrawable(itemView.context, stateRes)
            ui.timestamp.setCompoundDrawables(null, null, drawable, null)

            ui.timestampLayout.orientation = if (shouldExpand(ui.messageText, msg)) {
                LinearLayout.VERTICAL
            } else {
                LinearLayout.HORIZONTAL
            }
        }
    }

    inner class IngoingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val ui = ItemIngoingMessageBinding.bind(view)

        fun bind(msg: Message) {
            ui.messageText.text = msg.text

            ui.username.visibility = if (groupMode) View.VISIBLE else View.GONE
            ui.photoCard.visibility = if (groupMode) View.VISIBLE else View.GONE

            ui.timestamp.text = formatMessageTimestamp(msg.timestamp)

            ui.timestampLayout.orientation = if (shouldExpand(ui.messageText, msg)) {
                LinearLayout.VERTICAL
            } else {
                LinearLayout.HORIZONTAL
            }
        }
    }

    private fun bindOriginalMessage(view: View, msg: Message) {
        val ui = AttachmentMessageBinding.bind(view)

        ui.text.text = msg.text
        ui.timestamp.text = formatMessageTimestamp(msg.timestamp)
    }

    private fun bindFile(view: View, file: MessageFile) {
        val ui = AttachmentFileBinding.bind(view)

        ui.filename.text = file.name

        val sizeUnits = view.context.resources.getStringArray(R.array.size_units)
        ui.fileSize.text = file.size.toHumanSize(sizeUnits)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position) ?: return

        when (holder) {
            is IngoingViewHolder -> holder.bind(item.msg)
            is OutgoingViewHolder -> holder.bind(item.msg)
        }

        item.orig?.let {
            bindOriginalMessage(holder.itemView.findViewById(R.id.attachment_message), it)
        }
        item.msg.file?.let {
            bindFile(holder.itemView.findViewById(R.id.attachment_file), it)
        }
    }
}