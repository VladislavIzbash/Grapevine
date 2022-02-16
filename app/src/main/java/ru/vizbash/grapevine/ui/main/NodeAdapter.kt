package ru.vizbash.grapevine.ui.main

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import ru.vizbash.grapevine.R
import ru.vizbash.grapevine.databinding.ItemNodeBinding
import ru.vizbash.grapevine.network.NetworkController
import ru.vizbash.grapevine.network.SourceType

class NodeAdapter(
    private val entries: StateFlow<List<NodeEntry>>,
    private val coroutineScope: CoroutineScope,
) : RecyclerView.Adapter<NodeAdapter.ViewHolder>() {

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

    @SuppressLint("NotifyDataSetChanged")
    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)

        coroutineScope.launch {
            entries.collect { notifyDataSetChanged() }
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)

        coroutineScope.cancel()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_node, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = entries.value[position]
        holder.bind(entry)

        coroutineScope.launch {
            try {
                entry.photo = entry.photoFetcher()
                holder.itemView.post { notifyDataSetChanged() }
            } catch (e: NetworkController.GvException) {
                e.printStackTrace()
            }
        }
    }

    override fun getItemCount() = entries.value.size
}