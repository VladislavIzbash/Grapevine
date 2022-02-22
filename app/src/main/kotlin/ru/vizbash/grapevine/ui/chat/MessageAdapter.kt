package ru.vizbash.grapevine.ui.chat

import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import ru.vizbash.grapevine.storage.messages.MessageWithOrig

class MessageAdapter : PagingDataAdapter<MessageWithOrig, RecyclerView.ViewHolder>(MessageDiffCallback()) {

    override fun getItemViewType(position: Int): Int {
        return super.getItemViewType(position)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        TODO("Not yet implemented")
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        TODO("Not yet implemented")
    }

    private class MessageDiffCallback : DiffUtil.ItemCallback<MessageWithOrig>() {
        override fun areItemsTheSame(oldItem: MessageWithOrig, newItem: MessageWithOrig)
            = oldItem.msg.id == newItem.msg.id

        override fun areContentsTheSame(oldItem: MessageWithOrig, newItem: MessageWithOrig)
            = oldItem == newItem
    }
}