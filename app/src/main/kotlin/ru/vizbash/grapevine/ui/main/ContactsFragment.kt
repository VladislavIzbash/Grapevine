package ru.vizbash.grapevine.ui.main

import android.graphics.PorterDuff
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.GenericFastAdapter
import com.mikepenz.fastadapter.GenericItem
import com.mikepenz.fastadapter.adapters.ItemAdapter
import com.mikepenz.fastadapter.binding.AbstractBindingItem
import com.mikepenz.fastadapter.binding.BindingViewHolder
import com.mikepenz.fastadapter.diff.FastAdapterDiffUtil
import com.mikepenz.fastadapter.items.AbstractItem
import com.mikepenz.fastadapter.listeners.ClickEventHook
import jp.wasabeef.recyclerview.animators.FadeInAnimator
import jp.wasabeef.recyclerview.animators.FlipInTopXAnimator
import jp.wasabeef.recyclerview.animators.SlideInRightAnimator
import jp.wasabeef.recyclerview.animators.SlideInUpAnimator
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.R
import ru.vizbash.grapevine.databinding.FragmentContactsBinding
import ru.vizbash.grapevine.databinding.ItemContactBinding
import ru.vizbash.grapevine.databinding.ItemPendingContactBinding
import ru.vizbash.grapevine.storage.contacts.ContactEntity

class ContactsFragment : Fragment() {
    private lateinit var ui: FragmentContactsBinding
    private val model: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        ui = FragmentContactsBinding.inflate(inflater, container, false)

        ui.rvContacts.layoutManager = LinearLayoutManager(activity)

        val ingoingHeader = ItemAdapter<HeaderItem>().apply {
            add(HeaderItem("Входящие запросы"))
        }
        val ingoingAdapter = ItemAdapter<PendingContactItem>()
        val outgoingHeader = ItemAdapter<HeaderItem>().apply {
            add(HeaderItem("Исходящие запросы"))
        }
        val outgoingAdapter = ItemAdapter<PendingContactItem>()
        val acceptedHeader = ItemAdapter<HeaderItem>().apply {
            add(HeaderItem("Список контактов"))
        }
        val acceptedAdapter = ItemAdapter<ContactItem>()

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                var prevVisibility = listOf(false, false, false)

                model.contacts.collect { contacts ->
                    ui.tvNoContacts.visibility = if (contacts.isEmpty()) View.VISIBLE else View.INVISIBLE
                    ui.rvContacts.visibility = if (contacts.isEmpty()) View.INVISIBLE else View.VISIBLE

                    val nodes = model.availableNodes.value

                    val ingoing = contacts
                        .filter { it.state == ContactEntity.State.INGOING }
                        .map(::PendingContactItem)
                    val outgoing = contacts
                        .filter { it.state == ContactEntity.State.OUTGOING }
                        .map(::PendingContactItem)
                    val accepted = contacts
                        .filter { it.state == ContactEntity.State.ACCEPTED }
                        .map { contact ->
                            ContactItem(
                                contact,
                                nodes.any { it.id == contact.nodeId },
                            )
                        }

                    val visibility = listOf(ingoing, outgoing, accepted).map(List<*>::isNotEmpty)
                    if (prevVisibility != visibility) {
                        val adapters = mutableListOf<ItemAdapter<*>>()

                        if (visibility[0]) {
                            adapters.add(ingoingHeader)
                            adapters.add(ingoingAdapter)
                        }
                        if (visibility[1]) {
                            adapters.add(outgoingHeader)
                            adapters.add(outgoingAdapter)
                        }
                        if (visibility[2]) {
                            adapters.add(acceptedHeader)
                            adapters.add(acceptedAdapter)
                        }

                        ui.rvContacts.adapter = FastAdapter.with(adapters).apply {
                            addEventHook(ContactAcceptClickHook())
                            addEventHook(ContactRejectClickHook())
                            setHasStableIds(true)
                        }
                    }
                    prevVisibility = visibility

                    FastAdapterDiffUtil.set(ingoingAdapter, ingoing)
                    FastAdapterDiffUtil.set(outgoingAdapter, outgoing)
                    FastAdapterDiffUtil.set(acceptedAdapter, accepted)
                }
            }
        }

        return ui.root
    }

    private inner class ContactAcceptClickHook : ClickEventHook<PendingContactItem>() {
        override fun onBind(viewHolder: RecyclerView.ViewHolder): View? {
            return if (viewHolder is BindingViewHolder<*>) {
                viewHolder.itemView.findViewById(R.id.buttonAcceptContact)
            } else {
                null
            }
        }

        override fun onClick(
            v: View,
            position: Int,
            fastAdapter: FastAdapter<PendingContactItem>,
            item: PendingContactItem,
        ) {
            model.answerContactInvitation(item.contact, true)
        }
    }

    private inner class ContactRejectClickHook : ClickEventHook<PendingContactItem>() {
        override fun onBind(viewHolder: RecyclerView.ViewHolder): View? {
            return if (viewHolder is BindingViewHolder<*>) {
                viewHolder.itemView.findViewById(R.id.buttonRejectContact)
            } else {
                null
            }
        }

        override fun onClick(
            v: View,
            position: Int,
            fastAdapter: FastAdapter<PendingContactItem>,
            item: PendingContactItem,
        ) {
            if (item.contact.state == ContactEntity.State.INGOING) {
                model.answerContactInvitation(item.contact, false)
            } else {
                model.cancelContactInvitation(item.contact)
            }
        }
    }

    private data class HeaderItem(val header: String) : AbstractItem<HeaderItem.ViewHolder>() {
        override var identifier: Long
            get() = hashCode().toLong()
            set(_) {}

        class ViewHolder(private val view: View) : FastAdapter.ViewHolder<HeaderItem>(view) {
            override fun bindView(item: HeaderItem, payloads: List<Any>) {
                (view as TextView).text = item.header
            }

            override fun unbindView(item: HeaderItem) {
                (view as TextView).text = null
            }
        }

        override val type = R.id.header_item
        override val layoutRes = R.layout.item_header

        override fun getViewHolder(v: View) = ViewHolder(v)
    }

    private data class ContactItem(
        val contact: ContactEntity,
        val isOnline: Boolean,
    ) : AbstractBindingItem<ItemContactBinding>() {

        override var identifier: Long
            get() = contact.nodeId
            set(_) {}

        override val type = R.id.contact_item

        override fun createBinding(inflater: LayoutInflater, parent: ViewGroup?): ItemContactBinding {
            return ItemContactBinding.inflate(inflater, parent, false)
        }

        override fun bindView(binding: ItemContactBinding, payloads: List<Any>) {
            if (contact.photo != null) {
                binding.ivPhoto.setImageBitmap(contact.photo)
            } else {
                binding.ivPhoto.setImageResource(R.drawable.avatar_placeholder)
            }

            binding.tvUsername.text = contact.username

            if (!isOnline) {
                val bg = binding.root.context.getColor(R.color.darkerBackground)
                binding.ivOnlineIndicator.setColorFilter(bg)
            }
        }
    }

    private data class PendingContactItem(
        val contact: ContactEntity,
    ) : AbstractBindingItem<ItemPendingContactBinding>() {
        override var identifier: Long
            get() = contact.nodeId
            set(_) {}

        override val type = R.id.pending_contact_item

        override fun createBinding(inflater: LayoutInflater, parent: ViewGroup?): ItemPendingContactBinding {
            return ItemPendingContactBinding.inflate(inflater, parent, false)
        }

        override fun bindView(binding: ItemPendingContactBinding, payloads: List<Any>) {
            if (contact.photo != null) {
                binding.ivPhoto.setImageBitmap(contact.photo)
            } else {
                binding.ivPhoto.setImageResource(R.drawable.avatar_placeholder)
            }

            binding.tvUsername.text = contact.username
            binding.buttonAcceptContact.visibility = if (contact.state == ContactEntity.State.INGOING) {
                View.VISIBLE
            } else {
                View.INVISIBLE
            }
        }
    }
}