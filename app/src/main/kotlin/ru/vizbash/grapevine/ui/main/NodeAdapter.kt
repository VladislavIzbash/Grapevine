package ru.vizbash.grapevine.ui.main

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import ru.vizbash.grapevine.R
import ru.vizbash.grapevine.databinding.ItemNodeBinding
import ru.vizbash.grapevine.network.Node
import ru.vizbash.grapevine.network.SourceType

class NodeAdapter(nodes: Set<Node>) : RecyclerView.Adapter<NodeAdapter.ViewHolder>() {
    private var entries = nodes.map(::NodeEntry)

    class NodeEntry(val node: Node) {
        var photo: Bitmap? = null
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val ui = ItemNodeBinding.bind(view)

        fun bind(entry: NodeEntry) {
            if (entry.photo != null) {
                ui.ivPhoto.setImageBitmap(entry.photo)
            } else {
                ui.ivPhoto.setImageResource(R.drawable.avatar_placeholder)
            }
            ui.tvUsername.text = entry.node.username
            ui.ivSource.setImageResource(when (entry.node.primarySource) {
                SourceType.BLUETOOTH -> R.drawable.ic_bluetooth
                SourceType.WIFI -> R.drawable.ic_wifi
                null -> R.drawable.avatar_placeholder
            })
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_node, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = entries[position]
        holder.bind(entry)
    }

    override fun getItemCount() = entries.size

    fun update(newEntries: Set<Node>) {
        val oldEntries = entries
        entries = newEntries.map(::NodeEntry)

        DiffUtil.calculateDiff(EntriesDiffCallback(oldEntries, entries), true)
            .dispatchUpdatesTo(this)
    }

    fun setPhoto(node: Node, photo: Bitmap?) {
        val pos = entries.indexOfFirst { it.node == node }
        if (pos == -1) {
            return
        }

        entries[pos].photo = photo
        notifyItemChanged(pos)
    }

    private class EntriesDiffCallback(
        private val oldList: List<NodeEntry>,
        private val newList: List<NodeEntry>,
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = oldList.size

        override fun getNewListSize() = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
            oldList[oldItemPosition].node == newList[newItemPosition].node

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldPhoto = oldList[oldItemPosition].photo ?: return false
            val newPhoto = newList[newItemPosition].photo ?: return false

            return oldPhoto.sameAs(newPhoto) && areItemsTheSame(oldItemPosition, newItemPosition)
        }
    }
}