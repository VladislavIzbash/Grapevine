package ru.vizbash.grapevine.ui.main.nodes

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
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
import ru.vizbash.grapevine.network.SourceType

class NodeAdapter(
    private val coroutineScope: CoroutineScope,
    private val addClickedCb: (Node) -> Unit,
) : ListAdapter<NodeAdapter.NodeItem, NodeAdapter.ViewHolder>(NodeDiffCallback()) {
    class NodeItem(
        val node: Node,
        val photo: Deferred<Bitmap?>,
        val isContact: Boolean,
    )

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val ui = ItemNodeBinding.bind(view)

        private lateinit var fetchJob: Job

        fun bind(item: NodeItem) {
            ui.tvUsername.text = item.node.username
            ui.ivSource.setImageResource(when (item.node.primarySource!!) {
                SourceType.BLUETOOTH -> R.drawable.ic_bluetooth
                SourceType.WIFI -> R.drawable.ic_wifi
            })

            if (item.isContact) {
                ui.buttonAddContact.setOnClickListener(null)
                ui.buttonAddContact.visibility = View.INVISIBLE
            } else {
                ui.buttonAddContact.setOnClickListener { addClickedCb(item.node) }
                ui.buttonAddContact.visibility = View.VISIBLE
            }

            fetchJob = coroutineScope.launch {
                val photo = item.photo.await()

                ui.ivPhoto.post {
                    if (photo != null) {
                        ui.ivPhoto.setImageBitmap(photo)
                        val fadeIn = AnimationUtils.loadAnimation(ui.ivPhoto.context, android.R.anim.fade_in)
                        ui.ivPhoto.startAnimation(fadeIn)
                    } else {
                        ui.ivPhoto.setImageResource(R.drawable.avatar_placeholder)
                    }
                }
            }
        }

        fun unbind() {
            fetchJob.cancel()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_node, parent, false)
        return ViewHolder(view)
    }

    override fun onViewDetachedFromWindow(holder: ViewHolder) {
        super.onViewDetachedFromWindow(holder)
        holder.unbind()
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class NodeDiffCallback : DiffUtil.ItemCallback<NodeItem>() {
        override fun areItemsTheSame(oldItem: NodeItem, newItem: NodeItem): Boolean =
            oldItem.node.id == newItem.node.id

        override fun areContentsTheSame(oldItem: NodeItem, newItem: NodeItem): Boolean {
            return oldItem.node == newItem.node && oldItem.isContact == newItem.isContact
        }
    }
}