package ru.vizbash.grapevine.ui.main.contacts

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import ru.vizbash.grapevine.R
import ru.vizbash.grapevine.databinding.ItemContactBinding
import ru.vizbash.grapevine.databinding.ItemPendingContactBinding
import ru.vizbash.grapevine.storage.contacts.ContactEntity

class ContactAdapter(
    private val listener: ContactListener,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    interface ContactListener {
        fun onSelected(contact: ContactEntity)
        fun onAccepted(contact: ContactEntity)
        fun onRejected(contact: ContactEntity)
        fun onCanceled(contact: ContactEntity)
    }

    data class ContactItem(
        val contact: ContactEntity,
        val isOnline: Boolean,
        var headerRes: Int? = null
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

            if (!item.isOnline) {
                val bg = ui.root.context.getColor(R.color.darkerBackground)
                ui.ivOnlineIndicator.setColorFilter(bg)
            }

            ui.root.setOnClickListener { listener.onSelected(item.contact) }
        }
    }

    companion object {
        private const val TYPE_PENDING = 0
        private const val TYPE_ACCEPTED = 1
    }

    var items = emptyList<ContactItem>()
        set(value) {
            val sorted = value.sortedBy { getStateOrder(it.contact.state) }

            val firstIngoing = sorted.find { it.contact.state == ContactEntity.State.INGOING }
            firstIngoing?.headerRes = R.string.ingoing_contacts

            val firstOutgoing = sorted.find { it.contact.state == ContactEntity.State.OUTGOING }
            firstOutgoing?.headerRes = R.string.outgoing_contacts

            if (firstIngoing != null || firstOutgoing != null) {
                val firstAccepted = sorted.find { it.contact.state == ContactEntity.State.ACCEPTED }
                firstAccepted?.headerRes = R.string.accepted_contacts
            }

            val callback = ContactDiffCallback(items, sorted)
            DiffUtil.calculateDiff(callback, true).dispatchUpdatesTo(this)

//            notifyDataSetChanged()

            field = sorted
        }

    private fun getStateOrder(state: ContactEntity.State) = when (state) {
        ContactEntity.State.ACCEPTED -> 2
        ContactEntity.State.OUTGOING -> 1
        ContactEntity.State.INGOING -> 0
    }

    override fun getItemViewType(position: Int): Int {
        val item = items[position]

        return when (item.contact.state) {
            ContactEntity.State.OUTGOING, ContactEntity.State.INGOING -> TYPE_PENDING
            ContactEntity.State.ACCEPTED -> TYPE_ACCEPTED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
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
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]

        when (item.contact.state) {
            ContactEntity.State.OUTGOING, ContactEntity.State.INGOING -> {
                (holder as PendingViewHolder).bind(item)
            }
            ContactEntity.State.ACCEPTED -> {
                (holder as AcceptedViewHolder).bind(item)
            }
        }
    }

    override fun getItemCount() = items.size

    private class ContactDiffCallback(
        private val oldItems: List<ContactItem>,
        private val newItems: List<ContactItem>,
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = oldItems.size

        override fun getNewListSize() = newItems.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
            oldItems[oldItemPosition].contact.nodeId == newItems[newItemPosition].contact.nodeId

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
            oldItems[oldItemPosition] == newItems[newItemPosition]
    }
}