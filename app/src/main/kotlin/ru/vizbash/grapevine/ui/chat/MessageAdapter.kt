package ru.vizbash.grapevine.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import ru.vizbash.grapevine.R
import ru.vizbash.grapevine.databinding.ItemIngoingMessageBinding
import ru.vizbash.grapevine.databinding.ItemOutgoingMessageBinding
import ru.vizbash.grapevine.storage.messages.MessageEntity
import java.text.SimpleDateFormat
import java.util.*

class MessageAdapter : PagingDataAdapter<MessageEntity, RecyclerView.ViewHolder>(MessageDiffCallback()) {
    companion object {
        private val DATE_FORMAT = SimpleDateFormat("k:mm", Locale.US)
        private const val TYPE_OUTGOING = 0
        private const val TYPE_INGOING = 2
    }

    class OutgoingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val ui = ItemOutgoingMessageBinding.bind(view)

        fun bind(message: MessageEntity) {
            ui.tvMessageText.text = message.text
            ui.layoutMessageBody.post {
                ui.layoutMessageBody.orientation = if (ui.tvMessageText.lineCount > 1) {
                    LinearLayout.VERTICAL
                } else {
                    LinearLayout.HORIZONTAL
                }
            }

            ui.tvMessageTime.text = DATE_FORMAT.format(message.timestamp)

            val res = when (message.state) {
                MessageEntity.State.SENT -> R.drawable.ic_msg_time
                MessageEntity.State.DELIVERED -> R.drawable.ic_msg_check
                MessageEntity.State.READ -> R.drawable.ic_msg_double_check
                MessageEntity.State.DELIVERY_FAILED -> R.drawable.ic_msg_error
            }
            ui.ivMessageStatus.setImageResource(res)
        }
    }

    class IngoingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val ui = ItemIngoingMessageBinding.bind(view)

        fun bind(message: MessageEntity) {
            ui.cardPhoto.visibility = View.GONE

            ui.tvMessageText.text = message.text
            ui.layoutMessageBody.post {
                ui.layoutMessageBody.orientation = if (ui.tvMessageText.lineCount > 1) {
                    LinearLayout.VERTICAL
                } else {
                    LinearLayout.HORIZONTAL
                }
            }

            ui.tvMessageTime.text = DATE_FORMAT.format(message.timestamp)
        }
    }

    override fun getItemViewType(position: Int): Int {
        val msg = getItem(position) ?: throw IllegalArgumentException()

        return if (msg.isIngoing) { TYPE_INGOING } else { TYPE_OUTGOING }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = getItem(position) ?: return

        if (msg.isIngoing) {
            (holder as IngoingViewHolder).bind(msg)
        } else {
            (holder as OutgoingViewHolder).bind(msg)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
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

    private class MessageDiffCallback : DiffUtil.ItemCallback<MessageEntity>() {
        override fun areItemsTheSame(oldItem: MessageEntity, newItem: MessageEntity)
            = oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: MessageEntity, newItem: MessageEntity)
            = oldItem == newItem
    }
}