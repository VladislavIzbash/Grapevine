package ru.vizbash.grapevine.ui.main.contacts

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.R
import ru.vizbash.grapevine.databinding.ItemContactBinding
import ru.vizbash.grapevine.databinding.ItemPendingContactBinding
import ru.vizbash.grapevine.storage.contacts.ContactEntity
import ru.vizbash.grapevine.storage.messages.MessageEntity

class ContactAdapter(
    private val coroutineScope: CoroutineScope,
    private val myId: Long,
    private val listener: ContactListener,
) : ListAdapter<ContactAdapter.ContactItem, RecyclerView.ViewHolder>(ContactDiffCallback()) {
    companion object {
        private const val TYPE_PENDING = 0
        private const val TYPE_ACCEPTED = 1
    }

    interface ContactListener {
        fun onSelected(contact: ContactEntity)
        fun onAccepted(contact: ContactEntity)
        fun onRejected(contact: ContactEntity)
        fun onCanceled(contact: ContactEntity)
    }

    data class ContactItem(
        val contact: ContactEntity,
        val isOnline: Flow<Boolean>,
        val lastMessage: Flow<MessageEntity?>,
        var headerRes: Int? = null,
    )

    inner class PendingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val ui = ItemPendingContactBinding.bind(view)

        fun bind(item: ContactItem) {
            if (item.headerRes != null) {
                ui.itemHeader.setText(item.headerRes!!)
                ui.itemHeader.visibility = View.VISIBLE
            } else {
                ui.itemHeader.visibility = View.GONE
            }

            if (item.contact.photo != null) {
                ui.ivPhoto.setImageBitmap(item.contact.photo)
            } else {
                ui.ivPhoto.setImageResource(R.drawable.avatar_placeholder)
            }

            ui.tvUsername.text = item.contact.username
            if (item.contact.state == ContactEntity.State.INGOING) {
                ui.buttonAcceptContact.visibility = View.VISIBLE
                ui.buttonAcceptContact.setOnClickListener { listener.onAccepted(item.contact) }

                ui.buttonRejectContact.setOnClickListener { listener.onRejected(item.contact) }
            } else {
                ui.buttonAcceptContact.visibility = View.INVISIBLE
                ui.buttonAcceptContact.setOnClickListener(null)

                ui.buttonRejectContact.setOnClickListener { listener.onCanceled(item.contact) }
            }
        }
    }

    inner class AcceptedViewHolder(view: View): RecyclerView.ViewHolder(view) {
        private val ui = ItemContactBinding.bind(view)

        lateinit var updateJob: Job

        fun bind(item: ContactItem) {
            if (item.headerRes != null) {
                ui.itemHeader.setText(item.headerRes!!)
                ui.itemHeader.visibility = View.VISIBLE
            } else {
                ui.itemHeader.visibility = View.GONE
            }

            if (item.contact.photo != null) {
                ui.ivPhoto.setImageBitmap(item.contact.photo)
            } else {
                ui.ivPhoto.setImageResource(R.drawable.avatar_placeholder)
            }

            ui.tvUsername.text = item.contact.username

            updateJob = coroutineScope.launch {
                launch {
                    item.lastMessage.collect { msg ->
                        if (msg == null) {
                            ui.tvLastMessage.text = ""
                        } else {
                            ui.tvLastMessage.text = if (msg.senderId == myId) {
                                "> ${msg.text}"
                            } else {
                                msg.text
                            }
                        }
                    }
                }
                launch {
                    item.isOnline.collect { online ->
                        if (online) {
                            ui.ivOnlineIndicator.clearColorFilter()
                        } else {
                            val bg = ui.root.context.getColor(R.color.darkerBackground)
                            ui.ivOnlineIndicator.setColorFilter(bg)
                        }
                    }
                }
            }

            ui.root.setOnClickListener { listener.onSelected(item.contact) }
        }
    }

    override fun submitList(list: MutableList<ContactItem>?) {
        requireNotNull(list)

        val sorted = list.sortedBy { getStateOrder(it.contact.state) }

        val firstIngoing = sorted.find { it.contact.state == ContactEntity.State.INGOING }
        firstIngoing?.headerRes = R.string.ingoing_contacts

        val firstOutgoing = sorted.find { it.contact.state == ContactEntity.State.OUTGOING }
        firstOutgoing?.headerRes = R.string.outgoing_contacts

        if (firstIngoing != null || firstOutgoing != null) {
            val firstAccepted = sorted.find { it.contact.state == ContactEntity.State.ACCEPTED }
            firstAccepted?.headerRes = R.string.accepted_contacts
        }

        super.submitList(list)
    }

//    var items = emptyList<ContactItem>()
//        set(value) {
//            val sorted = value.sortedBy { getStateOrder(it.contact.state) }
//
//            val firstIngoing = sorted.find { it.contact.state == ContactEntity.State.INGOING }
//            firstIngoing?.headerRes = R.string.ingoing_contacts
//
//            val firstOutgoing = sorted.find { it.contact.state == ContactEntity.State.OUTGOING }
//            firstOutgoing?.headerRes = R.string.outgoing_contacts
//
//            if (firstIngoing != null || firstOutgoing != null) {
//                val firstAccepted = sorted.find { it.contact.state == ContactEntity.State.ACCEPTED }
//                firstAccepted?.headerRes = R.string.accepted_contacts
//            }
//
//            val callback = ContactDiffCallback(items, sorted)
//            DiffUtil.calculateDiff(callback, true).dispatchUpdatesTo(this)
//
//            field = sorted
//        }

    private fun getStateOrder(state: ContactEntity.State) = when (state) {
        ContactEntity.State.ACCEPTED -> 2
        ContactEntity.State.OUTGOING -> 1
        ContactEntity.State.INGOING -> 0
    }

    override fun getItemViewType(position: Int) = when (getItem(position).contact.state) {
        ContactEntity.State.OUTGOING, ContactEntity.State.INGOING -> TYPE_PENDING
        ContactEntity.State.ACCEPTED -> TYPE_ACCEPTED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = when (viewType) {
        TYPE_PENDING -> {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_pending_contact, parent, false)
            PendingViewHolder(view)
        }
        TYPE_ACCEPTED -> {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_contact, parent, false)
            AcceptedViewHolder(view)
        }
        else -> throw IllegalArgumentException()
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)

        when (item.contact.state) {
            ContactEntity.State.OUTGOING, ContactEntity.State.INGOING -> {
                (holder as PendingViewHolder).bind(item)
            }
            ContactEntity.State.ACCEPTED -> {
                (holder as AcceptedViewHolder).bind(item)
            }
        }
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        super.onViewDetachedFromWindow(holder)

        if (holder is AcceptedViewHolder) {
            holder.updateJob.cancel()
        }
    }

    private class ContactDiffCallback : DiffUtil.ItemCallback<ContactItem>() {
        override fun areItemsTheSame(oldItem: ContactItem, newItem: ContactItem): Boolean =
            oldItem.contact.nodeId == newItem.contact.nodeId

        override fun areContentsTheSame(oldItem: ContactItem, newItem: ContactItem): Boolean =
            oldItem == newItem
    }
}