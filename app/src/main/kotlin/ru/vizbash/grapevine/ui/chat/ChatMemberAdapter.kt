package ru.vizbash.grapevine.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ru.vizbash.grapevine.R
import ru.vizbash.grapevine.databinding.ItemChatMemberBinding
import ru.vizbash.grapevine.storage.node.KnownNode

class ChatMemberAdapter(
    private val myId: Long,
    private val showKick: Boolean,
    private val onKickClicked: (KnownNode) -> Unit,
) : ListAdapter<ChatMemberAdapter.ChatMemberItem, ChatMemberAdapter.ViewHolder>(ChatMemberCallback()) {

    data class ChatMemberItem(val node: KnownNode, val isOnline: Boolean)

    class ChatMemberCallback : DiffUtil.ItemCallback<ChatMemberItem>() {
        override fun areItemsTheSame(oldItem: ChatMemberItem, newItem: ChatMemberItem)
            = oldItem.node.id == newItem.node.id

        override fun areContentsTheSame(oldItem: ChatMemberItem, newItem: ChatMemberItem)
            = oldItem == newItem
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val ui = ItemChatMemberBinding.bind(view)

        fun bind(item: ChatMemberItem) {
            ui.onlineIndicator.visibility = if (item.node.id == myId) {
                View.INVISIBLE
            } else {
                View.VISIBLE
            }
            ui.onlineIndicator.isEnabled = item.isOnline
            ui.username.text = item.node.username

            if (item.node.photo != null) {
                ui.photo.setImageBitmap(item.node.photo)
            } else {
                ui.photo.setImageResource(R.drawable.avatar_placeholder)
            }

            ui.kickButton.visibility = if (showKick && item.node.id != myId) {
                View.VISIBLE
            } else {
                View.GONE
            }
            ui.kickButton.setOnClickListener { onKickClicked(item.node) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_member, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}