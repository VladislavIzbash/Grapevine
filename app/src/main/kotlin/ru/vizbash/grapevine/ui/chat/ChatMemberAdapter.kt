package ru.vizbash.grapevine.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ru.vizbash.grapevine.R
import ru.vizbash.grapevine.databinding.ItemChatBinding
import ru.vizbash.grapevine.storage.node.KnownNode

class ChatMemberAdapter : ListAdapter<ChatMemberAdapter.ChatMemberItem, ChatMemberAdapter.ViewHolder>(ChatMemberCallback()) {

    data class ChatMemberItem(val node: KnownNode, val isOnline: Boolean)

    class ChatMemberCallback : DiffUtil.ItemCallback<ChatMemberItem>() {
        override fun areItemsTheSame(oldItem: ChatMemberItem, newItem: ChatMemberItem)
            = oldItem.node.id == newItem.node.id

        override fun areContentsTheSame(oldItem: ChatMemberItem, newItem: ChatMemberItem)
            = oldItem == newItem
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val ui = ItemChatBinding.bind(view)

        fun bind(item: ChatMemberItem) {
            ui.lastMessage.visibility = View.GONE
            ui.onlineIndicator.isEnabled = item.isOnline
            ui.chatName.text = item.node.username

            if (item.node.photo != null) {
                ui.photo.setImageBitmap(item.node.photo)
            } else {
                ui.photo.setImageResource(R.drawable.avatar_placeholder)
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
}