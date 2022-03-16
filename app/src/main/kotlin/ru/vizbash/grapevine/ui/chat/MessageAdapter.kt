package ru.vizbash.grapevine.ui.chat

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.R
import ru.vizbash.grapevine.databinding.FileAttachmentBinding
import ru.vizbash.grapevine.databinding.ForwardedMessageBinding
import ru.vizbash.grapevine.databinding.ItemIngoingMessageBinding
import ru.vizbash.grapevine.databinding.ItemOutgoingMessageBinding
import ru.vizbash.grapevine.storage.messages.MessageEntity
import ru.vizbash.grapevine.storage.messages.MessageFile
import ru.vizbash.grapevine.storage.messages.MessageWithOrig
import ru.vizbash.grapevine.storage.messages.MessageWithUsername
import ru.vizbash.grapevine.util.toHumanSize
import java.text.SimpleDateFormat
import java.util.*

class MessageAdapter(
    private val myId: Long,
    private val showIngoingSender: Boolean,
    private val loadPhoto: suspend (Long) -> Bitmap?,
    private val coroutineScope: CoroutineScope,
    private val messageReadCb: (MessageEntity) -> Unit,
) : PagingDataAdapter<MessageWithOrig, MessageAdapter.MessageViewHolder>(MessageDiffCallback()) {
    companion object {
        val TIMESTAMP_FORMAT = SimpleDateFormat("k:mm", Locale.US)
        private const val TYPE_OUTGOING = 0
        private const val TYPE_INGOING = 1
    }

    private fun ForwardedMessageBinding.bindOriginalMessage(message: MessageWithUsername?) {
        if (message != null) {
            tvForwardedUsername.text = message.username
            tvForwardedText.text = message.msg.text
            tvForwardedTime.text = TIMESTAMP_FORMAT.format(message.msg.timestamp)

            root.visibility = View.VISIBLE
        } else {
            root.visibility = View.GONE
        }
    }

    private fun FileAttachmentBinding.bindAttachment(file: MessageFile?, outgoing: Boolean) {
        if (file != null) {
            tvFileName.text = file.name

            val units = root.context.resources.getStringArray(R.array.size_units)
            tvFileSize.text = file.size.toHumanSize(units)

            frameDownload.visibility = if (outgoing) View.GONE else View.VISIBLE

            root.visibility = View.VISIBLE
        } else {
            root.visibility = View.GONE
        }
    }

    abstract class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        abstract var boundItem: MessageWithOrig?
            protected set

        abstract fun bind(item: MessageWithOrig)

        open fun unbind() {
            boundItem = null
        }
    }

    inner class OutgoingViewHolder(view: View) : MessageViewHolder(view) {
        private val ui = ItemOutgoingMessageBinding.bind(view)

        override var boundItem: MessageWithOrig? = null

        override fun bind(item: MessageWithOrig) {
            boundItem = item

            ui.layoutForwardedMessage.bindOriginalMessage(item.origWithUsername)
            ui.layoutFileAttachment.bindAttachment(item.withUsername.msg.file, true)

            ui.tvMessageText.text = item.withUsername.msg.text
            ui.layoutMessageBody.post {
                val expanded = ui.tvMessageText.lineCount > 1
                        || item.origWithUsername != null
                        || item.withUsername.msg.file != null
                ui.layoutMessageBody.orientation = if (expanded) {
                    LinearLayout.VERTICAL
                } else {
                    LinearLayout.HORIZONTAL
                }
            }

            ui.tvMessageTime.text = TIMESTAMP_FORMAT.format(item.withUsername.msg.timestamp)

            val res = when (item.withUsername.msg.state) {
                MessageEntity.State.SENT -> R.drawable.ic_msg_time
                MessageEntity.State.DELIVERED -> R.drawable.ic_msg_check
                MessageEntity.State.READ -> R.drawable.ic_msg_double_check
                MessageEntity.State.DELIVERY_FAILED -> R.drawable.ic_msg_error
            }
            ui.ivMessageStatus.setImageResource(res)
        }
    }

    inner class IngoingViewHolder(view: View) : MessageViewHolder(view) {
        private val ui = ItemIngoingMessageBinding.bind(view)

        private lateinit var loadJob: Job

        override var boundItem: MessageWithOrig? = null

        override fun bind(item: MessageWithOrig) {
            boundItem = item

            ui.layoutForwardedMessage.bindOriginalMessage(item.origWithUsername)
            ui.layoutFileAttachment.bindAttachment(item.withUsername.msg.file, false)

            if (showIngoingSender) {
                ui.tvUsername.text = item.withUsername.username
                loadJob = coroutineScope.launch {
                    val photo = loadPhoto(item.withUsername.msg.senderId)
                    ui.ivPhoto.post {
                        if (photo != null) {
                            ui.ivPhoto.setImageBitmap(photo)
                        } else {
                            ui.ivPhoto.setImageResource(R.drawable.avatar_placeholder)
                        }
                    }
                }
                ui.cardPhoto.visibility = View.VISIBLE
            } else {
                ui.cardPhoto.visibility = View.GONE
            }

            ui.tvMessageText.text = item.withUsername.msg.text
            ui.layoutMessageBody.post {
                val expanded = ui.tvMessageText.lineCount > 1
                        || item.origWithUsername != null
                        || item.withUsername.msg.file != null
                ui.layoutMessageBody.orientation = if (expanded) {
                    LinearLayout.VERTICAL
                } else {
                    LinearLayout.HORIZONTAL
                }
            }

            ui.tvMessageTime.text = TIMESTAMP_FORMAT.format(item.withUsername.msg.timestamp)

            messageReadCb(item.withUsername.msg)
        }

        override fun unbind() {
            super.unbind()
            loadJob.cancel()
        }
    }

    override fun getItemViewType(position: Int): Int {
        val msg = requireNotNull(getItem(position))

        return if (msg.withUsername.msg.senderId == myId) {
            TYPE_OUTGOING
        } else {
            TYPE_INGOING
        }
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val msg = getItem(position) ?: return
        holder.bind(msg)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        return when (viewType) {
            TYPE_INGOING -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_ingoing_message, parent, false)
                IngoingViewHolder(view)
            }
            TYPE_OUTGOING -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_outgoing_message, parent, false)
                OutgoingViewHolder(view)
            }
            else -> throw IllegalArgumentException()
        }
    }

    override fun onViewDetachedFromWindow(holder: MessageViewHolder) {
        super.onViewDetachedFromWindow(holder)
        holder.unbind()
    }

    private class MessageDiffCallback : DiffUtil.ItemCallback<MessageWithOrig>() {
        override fun areItemsTheSame(oldItem: MessageWithOrig, newItem: MessageWithOrig): Boolean
            = oldItem.withUsername.msg.id == newItem.withUsername.msg.id

        override fun areContentsTheSame(oldItem: MessageWithOrig, newItem: MessageWithOrig): Boolean
            = oldItem == newItem
    }
}