package ru.vizbash.grapevine.ui.main.nodes

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.R
import ru.vizbash.grapevine.databinding.ItemNodeBinding
import ru.vizbash.grapevine.network.Node

class NodeAdapter(
    private val coroutineScope: CoroutineScope,
    private val onItemClicked: (Node) -> Unit,
    private val onWriteClicked: (Node) -> Unit,
) : ListAdapter<NodeAdapter.NodeItem, NodeAdapter.ViewHolder>(NodeDiffCallback()) {

    class NodeItem(val node: Node, val photo: Deferred<Bitmap?>)

    class NodeDiffCallback : DiffUtil.ItemCallback<NodeItem>() {
        override fun areItemsTheSame(oldItem: NodeItem, newItem: NodeItem): Boolean
                = oldItem.node.id == newItem.node.id

        override fun areContentsTheSame(oldItem: NodeItem, newItem: NodeItem): Boolean
                = oldItem.node == newItem.node
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val ui = ItemNodeBinding.bind(view)

        private lateinit var photoJob: Job

        fun bind(item: NodeItem) {
            ui.root.setOnClickListener { onItemClicked(item.node) }
            ui.writeButton.setOnClickListener { onWriteClicked(item.node) }

            ui.username.text = item.node.username

            photoJob = coroutineScope.launch {
                val photo = item.photo.await()
                ui.photo.post {
                    if (photo != null) {
                        ui.photo.setImageBitmap(photo)
                    } else {
                        ui.photo.setImageResource(R.drawable.avatar_placeholder)
                    }
                }
            }
        }

        fun unbind() {
            photoJob.cancel()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_node, parent, false)
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