package ru.vizbash.grapevine.ui.main.chats

import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.*
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.R
import ru.vizbash.grapevine.databinding.ItemChatBinding
import ru.vizbash.grapevine.storage.chat.Chat
import ru.vizbash.grapevine.storage.message.MessageWithSender

class ChatAdapter(
    private val coroutineScope: CoroutineScope,
    private val myId: Long,
    private val onItemClicked: (Chat) -> Unit,
) : ListAdapter<ChatAdapter.ChatItem, ChatAdapter.ViewHolder>(ChatDiffCallback()) {
    var currentMenuItem: ChatItem? = null
        private set

    data class ChatItem(val chat: Chat, val lastMessage: Flow<MessageWithSender?>)

    class ChatDiffCallback : DiffUtil.ItemCallback<ChatItem>() {
        override fun areItemsTheSame(oldItem: ChatItem, newItem: ChatItem): Boolean
            = oldItem.chat.id == newItem.chat.id

        override fun areContentsTheSame(oldItem: ChatItem, newItem: ChatItem): Boolean
            = oldItem.chat == newItem.chat
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val ui = ItemChatBinding.bind(view)

        private lateinit var lastMessageJob: Job

        fun bind(item: ChatItem) {
            ui.root.setOnClickListener { onItemClicked(item.chat) }
            ui.root.setOnCreateContextMenuListener { menu, _, _ ->
                menu.add(Menu.NONE, R.id.item_delete, Menu.NONE, R.string.delete)
            }
            ui.root.setOnLongClickListener {
                currentMenuItem = item
                false
            }

            ui.chatName.text = item.chat.name

            if (item.chat.photo != null) {
                ui.photo.setImageBitmap(item.chat.photo)
            } else {
                ui.photo.setImageResource(R.drawable.chat_placeholder)
            }

            lastMessageJob = coroutineScope.launch {
                item.lastMessage.collect { updateLastMessage(it, item) }
            }
        }

        fun unbind() {
            lastMessageJob.cancel()
        }

        private fun updateLastMessage(lastMessage: MessageWithSender?, item: ChatItem) {
            if (lastMessage == null) {
                ui.lastMessage.visibility = View.INVISIBLE
            } else {
                ui.lastMessage.visibility = View.VISIBLE

                val senderId = lastMessage.sender?.id ?: myId
                val username = if (senderId != myId) {
                    lastMessage.sender!!.username
                } else {
                    itemView.context.getString(R.string.you)
                }

                ui.lastMessage.text = if (item.chat.isGroup || senderId == myId) {
                    SpannableString("$username: ${lastMessage.msg.text}").apply {
                        setSpan(
                            StyleSpan(Typeface.BOLD),
                            0,
                            username.length + 1,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                        )
                    }
                } else {
                    lastMessage.msg.text
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewDetachedFromWindow(holder: ViewHolder) {
        super.onViewDetachedFromWindow(holder)
        holder.unbind()
    }
}