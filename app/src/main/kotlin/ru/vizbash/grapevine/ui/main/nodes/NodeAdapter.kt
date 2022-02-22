package ru.vizbash.grapevine.ui.main.nodes

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.recyclerview.widget.DiffUtil
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
) : RecyclerView.Adapter<NodeAdapter.ViewHolder>() {
    class NodeItem(
        val node: Node,
        val photo: Deferred<Bitmap?>,
        val isContact: Boolean,
    )

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val ui = ItemNodeBinding.bind(view)

        lateinit var fetchJob: Job
            private set

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
    }

    var items = emptyList<NodeItem>()
        set(value) {
            val callback = NodeDiffCallback(items, value)
            DiffUtil.calculateDiff(callback).dispatchUpdatesTo(this)
            field = value
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_node, parent, false)
        return ViewHolder(view)
    }

    override fun onViewDetachedFromWindow(holder: ViewHolder) {
        super.onViewDetachedFromWindow(holder)

        holder.fetchJob.cancel()
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    class NodeDiffCallback(
        private val oldItems: List<NodeItem>,
        private val newItems: List<NodeItem>,
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = oldItems.size

        override fun getNewListSize() = newItems.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
            oldItems[oldItemPosition].node.id == newItems[newItemPosition].node.id

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val old = oldItems[oldItemPosition]
            val new = newItems[newItemPosition]
            return old.node == new.node && old.isContact == new.isContact
        }
    }
}